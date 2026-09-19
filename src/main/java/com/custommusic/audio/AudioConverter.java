package com.custommusic.audio;

import com.custommusic.CustomMusicClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 调 ffmpeg 把任意音频转成 Minecraft 能解码的 OGG Vorbis。
 * 纯 Java 没有可用的 Vorbis 编码器，所以走外部进程：优先 libvorbis，
 * 精简版 ffmpeg 里只有内置 vorbis 编码器（要 -strict -2，音质差一些）。
 */
public final class AudioConverter {

    public enum Encoder {
        LIBVORBIS("-c:a", "libvorbis"),
        NATIVE_VORBIS("-c:a", "vorbis", "-strict", "-2"),
        MISSING();

        private final String[] args;

        Encoder(String... args) {
            this.args = args;
        }
    }

    private static Encoder detected;
    private static String detectedFor;
    private static String resolvedFfmpeg;

    private AudioConverter() {
    }

    /**
     * 找出可用的 ffmpeg：先看配置里写的，再试 PATH，最后扫常见安装位置。
     * 返回 null 表示没找到（这时会退回到纯 Java 的 MP3 直读播放）。
     */
    public static synchronized String resolveFfmpeg(String configured) {
        if (resolvedFfmpeg != null) {
            return resolvedFfmpeg.isEmpty() ? null : resolvedFfmpeg;
        }
        // 配置里写 none/off/disabled 表示「明确不要用 ffmpeg」，直接走纯 Java 直读
        if (configured != null && List.of("none", "off", "disabled", "disabled;")
                .contains(configured.trim().toLowerCase(java.util.Locale.ROOT))) {
            resolvedFfmpeg = "";
            CustomMusicClient.LOG.info("配置里禁用了 ffmpeg，使用纯 Java 的 MP3 直读播放");
            return null;
        }

        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured.trim());
        }
        candidates.add("ffmpeg"); // PATH
        candidates.addAll(commonFfmpegPaths());

        for (String candidate : candidates) {
            if (works(candidate)) {
                resolvedFfmpeg = candidate;
                if (!candidate.equals(configured)) {
                    CustomMusicClient.LOG.info("自动找到 ffmpeg: {}", candidate);
                }
                return candidate;
            }
        }
        resolvedFfmpeg = "";
        CustomMusicClient.LOG.warn("没找到 ffmpeg，将使用纯 Java 的 MP3 直读播放（只支持 mp3/wav）");
        return null;
    }

    private static boolean works(String candidate) {
        try {
            String out = run(candidate, "-version");
            return out.contains("ffmpeg version") || out.contains("ffmpeg");
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> commonFfmpegPaths() {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> paths = new ArrayList<>();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("ProgramFiles");
            String userProfile = System.getenv("USERPROFILE");
            String programData = System.getenv("ProgramData");
            if (localAppData != null) {
                paths.add(localAppData + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe");
            }
            if (programFiles != null) {
                paths.add(programFiles + "\\ffmpeg\\bin\\ffmpeg.exe");
            }
            if (userProfile != null) {
                paths.add(userProfile + "\\scoop\\shims\\ffmpeg.exe");
            }
            if (programData != null) {
                paths.add(programData + "\\chocolatey\\bin\\ffmpeg.exe");
            }
            paths.add("C:\\ffmpeg\\bin\\ffmpeg.exe");
        } else {
            paths.add("/opt/homebrew/bin/ffmpeg");   // macOS (Apple Silicon)
            paths.add("/usr/local/bin/ffmpeg");      // macOS (Intel) / 手动编译
            paths.add("/opt/local/bin/ffmpeg");      // MacPorts
            paths.add("/usr/bin/ffmpeg");            // Linux
            paths.add("/snap/bin/ffmpeg");
            paths.add("/app/bin/ffmpeg");
        }
        return paths;
    }

    /** 探测可用的编码器，结果缓存。 */
    public static synchronized Encoder encoder(String ffmpeg) {
        if (detected != null && ffmpeg.equals(detectedFor)) {
            return detected;
        }
        detectedFor = ffmpeg;
        try {
            String out = run(ffmpeg, "-hide_banner", "-encoders");
            if (out.contains("libvorbis")) {
                detected = Encoder.LIBVORBIS;
            } else if (out.contains(" vorbis ")) {
                CustomMusicClient.LOG.warn("该 ffmpeg 没有 libvorbis，改用内置 vorbis 编码器（音质较差）");
                detected = Encoder.NATIVE_VORBIS;
            } else {
                detected = Encoder.MISSING;
            }
        } catch (Exception e) {
            CustomMusicClient.LOG.warn("找不到可用的 ffmpeg: {}", ffmpeg);
            detected = Encoder.MISSING;
        }
        return detected;
    }

    public static boolean available(String ffmpeg) {
        return encoder(ffmpeg) != Encoder.MISSING;
    }

    /** 有没有可用的 ffmpeg（顺带做一次自动探测）。 */
    public static boolean ffmpegPresent(String configured) {
        return resolveFfmpeg(configured) != null;
    }

    /** 目标文件比源文件新就认为缓存有效。 */
    public static boolean isUpToDate(Path source, Path target) {
        try {
            return Files.isRegularFile(target)
                    && Files.size(target) > 0
                    && Files.getLastModifiedTime(target).compareTo(Files.getLastModifiedTime(source)) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** 转码，失败抛异常。 */
    public static void convert(String ffmpeg, Path source, Path target, int quality)
            throws IOException, InterruptedException {
        Encoder encoder = encoder(ffmpeg);
        if (encoder == Encoder.MISSING) {
            throw new IOException("ffmpeg 不可用: " + ffmpeg);
        }

        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-i", source.toString(),
                "-vn", "-map_metadata", "-1"));
        command.addAll(List.of(encoder.args));
        command.addAll(List.of(
                "-q:a", String.valueOf(quality),
                "-ar", "44100", "-ac", "2",
                target.toString()));

        Files.createDirectories(target.getParent());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0 || !Files.isRegularFile(target) || Files.size(target) == 0) {
            Files.deleteIfExists(target);
            throw new IOException("转码失败 (" + code + "): " + source.getFileName() + " " + log.trim());
        }
    }

    /** 直接拷贝，用于源文件本来就是 ogg、但没有 ffmpeg 的情况。 */
    public static void copy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return out;
    }
}
