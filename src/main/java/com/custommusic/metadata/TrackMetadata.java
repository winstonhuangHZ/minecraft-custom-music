package com.custommusic.metadata;

/** 从音频文件里读出来的信息；没有的字段就是 null。 */
public record TrackMetadata(String title, String artist, String album, byte[] cover) {

    public static final TrackMetadata EMPTY = new TrackMetadata(null, null, null, null);

    public boolean hasCover() {
        return cover != null && cover.length > 0;
    }
}
