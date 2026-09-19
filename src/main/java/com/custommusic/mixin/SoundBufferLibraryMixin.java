package com.custommusic.mixin;

import com.custommusic.audio.Mp3Fallback;
import net.minecraft.util.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * 没有 ffmpeg 时，把我们那些占位 ogg 的音频流换成 MP3 直解。
 *
 * <p>{@code getStream} 是游戏创建流式音频的唯一入口，原版在这里 {@code supplyAsync} 到
 * 非关键 IO 线程去开 OGG 流；我们照着同样的语义返回自己的 MP3 流，
 * 只有确认是我们这个曲目时才接管，其它声音一切照旧。
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void custommusic$mp3Fallback(Identifier id, boolean streaming,
                                         CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        String source = Mp3Fallback.findSource(id);
        if (source == null) {
            return;
        }
        cir.setReturnValue(CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return Mp3Fallback.openStream(source);
                    } catch (Exception e) {
                        throw new RuntimeException("无法打开 mp3: " + source, e);
                    }
                },
                Util.nonCriticalIoPool()));
    }
}
