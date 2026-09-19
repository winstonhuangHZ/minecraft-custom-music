package com.custommusic.pack;

import com.custommusic.CustomMusicClient;
import com.custommusic.audio.AudioConverter;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 生成资源包：转码 + 写 sounds.json / pack.mcmeta。 */
public final class PackGenerator {

    /** 每首歌一个事件的前缀，试听和歌单播放都走它。 */
    public static final String PREVIEW_NAMESPACE = "custommusic";
    public static final String TRACK_PREFIX = "track.";

    public interface Progress {
        void onProgress(int done, int total, String current);
    }

    public record Result(int converted, int cached, int failed, List<String> errors) {
        public boolean ok() {
            return failed == 0;
        }
    }

    private PackGenerator() {
    }

    public static Result generate(MusicLibrary library, CustomMusicConfig config,
                                  java.util.Map<String, java.util.SortedSet<String>> events, Progress progress)
            throws IOException {
        List<CustomMusicConfig.TrackEntry> enabled = library.enabledTracks();
        Path musicDir = PackPaths.musicDir();
        Files.createDirectories(musicDir);

        List<String> ids = new ArrayList<>();
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int converted = 0;
        int cached = 0;
        int failed = 0;
        int total = enabled.size();
        int index = 0;

        boolean ffmpegReady = AudioConverter.available(config.ffmpegPath);

        for (CustomMusicConfig.TrackEntry entry : enabled) {
            index++;
            if (progress != null) {
                progress.onProgress(index - 1, total, MusicLibrary.displayName(entry.file));
            }

            Path source = library.resolve(entry);
            if (!Files.isRegularFile(source)) {
                failed++;
                errors.add("文件不存在: " + entry.file);
                continue;
            }

            String id = MusicLibrary.trackId(entry.file);
            Path target = musicDir.resolve(id + ".ogg");

            if (AudioConverter.isUpToDate(source, target)) {
                ids.add(id);
                names.put(id, MusicLibrary.displayName(entry.file));
                cached++;
                continue;
            }

            try {
                if (ffmpegReady) {
                    AudioConverter.convert(config.ffmpegPath, source, target, config.vorbisQuality);
                } else if (isOgg(source)) {
                    AudioConverter.copy(source, target);
                } else {
                    failed++;
                    errors.add("没有可用的 ffmpeg，无法转换 " + entry.file);
                    continue;
                }
                ids.add(id);
                names.put(id, MusicLibrary.displayName(entry.file));
                converted++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed++;
                errors.add("转码被中断: " + entry.file);
            } catch (Exception e) {
                failed++;
                errors.add(entry.file + " -> " + e.getMessage());
                CustomMusicClient.LOG.error("转码失败: {}", entry.file, e);
            }
        }

        pruneOrphans(musicDir, ids);
        writeOverrideSoundsJson(events, ids);
        writeLangFiles(names);
        writePackMeta(enabled.size());

        if (progress != null) {
            progress.onProgress(total, total, "");
        }
        return new Result(converted, cached, failed, List.copyOf(errors));
    }

    private static boolean isOgg(Path source) {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ogg") || name.endsWith(".oga");
    }

