package com.custommusic.hud;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.metadata.MetadataReader;
import com.custommusic.metadata.TrackMetadata;
import com.custommusic.music.MusicLibrary;
import com.custommusic.pack.PackPaths;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * 「正在播放」状态：歌名、艺术家、封面贴图、播放进度。
 *
 * <p>信息在后台线程读（ID3 封面 + OGG 时长），贴图注册回主线程做。
 * 封面是可选的：文件里没有内嵌封面就不显示。
 */
public final class NowPlaying {

    private static String trackFile;
    private static String title;
    private static String artist;
    private static long startedAtMillis;
    private static float durationSeconds = -1f;
    private static Identifier coverTexture;
    private static String coverOwner;
    private static String loadedFor;

    private NowPlaying() {
    }

    public static boolean isPlaying() {
        return trackFile != null;
    }

    public static String title() {
        return title;
    }

    public static String artist() {
        return artist;
    }

    public static Identifier coverTexture() {
        return coverTexture;
    }

    public static float durationSeconds() {
        return durationSeconds;
    }

    /** 0-1 的播放进度；时长未知时返回 -1。 */
    public static float progress() {
        if (durationSeconds <= 0) {
            return -1f;
        }
        float elapsed = (System.currentTimeMillis() - startedAtMillis) / 1000f;
        return Math.max(0f, Math.min(1f, elapsed / durationSeconds));
    }

    public static float elapsedSeconds() {
        return Math.max(0f, (System.currentTimeMillis() - startedAtMillis) / 1000f);
    }

    public static void onTrackStarted(String file) {
        trackFile = file;
        startedAtMillis = System.currentTimeMillis();

        // 同一首歌重复播放（列表循环、重试）不用再读一遍元数据
        if (file.equals(loadedFor)) {
            return;
        }

        loadedFor = null;
        title = MusicLibrary.displayName(file);
        artist = null;
        durationSeconds = -1f;
        releaseCover();

        Thread loader = new Thread(() -> load(file), "CustomMusic-Metadata");
        loader.setDaemon(true);
        loader.start();
    }

    public static void onStopped() {
        trackFile = null;
        title = null;
        artist = null;
        durationSeconds = -1f;
    }

    private static void load(String file) {
        MusicLibrary library = CustomMusicClient.library();
        Path source = null;
        for (CustomMusicConfig.TrackEntry entry : library.tracks()) {
            if (entry.file.equals(file)) {
                source = library.resolve(entry);
                break;
            }
        }

        TrackMetadata metadata = source == null ? TrackMetadata.EMPTY : MetadataReader.read(source);
        float duration = readDuration(file);
        CustomMusicClient.LOG.debug("元数据 {}：来源={} 封面={} 字节 时长={}",
                file, source == null ? "未找到" : "ok",
                metadata.cover() == null ? 0 : metadata.cover().length, duration);

        // 读的过程中用户可能已经切歌了
        if (!file.equals(trackFile)) {
            return;
        }
        loadedFor = file;
        durationSeconds = duration;
        if (metadata.title() != null) {
            title = metadata.title();
        }
        artist = metadata.artist();

        if (!metadata.hasCover()) {
            return;
        }
        try {
            NativeImage image = decodeCover(metadata.cover());
            Minecraft.getInstance().execute(() -> registerCover(file, image));
        } catch (Exception e) {
            CustomMusicClient.LOG.warn("封面解析失败: {}", file, e);
        }
    }

    /**
     * mp3 里的封面绝大多数是 JPEG，而 Minecraft 的 NativeImage 只认 PNG，
     * 所以先用 JDK 的 ImageIO 解出来、缩到 128px 再转成 PNG 喂给它。
     */
    private static NativeImage decodeCover(byte[] raw) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
        if (source == null) {
            throw new IOException("无法识别的图片格式");
        }
        int max = 128;
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IOException("封面尺寸异常");
        }
        if (Math.max(width, height) > max) {
            double scale = (double) max / Math.max(width, height);
            width = Math.max(1, (int) Math.round(width * scale));
            height = Math.max(1, (int) Math.round(height * scale));
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            source = scaled;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "png", out)) {
            throw new IOException("没有可用的 PNG 编码器");
        }
        return NativeImage.read(out.toByteArray());
    }

    private static void registerCover(String file, NativeImage image) {
        if (!file.equals(trackFile)) {
            image.close();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            image.close();
            return;
        }

        releaseCover();
        Identifier id = Identifier.fromNamespaceAndPath(
                CustomMusicClient.MOD_ID, "cover/" + MusicLibrary.trackId(file));
        minecraft.getTextureManager().register(id, new DynamicTexture(() -> "custommusic cover", image));
        coverTexture = id;
        coverOwner = file;
        CustomMusicClient.LOG.info("已加载内嵌封面 {}x{}：{}", image.getWidth(), image.getHeight(), file);
    }

    private static void releaseCover() {
        if (coverTexture != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.getTextureManager().release(coverTexture);
            }
            coverTexture = null;
        }
    }

    /** 用 JOrbis 读转码后 OGG 的总时长，失败就返回 -1（进度条退化成不定长）。 */
    private static float readDuration(String file) {
        Path ogg = PackPaths.musicDir().resolve(MusicLibrary.trackId(file) + ".ogg");
        if (!Files.isRegularFile(ogg)) {
            return -1f;
        }
        try {
            com.jcraft.jorbis.VorbisFile vorbis = new com.jcraft.jorbis.VorbisFile(ogg.toString());
            try {
                return vorbis.time_total(0);
            } finally {
                vorbis.close();
            }
        } catch (Throwable t) {
            CustomMusicClient.LOG.debug("读取时长失败: {}", file, t);
            return -1f;
        }
    }
}
