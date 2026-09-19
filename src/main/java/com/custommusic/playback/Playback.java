package com.custommusic.playback;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.hud.NowPlaying;
import com.custommusic.music.MusicLibrary;
import com.custommusic.music.PlaylistEngine;
import com.custommusic.pack.PackGenerator;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;

/**
 * 播放总调度：决定「现在这首放什么」，两种模式都从这里出去。
 *
 * <ul>
 *   <li><b>播放器模式</b>：忽略游戏情境，按歌单队列一直放（{@link PlaylistEngine}）。</li>
 *   <li><b>情境模式</b>：游戏想放哪首情境音乐，就按你的规则换成对应的曲子，
 *       没配规则的情境保持原版（{@link SituationalEngine}）。</li>
 * </ul>
 *
 * <p>两种模式共用「当前实例 / 失败重试 / 曲间间隔」这些状态 —— 同一时刻只会有
 * 一首我们在放的歌，所以放在这里是安全的。
 */
public final class Playback {

    /** 一次选曲的结果。 */
    public record Pick(String file, SoundEvent event) {
    }

    private static SoundInstance currentInstance;
    private static boolean ourTrackPending;
    private static String lastFailed;
    private static int failCount;

    private Playback() {
    }

    public static boolean isSituational() {
        CustomMusicConfig config = CustomMusicClient.config();
        return config != null && config.isSituational();
    }

    /** MusicManager 每次想开一首新歌时来问这里；返回 null 表示交回原版。 */
    public static Pick next(Music music) {
        CustomMusicConfig config = CustomMusicClient.config();
        if (config == null) {
            return null;
        }
        if (config.isSituational()) {
            Identifier context = music == null ? null : music.sound().value().location();
            return SituationalEngine.next(context);
        }
        // 播放器模式：排除列表里的情境不接管（默认是末影龙和终末之诗），
        // 否则「资源包不覆盖它、运行时却照样替换」就自相矛盾了
        if (music != null && isExcluded(music.sound().value().location())) {
            return null;
        }
        return PlaylistEngine.nextPick();
    }

    /** 这个情境是不是在 overrideExclude 里。写 "music.dragon" 或 "minecraft:music.dragon" 都认。 */
    private static boolean isExcluded(Identifier context) {
        CustomMusicConfig config = CustomMusicClient.config();
        if (config.overrideExclude == null || config.overrideExclude.isEmpty()) {
            return false;
        }
        String full = context.toString();
        String path = context.getPath();
        for (String raw : config.overrideExclude) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String value = raw.trim();
            if (value.equals(full) || value.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** 有没有在接管（每 tick 都会被调用，别做重活）。 */
    public static boolean active() {
        CustomMusicConfig config = CustomMusicClient.config();
        if (config == null) {
            return false;
        }
        return config.isSituational() ? SituationalEngine.active() : !config.queue.isEmpty();
    }

    public static Pick pickOf(String file) {
        Identifier id = Identifier.fromNamespaceAndPath(
                PackGenerator.PREVIEW_NAMESPACE,
                PackGenerator.TRACK_PREFIX + MusicLibrary.trackId(file));
        return new Pick(file, SoundEvent.createVariableRangeEvent(id));
    }

    public static void markStarted(SoundInstance instance) {
        currentInstance = instance;
        ourTrackPending = true;
    }

    /**
     * 「我们放的那首刚刚结束了」——消费式，问一次就清掉。
     * 只有我们自己放的歌才该压缩曲间间隔；没配规则的情境放的是原版音乐，
     * 必须保持原版间隔，否则原版音乐会被我们压成两秒一首。
     */
    public static boolean consumeOurTrackEnded() {
        boolean pending = ourTrackPending;
        ourTrackPending = false;
        return pending;
    }

    /**
     * 这个实例是不是我们在放的歌。
     * 原版 MusicManager.canReplace() 只比较事件 id：我们的自定义事件和情境音乐
     * （菜单音乐、群系音乐）永远不相等，于是它会在下一个 tick 就把我们的歌停掉，
     * 表现出来就是「每首只放一秒」。识别出来交给 mixin 挡掉。
     */
    public static boolean isOurInstance(SoundInstance instance) {
        return instance != null && instance == currentInstance;
    }

    public static void noteSuccess() {
        lastFailed = null;
        failCount = 0;
    }

    /** 立刻停掉当前这首（界面上的「停止播放」）。 */
    public static void stopCurrent() {
        if (currentInstance != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.getSoundManager().stop(currentInstance);
            }
        }
        currentInstance = null;
        // 让 tick 钩子把间隔压成配置的秒数，下一首很快就会接上（队列空的时候无所谓）
        ourTrackPending = true;
        lastFailed = null;
        failCount = 0;
        NowPlaying.onStopped();
    }

    /**
     * 播放失败时把这首放回去重试。
     *
     * <p>最典型的是模组重载资源包的那一两秒：SoundEngine 处于未加载状态，
     * 任何播放请求都返回 NOT_STARTED。这种情况不能当成「这首放完了」，
     * 否则整个队列会被瞬间消耗光。同一首连续失败 10 次才真正丢掉。
     */
    public static boolean requeue(String file) {
        if (file.equals(lastFailed)) {
            if (++failCount >= 10) {
                CustomMusicClient.LOG.warn("{} 连续播放失败，跳过", file);
                noteSuccess();
                return false;
            }
        } else {
            lastFailed = file;
            failCount = 1;
        }

        if (isSituational()) {
            SituationalEngine.requeue(file);
        } else {
            PlaylistEngine.requeue(file);
        }
        NowPlaying.onStopped();
        return true;
    }

    /** 重试间隔：前几次短一点，好尽快穿过资源重载窗口。 */
    public static int retryDelayTicks() {
        return failCount <= 3 ? 5 : 20;
    }

    public static int gapTicks() {
        return Math.max(0, CustomMusicClient.config().trackGapSeconds) * 20;
    }
}
