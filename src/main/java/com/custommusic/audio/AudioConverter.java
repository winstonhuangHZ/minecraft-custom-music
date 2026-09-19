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

    private AudioConverter() {
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
