package com.custommusic.playback;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.hud.NowPlaying;
import com.custommusic.music.MusicLibrary;
import com.custommusic.music.Playlist;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 情境模式：游戏想放哪首情境音乐，就换成你给这个情境配的曲子。
 *
 * <p>情境 id 就是原版那个配乐事件，比如 {@code minecraft:music.nether.nether_wastes}；
 * 我们拦截 {@code startPlaying(Music)} 时能直接拿到它。每个情境自己维护一个待播池，
 * 池子空了就重新装填（开了随机就打乱），所以同一个情境里会轮着放你配的那几首。
 */
public final class SituationalEngine {

    private static final Map<String, Deque<CustomMusicConfig.TrackEntry>> pools = new HashMap<>();
    private static final Map<String, String> lastPicked = new HashMap<>();
    private static String currentContext;

    private SituationalEngine() {
    }

    public static boolean active() {
        CustomMusicConfig config = CustomMusicClient.config();
        return config != null && config.isSituational() && !config.assignments.isEmpty();
    }

    public static String currentContext() {
        return currentContext;
    }

    /** 某个情境轮到哪首；null = 没配规则或曲子都没了，交回原版。 */
    public static Playback.Pick next(Identifier context) {
        if (context == null) {
            return null;
        }
        String key = context.toString();
        List<CustomMusicConfig.TrackEntry> tracks = resolve(key);
        if (tracks.isEmpty()) {
            pools.remove(key);
            return null;
        }

        Deque<CustomMusicConfig.TrackEntry> pool = pools.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (pool.isEmpty()) {
            refill(pool, tracks);
        }
        CustomMusicConfig.TrackEntry entry = pool.poll();
        if (entry == null) {
            return null;
        }

        currentContext = key;
        lastPicked.put(key, entry.file);
        NowPlaying.onTrackStarted(entry.file);
        return Playback.pickOf(entry.file);
    }

    /** 播放失败时把这首放回该情境的队首。 */
    public static void requeue(String file) {
        String key = currentContext;
        if (key == null) {
            return;
        }
        Deque<CustomMusicConfig.TrackEntry> pool = pools.get(key);
        if (pool == null) {
            return;
        }
        String previous = lastPicked.get(key);
        if (file.equals(previous)) {
            CustomMusicConfig.TrackEntry entry = findEntry(file);
            if (entry != null) {
                pool.addFirst(entry);
            }
        }
    }

    /** 配置改了以后清掉运行状态，下次重新装填。 */
    public static void reset() {
        pools.clear();
        lastPicked.clear();
        currentContext = null;
    }

    // ------------------------------------------------------------------

    /** 把某个情境配置里的「选择器」解析成实际曲目：可以写歌单 id（文件夹），也可以写单个曲目文件。 */
    public static List<CustomMusicConfig.TrackEntry> resolve(String contextId) {
        CustomMusicConfig config = CustomMusicClient.config();
        List<String> selectors = config.assignments.get(contextId);
        if (selectors == null || selectors.isEmpty()) {
            return List.of();
        }

        List<Playlist> playlists = CustomMusicClient.library().playlists();
        List<CustomMusicConfig.TrackEntry> result = new ArrayList<>();
        for (String selector : selectors) {
            if (selector == null || selector.isBlank()) {
                continue;
            }
            Playlist playlist = MusicLibrary.findPlaylist(playlists, selector);
            if (playlist != null) {
                result.addAll(playlist.tracks());
                continue;
            }
            CustomMusicConfig.TrackEntry entry = findEntry(selector);
            if (entry != null && entry.enabled) {
                result.add(entry);
            }
        }
        return result;
    }

    public static CustomMusicConfig.TrackEntry findEntry(String file) {
        for (CustomMusicConfig.TrackEntry entry : CustomMusicClient.library().tracks()) {
            if (entry.file.equals(file)) {
                return entry;
            }
        }
        return null;
    }

    private static void refill(Deque<CustomMusicConfig.TrackEntry> pool, List<CustomMusicConfig.TrackEntry> tracks) {
        List<CustomMusicConfig.TrackEntry> copy = new ArrayList<>(tracks);
        if (CustomMusicClient.config().shuffleTracks) {
            Collections.shuffle(copy);
        }
        pool.addAll(copy);
    }
}
