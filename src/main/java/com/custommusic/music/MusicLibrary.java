package com.custommusic.music;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 管理音乐文件夹里有哪些曲子、哪些启用、什么顺序。 */
public final class MusicLibrary {

    public static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "aiff", "aif");

    private final CustomMusicConfig config;
    private final List<CustomMusicConfig.TrackEntry> tracks = new ArrayList<>();
    private Path folder;

    public MusicLibrary(CustomMusicConfig config) {
        this.config = config;
    }

    public Path folder() {
        if (folder == null) {
            folder = config.resolveMusicFolder();
        }
        return folder;
    }

    /** 重新扫描文件夹，保留已有曲目的启用状态与顺序，新文件追加到末尾。 */
    public void scan() {
        Path dir = folder();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            CustomMusicClient.LOG.error("创建音乐文件夹失败: {}", dir, e);
        }

        List<Path> found = listAudioFiles(dir);
        Set<String> foundNames = found.stream().map(this::relativeName).collect(Collectors.toSet());

        List<CustomMusicConfig.TrackEntry> merged = new ArrayList<>();
        Set<String> kept = new HashSet<>();
        for (CustomMusicConfig.TrackEntry entry : config.tracks) {
            if (entry.file == null || !foundNames.contains(entry.file) || !kept.add(entry.file)) {
                continue;
            }
            merged.add(entry);
        }
        for (Path path : found) {
            String name = relativeName(path);
            if (kept.add(name)) {
                merged.add(new CustomMusicConfig.TrackEntry(name, true));
            }
        }

        // 一次性修顺序：老版本是按字典序排的（1 -> 10 -> 11 -> 2）。
        // 如果现在的顺序恰好还是那个字典序，说明用户没手动调过，就按自然序重排一次。
        if (looksLikeLegacyOrder(merged)) {
            merged.sort((a, b) -> compareNatural(a.file, b.file));
            CustomMusicClient.LOG.info("检测到旧的字典序，已按文件名自然顺序重排");
        }

        boolean changed = merged.size() != config.tracks.size();
        if (!changed) {
            for (int i = 0; i < merged.size(); i++) {
                CustomMusicConfig.TrackEntry a = merged.get(i);
                CustomMusicConfig.TrackEntry b = config.tracks.get(i);
                if (!a.file.equals(b.file) || a.enabled != b.enabled) {
                    changed = true;
                    break;
                }
            }
        }

        config.tracks = merged;
        tracks.clear();
        tracks.addAll(merged);

        CustomMusicClient.LOG.info("扫描完成，共 {} 首曲目", tracks.size());
        if (changed) {
            config.save();
        }
    }

    private List<Path> listAudioFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !isHidden(p, dir))
                    .filter(p -> AUDIO_EXTENSIONS.contains(extension(p)))
                    .sorted(Comparator.comparing(Path::toString, MusicLibrary::compareNatural))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            CustomMusicClient.LOG.error("遍历音乐文件夹失败", e);
            return List.of();
        }
    }

    private static boolean isHidden(Path path, Path root) {
        for (Path part : root.relativize(path)) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private String relativeName(Path path) {
        return folder().relativize(path).toString().replace('\\', '/');
    }

    public Path resolve(CustomMusicConfig.TrackEntry entry) {
        return folder().resolve(entry.file);
    }

    public List<CustomMusicConfig.TrackEntry> tracks() {
        return tracks;
    }

    public List<CustomMusicConfig.TrackEntry> enabledTracks() {
        return tracks.stream().filter(t -> t.enabled).collect(Collectors.toList());
    }

    /**
     * 按文件夹把启用的曲子分组成功歌单。
     * 只有启用的曲子会被转码、才会在资源包里生成事件，所以歌单同样只看启用的。
     */
    public List<Playlist> playlists() {
        Map<String, List<CustomMusicConfig.TrackEntry>> grouped = new LinkedHashMap<>();
        for (CustomMusicConfig.TrackEntry entry : enabledTracks()) {
            grouped.computeIfAbsent(folderOf(entry.file), key -> new ArrayList<>()).add(entry);
        }

        List<Playlist> result = new ArrayList<>();
        grouped.forEach((folder, entries) ->
                result.add(new Playlist(folder, nameOf(folder), List.copyOf(entries))));
        result.sort((a, b) -> compareNatural(a.name(), b.name()));
        return result;
    }

    /** 这个顺序是不是还停留在旧的字典序（说明没人手动调过）。 */
    private static boolean looksLikeLegacyOrder(List<CustomMusicConfig.TrackEntry> entries) {
        if (entries.size() < 2) {
            return false;
        }
        List<String> current = entries.stream().map(e -> e.file).collect(Collectors.toList());
        List<String> lexicographic = new ArrayList<>(current);
        lexicographic.sort(Comparator.naturalOrder());
        if (!current.equals(lexicographic)) {
            return false;
        }
        List<String> natural = new ArrayList<>(current);
        natural.sort(MusicLibrary::compareNatural);
        return !current.equals(natural);
    }

    public static Playlist findPlaylist(List<Playlist> playlists, String id) {
        for (Playlist playlist : playlists) {
            if (playlist.id().equals(id)) {
                return playlist;
            }
        }
        return null;
    }

    private static String folderOf(String relativeFile) {
        int slash = relativeFile.lastIndexOf('/');
        return slash < 0 ? Playlist.ROOT_ID : relativeFile.substring(0, slash);
    }

    private static String nameOf(String folder) {
        if (folder.isEmpty()) {
            return "未分组";
        }
        int slash = folder.lastIndexOf('/');
        return slash < 0 ? folder : folder.substring(slash + 1);
    }

    /**
     * 自然序比较：让 "2.mp3" 排在 "10.mp3" 前面。
     * 默认的字符串比较是逐字符的，所以会出现 1 -> 10 -> 11 -> 2 这种反直觉顺序。
     */
    public static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String numA = stripLeadingZeros(a.substring(startA, i));
                String numB = stripLeadingZeros(b.substring(startB, j));
                int byLength = Integer.compare(numA.length(), numB.length());
                if (byLength != 0) {
                    return byLength;
                }
                int byValue = numA.compareTo(numB);
                if (byValue != 0) {
                    return byValue;
                }
            } else {
                int byChar = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (byChar != 0) {
                    return byChar;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static String stripLeadingZeros(String value) {
        int start = 0;
        while (start < value.length() - 1 && value.charAt(start) == '0') {
            start++;
        }
        return value.substring(start);
    }

    public int enabledCount() {
        return (int) tracks.stream().filter(t -> t.enabled).count();
    }

    public void setEnabled(int index, boolean enabled) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        tracks.get(index).enabled = enabled;
        config.save();
    }

    public void setAllEnabled(boolean enabled) {
        for (CustomMusicConfig.TrackEntry entry : tracks) {
            entry.enabled = enabled;
        }
        config.save();
    }

    public void move(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= tracks.size() || target < 0 || target >= tracks.size()) {
            return;
        }
        CustomMusicConfig.TrackEntry entry = tracks.remove(index);
        tracks.add(target, entry);
        config.tracks = new ArrayList<>(tracks);
        config.save();
    }

    public CustomMusicConfig config() {
        return config;
    }

    /** 给每首歌生成稳定的资源包内文件名：t[hash]_[slug]。 */
    public static String trackId(String relativeFile) {
        return "t" + shortHash(relativeFile) + "_" + slug(relativeFile);
    }

    public static String displayName(String relativeFile) {
        String name = relativeFile.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String slug(String relativeFile) {
        String base = displayName(relativeFile).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.length() > 32) {
            base = base.substring(0, 32);
        }
        return base.isEmpty() ? "track" : base;
    }

    /** 给歌单 id 生成安全的 slug。 */
    public static String slugOf(String value) {
        String base = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        return base.isEmpty() ? "group" : base;
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.format("%08x", value.hashCode());
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
