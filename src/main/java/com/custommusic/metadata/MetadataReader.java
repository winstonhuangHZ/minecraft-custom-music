package com.custommusic.metadata;

import com.custommusic.CustomMusicClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 读取音频文件的标题/艺术家/内嵌封面。
 *
 * <p>自己实现而不是拉第三方库：
 * MP3 走 ID3v2（2.2/2.3/2.4 的 APIC/PIC 帧），FLAC 走 PICTURE 元数据块。
 * 只为读一张图，不值得引依赖。
 */
public final class MetadataReader {

    private MetadataReader() {
    }

    public static TrackMetadata read(Path file) {
        try {
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".mp3")) {
                return readId3(file);
            }
            if (name.endsWith(".flac")) {
                return readFlac(file);
            }
        } catch (Exception e) {
            CustomMusicClient.LOG.debug("读取元数据失败: {}", file, e);
        }
        return TrackMetadata.EMPTY;
    }

    // ------------------------------------------------------------------ MP3

    private static TrackMetadata readId3(Path file) throws IOException {
        byte[] head = readRange(file, 0, 10);
        if (head.length < 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') {
            return TrackMetadata.EMPTY;
        }

        int major = head[3] & 0xFF;
        int flags = head[5] & 0xFF;
        int size = syncSafe(head, 6);
        if (size <= 0) {
            return TrackMetadata.EMPTY;
        }

        byte[] tag = readRange(file, 10, size);
        // 只有 ID3v2.4 的帧长度是 syncsafe 编码
        boolean v4 = major >= 4;
        boolean v22 = major <= 2;
        // 扩展头先跳过
        int pos = 0;
        if ((flags & 0x40) != 0 && !v22 && tag.length > 4) {
            pos = 4 + (v4 ? syncSafe(tag, 0) : beInt(tag, 0));
        }

        String title = null;
        String artist = null;
        String album = null;
        byte[] cover = null;

        int idLength = v22 ? 3 : 4;
        while (pos + idLength + (v22 ? 3 : 6) <= tag.length) {
            String id = new String(tag, pos, idLength, StandardCharsets.ISO_8859_1);
            if (id.charAt(0) == 0) {
                break;
            }
            int frameSize = v22
                    ? be24(tag, pos + 3)
                    : (v4 ? syncSafe(tag, pos + 4) : beInt(tag, pos + 4));
            int dataStart = pos + idLength + (v22 ? 3 : 6);
            if (frameSize <= 0 || dataStart + frameSize > tag.length) {
                break;
            }

            switch (id) {
                case "TIT2", "TT2" -> title = decodeText(tag, dataStart, frameSize);
                case "TPE1", "TP1" -> artist = decodeText(tag, dataStart, frameSize);
                case "TALB", "TAL" -> album = decodeText(tag, dataStart, frameSize);
                case "APIC" -> cover = extractApic(tag, dataStart, frameSize);
                case "PIC" -> cover = extractPic(tag, dataStart, frameSize);
                default -> {
                }
            }
            pos = dataStart + frameSize;
        }

        return new TrackMetadata(blankToNull(title), blankToNull(artist), blankToNull(album), cover);
    }

    /** APIC: 编码(1) mime(0结尾) 图片类型(1) 描述(0结尾) 图片数据 */
    private static byte[] extractApic(byte[] tag, int start, int length) {
        int end = start + length;
        int pos = start;
        int encoding = tag[pos++] & 0xFF;

        int mimeEnd = pos;
        while (mimeEnd < end && tag[mimeEnd] != 0) {
            mimeEnd++;
        }
        String mime = new String(tag, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
        pos = mimeEnd + 1;
        pos++; // 图片类型
        pos = skipDescription(tag, pos, end, encoding);

        if (pos >= end || (!mime.startsWith("image/") && mime.length() > 0 && !mime.equals("JPG") && !mime.equals("PNG"))) {
            return null;
        }
        return Arrays.copyOfRange(tag, pos, end);
    }

    /** ID3v2.2 的 PIC: 编码(1) 图片格式(3) 图片类型(1) 描述(0结尾) 图片数据 */
    private static byte[] extractPic(byte[] tag, int start, int length) {
        int end = start + length;
        int pos = start;
        int encoding = tag[pos++] & 0xFF;
        pos += 3; // "JPG" / "PNG"
        pos++;    // 图片类型
        pos = skipDescription(tag, pos, end, encoding);
        if (pos >= end) {
            return null;
        }
        return Arrays.copyOfRange(tag, pos, end);
    }

    private static int skipDescription(byte[] tag, int pos, int end, int encoding) {
        if (encoding == 1 || encoding == 2) {
            while (pos + 1 < end && !(tag[pos] == 0 && tag[pos + 1] == 0)) {
                pos += 2;
            }
            return pos + 2;
        }
        while (pos < end && tag[pos] != 0) {
            pos++;
        }
        return pos + 1;
    }

    private static String decodeText(byte[] tag, int start, int length) {
        if (length <= 1) {
            return null;
        }
        int encoding = tag[start] & 0xFF;
        int from = start + 1;
        int to = start + length;
        Charset charset = switch (encoding) {
            case 0 -> StandardCharsets.ISO_8859_1;
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            default -> StandardCharsets.UTF_8;
        };
        String text = new String(tag, from, to - from, charset);
        return text.replace("\u0000", "").trim();
    }

    // ----------------------------------------------------------------- FLAC

    private static TrackMetadata readFlac(Path file) throws IOException {
        byte[] head = readRange(file, 0, 4);
        if (head.length < 4 || head[0] != 'f' || head[1] != 'L' || head[2] != 'a' || head[3] != 'C') {
            return TrackMetadata.EMPTY;
        }

        String title = null;
        String artist = null;
        String album = null;
        byte[] cover = null;

        long offset = 4;
        while (true) {
            byte[] blockHead = readRange(file, offset, 4);
            if (blockHead.length < 4) {
                break;
            }
            boolean last = (blockHead[0] & 0x80) != 0;
            int type = blockHead[0] & 0x7F;
            int length = ((blockHead[1] & 0xFF) << 16) | ((blockHead[2] & 0xFF) << 8) | (blockHead[3] & 0xFF);
            byte[] block = readRange(file, offset + 4, length);

            if (type == 6 && block.length > 32) {
                int mimeLength = beInt(block, 4);
                int pos = 8 + mimeLength;
                if (pos + 4 <= block.length) {
                    int descLength = beInt(block, pos);
                    pos += 4 + descLength + 16;
                    if (pos + 4 <= block.length) {
                        int dataLength = beInt(block, pos);
                        pos += 4;
                        if (dataLength > 0 && pos + dataLength <= block.length) {
                            cover = Arrays.copyOfRange(block, pos, pos + dataLength);
                        }
                    }
                }
            } else if (type == 4) {
                for (String comment : parseVorbisComments(block)) {
                    int eq = comment.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = comment.substring(0, eq).trim().toUpperCase();
                    String value = comment.substring(eq + 1).trim();
                    switch (key) {
                        case "TITLE" -> title = value;
                        case "ARTIST" -> artist = value;
                        case "ALBUM" -> album = value;
                        default -> {
                        }
                    }
                }
            }

            offset += 4L + length;
            if (last) {
                break;
            }
        }
        return new TrackMetadata(title, artist, album, cover);
    }

    /** Vorbis comment 块：4 字节小端厂商串长度 + 厂商串 + 4 字节数量 + 每条（4 字节长度 + 串）。 */
    private static java.util.List<String> parseVorbisComments(byte[] block) {
        java.util.List<String> comments = new java.util.ArrayList<>();
        int pos = 0;
        if (block.length < 8) {
            return comments;
        }
        int vendorLength = leInt(block, pos);
        pos += 4 + vendorLength;
        if (pos + 4 > block.length) {
            return comments;
        }
        int count = leInt(block, pos);
        pos += 4;
        for (int i = 0; i < count && pos + 4 <= block.length; i++) {
            int length = leInt(block, pos);
            pos += 4;
            if (length < 0 || pos + length > block.length) {
                break;
            }
            comments.add(new String(block, pos, length, StandardCharsets.UTF_8));
            pos += length;
        }
        return comments;
    }

    // ------------------------------------------------------------------ 工具

    private static byte[] readRange(Path file, long offset, int length) throws IOException {
        if (length <= 0) {
            return new byte[0];
        }
        try (InputStream in = Files.newInputStream(file)) {
            long skipped = in.skip(offset);
            while (skipped < offset) {
                long more = in.skip(offset - skipped);
                if (more <= 0) {
                    return new byte[0];
                }
                skipped += more;
            }
            byte[] data = new byte[length];
            int read = in.readNBytes(data, 0, length);
            return read == length ? data : Arrays.copyOf(data, Math.max(0, read));
        }
    }

    private static int syncSafe(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return ((data[offset] & 0x7F) << 21) | ((data[offset + 1] & 0x7F) << 14)
                | ((data[offset + 2] & 0x7F) << 7) | (data[offset + 3] & 0x7F);
    }

    private static int beInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static int be24(byte[] data, int offset) {
        if (offset + 3 > data.length) {
            return 0;
        }
        return ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
    }

    private static int leInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
