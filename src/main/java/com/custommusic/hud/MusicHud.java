package com.custommusic.hud;

import com.custommusic.CustomMusicClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** 右上角的「正在播放」小标签，可以在界面里关掉。 */
public final class MusicHud implements HudElement {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(CustomMusicClient.MOD_ID, "now_playing");
    private static final int MARGIN = 6;

    private MusicHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(ID, new MusicHud());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (CustomMusicClient.config() == null || !CustomMusicClient.config().showHud) {
            return;
        }
        if (!NowPlaying.isPlaying()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }

        int x = graphics.guiWidth() - NowPlayingCard.WIDTH - MARGIN;
        int y = MARGIN;
        NowPlayingCard.render(graphics, minecraft.font, Math.max(0, x), y);
    }
}
