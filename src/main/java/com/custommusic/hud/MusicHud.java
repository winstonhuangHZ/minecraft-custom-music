package com.custommusic.hud;

import com.custommusic.CustomMusicClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 右上角的「正在播放」小标签，可以在界面里关掉。
 * 由 HudMixin 注入原版 HUD 渲染的末尾调用，不依赖 Fabric API。
 */
public final class MusicHud {

    private static final int MARGIN = 6;

    private MusicHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, Hud hud) {
        if (CustomMusicClient.config() == null || !CustomMusicClient.config().showHud) {
            return;
        }
        if (hud.isHidden() || !NowPlaying.isPlaying()) {
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