    private static void pruneOrphans(Path musicDir, List<String> ids) throws IOException {
        try (var stream = Files.list(musicDir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".ogg")) {
                    continue;
                }
                String id = name.substring(0, name.length() - 4);
                if (!ids.contains(id)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /**
     * 生成覆盖用的 sounds.json。按命名空间分别写文件：
     * key 只能是「路径」，命名空间由文件所在目录决定，所以 minecraft 的事件写进
     * assets/minecraft/sounds.json，别的模组的写进它们自己的命名空间目录（资源包之间是合并的）。
     */
    private static void writeOverrideSoundsJson(java.util.Map<String, java.util.SortedSet<String>> events,
                                                List<String> ids) throws IOException {
        for (java.util.Map.Entry<String, java.util.SortedSet<String>> entry : events.entrySet()) {
            List<String> blocks = new ArrayList<>();
            for (String event : entry.getValue()) {
                StringBuilder block = new StringBuilder();
                block.append("  \"").append(event).append("\": {\n");
                // replace:true 才会盖掉原版音乐，否则只是「追加」
                block.append("    \"replace\": true,\n");
                block.append("    \"sounds\": [\n");
                for (int i = 0; i < ids.size(); i++) {
                    block.append("      { \"name\": \"music/").append(ids.get(i))
                            .append("\", \"stream\": true }");
                    block.append(i < ids.size() - 1 ? ",\n" : "\n");
                }
                block.append("    ]\n  }");
                blocks.add(block.toString());
            }

            Path file = PackPaths.soundsJson(entry.getKey());
            if (blocks.isEmpty()) {
                Files.deleteIfExists(file);
                continue;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\n" + String.join(",\n", blocks) + "\n}\n", StandardCharsets.UTF_8);
            CustomMusicClient.LOG.info("覆盖 {} 命名空间下 {} 个配乐事件", entry.getKey(), blocks.size());
        }

        pruneStaleOverrideFiles(events.keySet());
        writeTrackSoundsJson(ids);
    }

    /**
     * 删掉这次不再需要的覆盖文件。
     * 情境模式下 events 是空的，所以切模式之后原来那份 assets/minecraft/sounds.json
     * 会被清掉，原版配乐就恢复了（未配置的情境保持原样）。
     */
    private static void pruneStaleOverrideFiles(java.util.Set<String> namespaces) throws IOException {
        Path assets = PackPaths.packDir().resolve("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (var stream = Files.list(assets)) {
            for (Path namespaceDir : stream.filter(Files::isDirectory).toList()) {
                String namespace = namespaceDir.getFileName().toString();
                if (namespaces.contains(namespace) || namespace.equals(PREVIEW_NAMESPACE)) {
                    continue;
                }
                Files.deleteIfExists(namespaceDir.resolve("sounds.json"));
            }
        }
    }

    /**
     * 试听事件单独放一份 sounds.json。
     * 26.x 里 sounds.json 的 key 只能是「路径」，命名空间由文件所在目录决定：
     * assets/custommusic/sounds.json 里的 preview.xxx 就是 custommusic:preview.xxx。
     * 曲名（name）用完整的 minecraft:music/... 指回另一棵树里的 ogg。
     */
    private static void writeTrackSoundsJson(List<String> ids) throws IOException {
        List<String> blocks = new ArrayList<>();
        for (String id : ids) {
            blocks.add("  \"" + TRACK_PREFIX + id + "\": {\n"
                    + "    \"sounds\": [\n"
                    + "      { \"name\": \"minecraft:music/" + id + "\", \"stream\": true }\n"
                    + "    ]\n  }");
        }
        Path file = PackPaths.previewSoundsJson();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\n" + String.join(",\n", blocks) + "\n}\n", StandardCharsets.UTF_8);
    }

    /**
     * 生成曲名翻译，让原版的「正在播放」提示显示歌名而不是内部 id。
     * 原版是从音频文件路径推翻译键的，所以几个可能的形式都写上，多余的无害。
     */
    private static void writeLangFiles(java.util.Map<String, String> names) throws IOException {
        java.util.Map<String, String> lang = new java.util.LinkedHashMap<>();
        names.forEach((id, name) -> {
            lang.put("music." + id, name);
            lang.put("sound.custommusic.track." + id, name);
            lang.put("custommusic.track." + id, name);
        });

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        String json = gson.toJson(lang);

        for (String locale : List.of("en_us", "zh_cn")) {
            Path file = PackPaths.langFile(locale);
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        }
    }

    private static void writePackMeta(int trackCount) throws IOException {
        // 26.x 的 pack.mcmeta 用 min_format / max_format（PackFormat = [主版本, 次版本]）。
        // 老写法 pack_format + supported_formats 在这里会被判定成 UNKNOWN（不兼容）。
        int major = SharedConstants.RESOURCE_PACK_FORMAT_MAJOR;
        int minor = SharedConstants.RESOURCE_PACK_FORMAT_MINOR;
        String json = """
                {
                  "pack": {
                    "description": "Custom Music (%d tracks)",
                    "min_format": [%d, %d],
                    "max_format": [%d, %d]
                  }
                }
                """.formatted(trackCount, major, minor, major, minor);
        Path file = PackPaths.packMeta();
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }
}
