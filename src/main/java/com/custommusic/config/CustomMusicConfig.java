package com.custommusic.config;

import com.custommusic.CustomMusicClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 模组配置，存在 config/custommusic/config.json。 */
public final class CustomMusicConfig {

    /** 自定义音乐文件夹；留空表示用默认的 config/custommusic/music。 */
    public String musicFolder = "";

    /** ffmpeg 可执行文件，可以填绝对路径。 */
    public String ffmpegPath = "ffmpeg";

    /** Vorbis 质量 0-10，5 大约等于 160kbps。 */
    public int vorbisQuality = 5;

    /** 要覆盖的原版音乐事件。 */
    public List<String> overrideEvents = new ArrayList<>(List.of(
            "music.game", "music.creative", "music.menu", "music.under_water"));

    /** 启动时自动扫描并转码。 */
    public boolean autoSyncOnStart = true;

    /** 曲目列表，顺序决定资源包里事件的条目顺序。 */
    public List<TrackEntry> tracks = new ArrayList<>();

    /** 上次成功生成资源包的时间戳。 */
    public long lastGeneratedAt = 0L;

    // ---------------- 歌单 / 队列 ----------------

    /** 歌单队列：存歌单 id（文件夹相对路径，顶层散文件是空串）。 */
    public List<String> queue = new ArrayList<>();

    /** 当前播到队列里的第几个歌单。 */
    public int queueIndex = 0;

    /** 歌单内是否随机播放。 */
    public boolean shuffleTracks = false;

    /** 一张歌单最多播几首，0 = 播完为止。 */
    public int tracksPerPlaylist = 0;

    /** 一张歌单最多播几分钟，0 = 不限制。 */
    public int minutesPerPlaylist = 0;

    /** 队列播完后是否循环。 */
    public boolean loopQueue = false;

    /** 两首歌之间的间隔秒数。 */
    public int trackGapSeconds = 2;

    /** 是否在右上角显示「正在播放」小标签。 */
    public boolean showHud = true;

    public static final class TrackEntry {
        /** 相对音乐文件夹的路径，用 / 分隔。 */
        public String file;
        public boolean enabled = true;

        public TrackEntry() {
        }

        public TrackEntry(String file, boolean enabled) {
            this.file = file;
            this.enabled = enabled;
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static Path file() {
        return CustomMusicClient.configDir().resolve("config.json");
    }

    public static CustomMusicConfig load() {
        Path file = file();
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                CustomMusicConfig config = GSON.fromJson(json, CustomMusicConfig.class);
                if (config != null) {
                    config.fixup();
                    return config;
                }
            }
        } catch (Exception e) {
            CustomMusicClient.LOG.error("读取配置失败，改用默认配置", e);
        }
        CustomMusicConfig config = new CustomMusicConfig();
        config.fixup();
        return config;
    }

    private void fixup() {
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            ffmpegPath = "ffmpeg";
        }
        if (overrideEvents == null || overrideEvents.isEmpty()) {
            overrideEvents = new ArrayList<>(List.of(
                    "music.game", "music.creative", "music.menu", "music.under_water"));
        }
        if (tracks == null) {
            tracks = new ArrayList<>();
        }
        if (queue == null) {
            queue = new ArrayList<>();
        }
        vorbisQuality = Math.max(0, Math.min(10, vorbisQuality));
        tracksPerPlaylist = Math.max(0, tracksPerPlaylist);
        minutesPerPlaylist = Math.max(0, minutesPerPlaylist);
        trackGapSeconds = Math.max(0, Math.min(60, trackGapSeconds));
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CustomMusicClient.LOG.error("保存配置失败", e);
        }
    }

    /** 解析后的音乐文件夹绝对路径。 */
    public Path resolveMusicFolder() {
        if (musicFolder != null && !musicFolder.isBlank()) {
            return Path.of(musicFolder.replace("~", System.getProperty("user.home")))
                    .toAbsolutePath().normalize();
        }
        return CustomMusicClient.configDir().resolve("music");
    }
}
