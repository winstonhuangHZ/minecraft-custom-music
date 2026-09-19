package com.custommusic.audio;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import com.custommusic.pack.PackPaths;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.Identifier;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 没有 ffmpeg 时的退路：MP3 不转码，直接把源文件交给 JLayer 解码播放。
 *
 * <p>资源包里那条 {@code track.xxx} 事件仍然存在，只是对应的 ogg 是个占位文件
 * （声音事件的校验只检查文件在不在）。真正要开流的时候，
 * {@code SoundBufferLibraryMixin} 会把原版的 OGG 解码换成这里的 MP3 流。
 */
public final class Mp3Fallback {

    /** 占位 ogg 小于这个大小就认为是占位（真的 ogg 起码几 KB）。 */
    public static final long PLACEHOLDER_SIZE = 512L;

    private Mp3Fallback() {
    }

    /** 这个声音文件是不是我们在用 MP3 直读；是就返回源文件相对路径，否则 null。 */
    public static String findSource(Identifier soundFile) {
        if (CustomMusicClient.library() == null) {
            return null;
        }
        // 注意：这里拿到的是 Sound.getPath()，形如 sounds/music/<trackId>.ogg
        String path = soundFile.getPath();
        String prefix = "sounds/music/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String trackId = path.substring(prefix.length());
        if (trackId.endsWith(".ogg")) {
            trackId = trackId.substring(0, trackId.length() - 4);
        }

        for (CustomMusicConfig.TrackEntry entry : CustomMusicClient.library().enabledTracks()) {
            if (!MusicLibrary.trackId(entry.file).equals(trackId)) {
                continue;
            }
            if (!entry.file.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                return null;
            }
            Path ogg = PackPaths.musicDir().resolve(trackId + ".ogg");
            return isPlaceholder(ogg) ? entry.file : null;
        }
        return null;
    }

    public static AudioStream openStream(String relativeFile) throws Exception {
        Path source = CustomMusicClient.library().folder().resolve(relativeFile);
        return new Mp3AudioStream(new BufferedInputStream(Files.newInputStream(source)));
    }

    public static boolean isPlaceholder(Path ogg) {
        try {
            return Files.isRegularFile(ogg) && Files.size(ogg) < PLACEHOLDER_SIZE;
        } catch (Exception e) {
            return false;
        }
    }

    /** 用 JLayer 扫一遍帧头算总时长（不解码），给进度条用。 */
    public static float durationSeconds(Path mp3) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(mp3))) {
            Bitstream bitstream = new Bitstream(in);
            float milliseconds = 0f;
            try {
                Header header;
                while ((header = bitstream.readFrame()) != null) {
                    milliseconds += header.ms_per_frame();
                    bitstream.closeFrame();
                }
            } finally {
                bitstream.close();
            }
            return milliseconds / 1000f;
        } catch (Throwable t) {
            CustomMusicClient.LOG.debug("估算 mp3 时长失败: {}", mp3, t);
            return -1f;
        }
    }
}
