package com.custommusic.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** 「正在播放」小卡片：封面 + 歌名 + 艺术家 + 进度条。HUD 和界面里共用同一套画法。 */
public final class NowPlayingCard {

    public static final int WIDTH = 152;
    public static final int HEIGHT = 52;

    private static final int COVER_SIZE = 44;
    private static final int PADDING = 4;

    private NowPlayingCard() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xB4000000);
        graphics.fill(x, y, x + WIDTH, y + 1, 0xFF55FF55);

        Identifier cover = NowPlaying.coverTexture();
        int textX = x + PADDING + 2;
        if (cover != null) {
            graphics.blit(cover,
                    x + PADDING, y + PADDING,
                    x + PADDING + COVER_SIZE, y + PADDING + COVER_SIZE,
                    0f, 1f, 0f, 1f);
            textX = x + PADDING + COVER_SIZE + 6;
        }

        String title = NowPlaying.title();
        String artist = NowPlaying.artist();
        graphics.text(font, truncate(font, title == null ? "" : title, x + WIDTH - 6 - textX),
                textX, y + 7, 0xFFFFFFFF);
        if (artist != null && !artist.isBlank()) {
            graphics.text(font, truncate(font, artist, x + WIDTH - 6 - textX),
                    textX, y + 20, 0xFFB0B0B0);
        }

        int barLeft = textX;
        int barRight = x + WIDTH - 6;
        int barTop = y + HEIGHT - 14;
        graphics.fill(barLeft, barTop, barRight, barTop + 4, 0xFF303030);

        float progress = NowPlaying.progress();
        if (progress >= 0f) {
            int filled = barLeft + Math.round((barRight - barLeft) * progress);
            graphics.fill(barLeft, barTop, Math.max(barLeft, filled), barTop + 4, 0xFF55FF55);
        }

        String time = progress >= 0f
                ? format(NowPlaying.elapsedSeconds()) + " / " + format(NowPlaying.durationSeconds())
                : "";
        graphics.text(font, Component.literal(time), barLeft, barTop + 6, 0xFF808080);
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(sb.toString() + c + "…") > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + "…";
    }

    private static String format(float seconds) {
        int total = Math.max(0, (int) seconds);
        return total / 60 + ":" + String.format("%02d", total % 60);
    }
}
