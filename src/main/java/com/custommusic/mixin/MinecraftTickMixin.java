package com.custommusic.mixin;

import com.custommusic.CustomMusicClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端 tick 钩子，替代 Fabric API 的 END_CLIENT_TICK / CLIENT_STARTED：
 * 第一 tick 做启动（启用资源包、扫描转码），之后处理打开界面的快捷键。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void custommusic$onTick(CallbackInfo ci) {
        CustomMusicClient.onClientTick((Minecraft) (Object) this);
    }
}
