package com.custommusic.mixin;

import com.custommusic.music.PlaylistEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让歌单接管背景音乐。
 *
 * <p>原版每次要开一首新歌都会走 {@code startPlaying(Music)}，这里抢先一步改成放歌单里的曲子；
 * 另外原版在一首歌放完之后会等「情境音乐的最大间隔」才开下一首（可能十几分钟），
 * 所以在 tick 末尾把间隔压成配置的秒数，这样一整个歌单是连着放的。
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Shadow
    private SoundInstance currentMusic;

    @Shadow
    private int nextSongDelay;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void custommusic$playFromPlaylist(Music music, CallbackInfo ci) {
        if (!PlaylistEngine.active()) {
            return;
        }
        PlaylistEngine.Pick pick = PlaylistEngine.nextPick();
        if (pick == null) {
            return;
        }

        SoundInstance instance = SimpleSoundInstance.forMusic(pick.event());
        this.currentMusic = instance;
        SoundEngine.PlayResult result = this.minecraft.getSoundManager().play(instance);

        if (result == SoundEngine.PlayResult.NOT_STARTED) {
            // 多半是资源包正在重载，放回队列，一秒后重试
            this.currentMusic = null;
            if (PlaylistEngine.requeue(pick.file())) {
                this.nextSongDelay = PlaylistEngine.retryDelayTicks();
            }
            ci.cancel();
            return;
        }

        com.custommusic.CustomMusicClient.LOG.info("歌单播放 {} -> {}",
                pick.event().location(), result);
        PlaylistEngine.noteSuccess();
        PlaylistEngine.markStarted(instance);
        if (result == SoundEngine.PlayResult.STARTED) {
            this.minecraft.gui.toastManager().showNowPlayingToast();
        }
        // 和原版保持一致：间隔交给我们的 tick 钩子去压
        this.nextSongDelay = Integer.MAX_VALUE;
        ci.cancel();
    }

    /**
     * 别让原版把我们在放的歌换掉。
     * 原版这个判断是给「换情境音乐」用的（比如进下界换音乐），
     * 但它比较的是事件 id，我们的自定义事件必然不匹配，所以要放行我们自己的。
     */
    @Inject(method = "canReplace", at = @At("HEAD"), cancellable = true)
    private static void custommusic$keepOurMusic(Music music, SoundInstance instance,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (PlaylistEngine.isOurInstance(instance)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void custommusic$shortenGap(CallbackInfo ci) {
        if (!PlaylistEngine.active() || this.currentMusic != null) {
            return;
        }
        this.nextSongDelay = Math.min(this.nextSongDelay, PlaylistEngine.gapTicks());
    }
}
