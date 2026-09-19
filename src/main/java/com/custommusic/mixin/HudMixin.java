package com.custommusic.mixin;

import com.custommusic.hud.MusicHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原版 HUD 画完之后画右上角的「正在播放」小标签，替代 Fabric API 的 HudElementRegistry。 */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void custommusic$drawNowPlaying(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                            CallbackInfo ci) {
        MusicHud.render(graphics, (Hud) (Object) this);
    }
}
