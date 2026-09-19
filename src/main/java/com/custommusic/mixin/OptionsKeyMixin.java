package com.custommusic.mixin;

import com.custommusic.CustomMusicClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * 把模组的快捷键插进原版的 keyMappings 数组，替代 Fabric API 的 KeyMappingHelper。
 *
 * <p>注入在 {@code Options.load()} 的开头，这样加载 options.txt 里保存的按键时就已经包含它了
 * （玩家改过的键位才能被读回来）。Fabric API 没装、装了都无所谓，两边互不干扰。
 */
@Mixin(Options.class)
public abstract class OptionsKeyMixin {

    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "load", at = @At("HEAD"))
    private void custommusic$addOpenKey(CallbackInfo ci) {
        if (keyMappings == null) {
            return;
        }
        for (KeyMapping existing : keyMappings) {
            if (existing != null && "key.custommusic.open".equals(existing.getName())) {
                CustomMusicClient.setOpenKey(existing);
                return;
            }
        }

        KeyMapping mapping = CustomMusicClient.createOpenKey();
        KeyMapping[] extended = Arrays.copyOf(keyMappings, keyMappings.length + 1);
        extended[keyMappings.length] = mapping;
        this.keyMappings = extended;
        CustomMusicClient.setOpenKey(mapping);
    }
}
