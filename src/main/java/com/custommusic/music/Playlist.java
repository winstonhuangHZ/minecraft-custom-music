package com.custommusic.music;

import com.custommusic.config.CustomMusicConfig;

import java.util.List;

/**
 * 一张歌单，对应音乐文件夹里的一个子文件夹。
 * id 是文件夹相对路径；顶层散落的文件归到 id 为空的歌单。
 */
public record Playlist(String id, String name, List<CustomMusicConfig.TrackEntry> tracks) {

    public static final String ROOT_ID = "";

    public boolean isRoot() {
        return id.isEmpty();
    }

    public int size() {
        return tracks.size();
    }
}
