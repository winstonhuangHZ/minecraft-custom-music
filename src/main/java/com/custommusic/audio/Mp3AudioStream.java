package com.custommusic.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 用纯 Java 的 JLayer 把 MP3 直接解成 PCM 喂给游戏的声音引擎 —— 完全不经过 OGG，也就不需要 ffmpeg。
 *
 * <p>游戏要的格式：{@code AudioFormat(采样率, 16, 声道, signed=true, bigEndian=false)}
 * （和原版 JOrbisAudioStream 保持一致），所以我们输出小端 16 位 PCM。
 */
public final class Mp3AudioStream implements AudioStream {

    private final InputStream input;
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;

    private SampleBuffer buffer;
    private int position;
    private boolean ended;

    public Mp3AudioStream(InputStream input) throws IOException {
        this.input = input;
        this.bitstream = new Bitstream(input);
        try {
            Header header = bitstream.readFrame();
            if (header == null) {
                throw new IOException("不是有效的 mp3 文件");
            }
            SampleBuffer first = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            if (first == null || first.getChannelCount() <= 0) {
                throw new IOException("mp3 解码失败");
            }
            this.buffer = first;
            this.position = first.getBufferLength();
            this.format = new AudioFormat(first.getSampleFrequency(), 16,
                    first.getChannelCount(), true, false);
            bitstream.closeFrame();
            com.custommusic.CustomMusicClient.LOG.info(
                    "MP3 直读播放：{} Hz / {} 声道（未经 OGG 转码）",
                    first.getSampleFrequency(), first.getChannelCount());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("初始化 mp3 解码器失败", e);
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int length) throws IOException {
        // 必须是 direct buffer：这个缓冲会直接交给 OpenAL（alBufferData），
        // 堆内 buffer 传进去会拿到无效指针，实测会直接把 JVM 崩掉。
        // 原版 JOrbisAudioStream 也是用 BufferUtils.createByteBuffer 分配的。
        ByteBuffer out = org.lwjgl.BufferUtils.createByteBuffer(length);
        try {
            while (out.remaining() >= 2) {
                if (buffer == null || position >= buffer.getBufferLength()) {
                    if (!decodeNextFrame()) {
                        break;
                    }
                }
                short[] samples = buffer.getBuffer();
                int count = buffer.getBufferLength();
                while (position < count && out.remaining() >= 2) {
                    out.putShort(samples[position++]);
                }
            }
        } catch (Exception e) {
            throw new IOException("mp3 解码失败", e);
        }
        out.flip();
        return out;
    }

    private boolean decodeNextFrame() throws Exception {
        while (!ended) {
            Header header = bitstream.readFrame();
            if (header == null) {
                ended = true;
                return false;
            }
            SampleBuffer decoded = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            bitstream.closeFrame();
            if (decoded != null && decoded.getBufferLength() > 0) {
                buffer = decoded;
                position = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        try {
            bitstream.close();
        } catch (Exception ignored) {
            // 关不掉也无所谓
        }
        input.close();
    }
}
