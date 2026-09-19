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

    /** 选下一首；null 表示这次不接管，交回原版。 */
    public static com.custommusic.playback.Playback.Pick nextPick() {
        String file = pick();
        if (file == null) {
            return null;
        }
        return com.custommusic.playback.Playback.pickOf(file);
    }

    /** 播放失败时把这首放回队首（是否继续重试由 Playback 判断）。 */
    public static void requeue(String file) {
        if (lastPicked != null && lastPicked.file.equals(file)) {
            pending.addFirst(lastPicked);
            playedInPlaylist = Math.max(0, playedInPlaylist - 1);
        }
        currentTrack = null;
    }

    private static String pick() {
        CustomMusicConfig config = CustomMusicClient.config();

        // 队列里可能有已经被删掉的歌单，最多绕几圈避免死循环
        for (int guard = 0; guard < 64; guard++) {
            List<Playlist> playlists = CustomMusicClient.library().playlists();
            if (config.queue.isEmpty()) {
                return null;
            }

            // 队列里存的是「选择器」：可以是歌单 id，也可以是单个曲目文件
            Playlist current = resolveSelector(playlists, currentPlaylistId());
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
            // 队列接管时把试听停掉，免得两首一起响
            com.custommusic.preview.PreviewPlayer.stopQuietly();
            NowPlaying.onTrackStarted(entry.file);
            return entry.file;
        }
        return null;
    }

    /** 把队列里的一项解析成歌单：是歌单 id 就用整张，是单个曲目文件就包成一首的歌单。 */
    private static Playlist resolveSelector(List<Playlist> playlists, String selector) {
        Playlist playlist = MusicLibrary.findPlaylist(playlists, selector);
        if (playlist != null) {
            return playlist;
        }
        for (CustomMusicConfig.TrackEntry entry : CustomMusicClient.library().enabledTracks()) {
            if (entry.file.equals(selector)) {
                return new Playlist(selector, MusicLibrary.displayName(selector), List.of(entry));
            }
        }
        return null;
    }

    /** 立即播放当前所有启用的曲目（界面上的「播放全部」）。 */
    public static void playAll() {
        List<String> files = CustomMusicClient.library().enabledTracks().stream()
                .map(entry -> entry.file)
                .collect(java.util.stream.Collectors.toList());
        if (files.isEmpty()) {
            return;
        }
        CustomMusicConfig config = CustomMusicClient.config();
        config.queue = new ArrayList<>(files);
        config.queueIndex = 0;
        config.save();
        reset();
    }

    /** 停止播放：清空队列并立刻停掉当前这首。 */
    public static void stopPlayback() {
        clear();
        com.custommusic.playback.Playback.stopCurrent();
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
