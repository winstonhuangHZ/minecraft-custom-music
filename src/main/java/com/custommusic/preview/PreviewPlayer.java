package com.custommusic.preview;

import com.custommusic.CustomMusicClient;
import com.custommusic.hud.NowPlaying;
import com.custommusic.music.MusicLibrary;
import com.custommusic.pack.PackGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** 界面里的试听：直接播资源包里那首曲子。 */
public final class PreviewPlayer {

    private static SoundInstance current;
    private static String currentId;

    private PreviewPlayer() {
    }

    public static boolean isPlaying(String trackFile) {
        return current != null && MusicLibrary.trackId(trackFile).equals(currentId);
    }

    /** 播放或停止。 */
    public static void toggle(String trackFile) {
        String id = MusicLibrary.trackId(trackFile);
        if (isPlaying(trackFile)) {
            stop();
            return;
        }
        stop();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        Identifier eventId = Identifier.fromNamespaceAndPath(
                PackGenerator.PREVIEW_NAMESPACE, PackGenerator.TRACK_PREFIX + id);
        SoundEvent event = SoundEvent.createVariableRangeEvent(eventId);
        SoundInstance instance = SimpleSoundInstance.forMusic(event);
        SoundEngine.PlayResult result = minecraft.getSoundManager().play(instance);
        CustomMusicClient.LOG.info("试听 {} -> {}", id, result);
        if (result == SoundEngine.PlayResult.NOT_STARTED) {
            return;
        }

        current = instance;
        currentId = id;
        NowPlaying.onTrackStarted(trackFile);
    }

    public static void stop() {
        Minecraft minecraft = Minecraft.getInstance();
        if (current != null && minecraft != null) {
            minecraft.getSoundManager().stop(current);
        }
        current = null;
        currentId = null;
        NowPlaying.onStopped();
    }

    /** 只停声音，不动 HUD 状态（歌单队列接管时用，免得两首一起响）。 */
    public static void stopQuietly() {
        Minecraft minecraft = Minecraft.getInstance();
        if (current != null && minecraft != null) {
            minecraft.getSoundManager().stop(current);
        }
        current = null;
        currentId = null;
    }

    public static boolean isActive() {
        return current != null;
    }
}
