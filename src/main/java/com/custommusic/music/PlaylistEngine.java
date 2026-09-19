package com.custommusic.music;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.hud.NowPlaying;
import com.custommusic.pack.PackGenerator;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 歌单播放引擎：决定「下一首放什么」。
 *
 * <p>MusicManager 每次想放音乐时会来问一次 {@link #nextSoundEvent()}，
 * 这里按队列顺序推进；返回 null 就把控制权交回原版音乐。
 * 因为是在「每首歌的边界」上推进，所以换歌单不会打断正在放的歌。
 */
public final class PlaylistEngine {

    private static final Deque<CustomMusicConfig.TrackEntry> pending = new ArrayDeque<>();
    private static String loadedPlaylistId;
    private static String currentTrack;
    private static CustomMusicConfig.TrackEntry lastPicked;
    private static String lastFailed;
    private static int failCount;
    private static SoundInstance currentInstance;
    private static long playlistStartedAt;
    private static int playedInPlaylist;

    private PlaylistEngine() {
    }

    /** 队列非空时才算接管。这个方法每 tick 都会被调用，别做重活。 */
    public static boolean active() {
        CustomMusicConfig config = CustomMusicClient.config();
        return config != null && !config.queue.isEmpty();
    }

    public static String currentTrack() {
        return currentTrack;
    }

    /** 记下我们启动的声音实例，用来识别「现在放的是我们的歌」。 */
    public static void markStarted(SoundInstance instance) {
        currentInstance = instance;
    }

    /**
     * 这个实例是不是我们正在放的歌。
     * 原版 MusicManager.canReplace() 只比较事件 id：我们的自定义事件和情境音乐
     * （菜单音乐、群系音乐）永远不相等，于是它会在下一个 tick 就把我们的歌停掉，
     * 表现出来就是「每首只放一秒」。这里识别出来交给 mixin 挡掉。
     */
    public static boolean isOurInstance(SoundInstance instance) {
        return instance != null && instance == currentInstance;
    }

    public static String currentPlaylistId() {
        CustomMusicConfig config = CustomMusicClient.config();
        if (config.queue.isEmpty()) {
            return null;
        }
        int index = Math.min(config.queueIndex, config.queue.size() - 1);
        return config.queue.get(index);
    }

    public static int playedInPlaylist() {
        return playedInPlaylist;
    }

    public static int gapTicks() {
        return Math.max(0, CustomMusicClient.config().trackGapSeconds) * 20;
    }

    // ------------------------------------------------------------ 队列操作

    /** 立刻改放这张歌单（清空原队列）。 */
    public static void playNow(String playlistId) {
        CustomMusicConfig config = CustomMusicClient.config();
        config.queue = new ArrayList<>(List.of(playlistId));
        config.queueIndex = 0;
        config.save();
        reset();
    }

    public static void enqueue(String playlistId) {
        CustomMusicConfig config = CustomMusicClient.config();
        config.queue.add(playlistId);
        config.save();
    }

    public static void removeAt(int index) {
        CustomMusicConfig config = CustomMusicClient.config();
        if (index < 0 || index >= config.queue.size()) {
            return;
        }
        config.queue.remove(index);
        if (config.queueIndex > index) {
            config.queueIndex--;
        } else if (config.queueIndex >= config.queue.size()) {
            config.queueIndex = 0;
            reset();
        }
        config.save();
    }

    public static void clear() {
        CustomMusicConfig config = CustomMusicClient.config();
        config.queue = new ArrayList<>();
        config.queueIndex = 0;
        config.save();
        reset();
    }

    /** 跳过当前歌单，立刻换下一张。 */
    public static void skipPlaylist() {
        currentTrack = null;
        pending.clear();
        advance(true);
    }

    /** 重新洗牌当前歌单的待播列表（改随机开关后用）。 */
    public static void reset() {
        pending.clear();
        loadedPlaylistId = null;
        currentTrack = null;
        playedInPlaylist = 0;
        playlistStartedAt = 0L;
    }

    // ------------------------------------------------------------ 选曲

    /** 一次选曲的结果。 */
    public record Pick(String file, SoundEvent event) {
    }

    /** 选下一首；null 表示这次不接管，交回原版。 */
    public static Pick nextPick() {
        String file = pick();
        if (file == null) {
            return null;
        }
        Identifier id = Identifier.fromNamespaceAndPath(
                PackGenerator.PREVIEW_NAMESPACE,
                PackGenerator.TRACK_PREFIX + MusicLibrary.trackId(file));
        return new Pick(file, SoundEvent.createVariableRangeEvent(id));
    }

    /**
     * 播放失败时把这首放回队首重试。
     *
     * <p>最典型的场景：模组启动时会重载资源包，重载期间 SoundEngine 处于未加载状态，
     * 任何播放请求都会返回 NOT_STARTED。这种情况不能当成「这首放完了」，
     * 否则整个队列会被瞬间消耗光。同一首连续失败 3 次才真正丢掉。
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

        if (lastPicked != null && lastPicked.file.equals(file)) {
            pending.addFirst(lastPicked);
            playedInPlaylist = Math.max(0, playedInPlaylist - 1);
        }
        currentTrack = null;
        return true;
    }

    /** 重试播放的间隔：前几次短一点，好尽快穿过资源重载窗口。 */
    public static int retryDelayTicks() {
        return failCount <= 3 ? 5 : 20;
    }

    /** 播放成功，清掉失败计数。 */
    public static void noteSuccess() {
        lastFailed = null;
        failCount = 0;
    }

    private static String pick() {
        CustomMusicConfig config = CustomMusicClient.config();

        // 队列里可能有已经被删掉的歌单，最多绕几圈避免死循环
        for (int guard = 0; guard < 64; guard++) {
            List<Playlist> playlists = CustomMusicClient.library().playlists();
            if (playlists.isEmpty() || config.queue.isEmpty()) {
                return null;
            }

            Playlist current = MusicLibrary.findPlaylist(playlists, currentPlaylistId());
            if (current == null) {
                config.queue.remove(Math.min(config.queueIndex, config.queue.size() - 1));
                if (config.queueIndex >= config.queue.size()) {
                    config.queueIndex = 0;
                }
                config.save();
                continue;
            }

            if (!current.id().equals(loadedPlaylistId)) {
                loadPlaylist(current);
            }

            if (pending.isEmpty() || limitsReached(config)) {
                if (!advance(false)) {
                    return null;
                }
                continue;
            }

            CustomMusicConfig.TrackEntry entry = pending.poll();
            playedInPlaylist++;
            currentTrack = entry.file;
            lastPicked = entry;
            NowPlaying.onTrackStarted(entry.file);
            return entry.file;
        }
        return null;
    }

    private static boolean limitsReached(CustomMusicConfig config) {
        if (playedInPlaylist == 0) {
            return false;
        }
        if (config.tracksPerPlaylist > 0 && playedInPlaylist >= config.tracksPerPlaylist) {
            return true;
        }
        return config.minutesPerPlaylist > 0
                && System.currentTimeMillis() - playlistStartedAt >= config.minutesPerPlaylist * 60_000L;
    }

    private static void loadPlaylist(Playlist playlist) {
        pending.clear();
        List<CustomMusicConfig.TrackEntry> entries = new ArrayList<>(playlist.tracks());
        if (CustomMusicClient.config().shuffleTracks) {
            Collections.shuffle(entries);
        }
        pending.addAll(entries);
        loadedPlaylistId = playlist.id();
        currentTrack = null;
        playedInPlaylist = 0;
        playlistStartedAt = System.currentTimeMillis();
        CustomMusicClient.LOG.info("开始播放歌单 {}（{} 首）", playlist.name(), playlist.size());
    }

    /** 切到队列里的下一张；返回 false 表示队列已经放完。 */
    private static boolean advance(boolean force) {
        CustomMusicConfig config = CustomMusicClient.config();
        if (config.queue.isEmpty()) {
            return false;
        }
        config.queueIndex++;
        if (config.queueIndex >= config.queue.size()) {
            if (config.loopQueue) {
                config.queueIndex = 0;
            } else {
                CustomMusicClient.LOG.info("歌单队列播放完毕，交回原版音乐");
                config.queue = new ArrayList<>();
                config.queueIndex = 0;
                config.save();
                reset();
                NowPlaying.onStopped();
                return false;
            }
        }
        pending.clear();
        loadedPlaylistId = null;
        playedInPlaylist = 0;
        config.save();
        return true;
    }
}
