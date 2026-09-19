package com.custommusic.ui;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import com.custommusic.preview.PreviewPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 曲目列表：一行一首，右侧有试听 / 上移 / 下移，点整行切换启用。 */
public final class TrackListWidget extends ObjectSelectionList<TrackListWidget.Row> {

    private final MusicScreen screen;

    public TrackListWidget(MusicScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
    }

    public void reload() {
        clearEntries();
        List<CustomMusicConfig.TrackEntry> tracks = CustomMusicClient.library().tracks();
        for (int i = 0; i < tracks.size(); i++) {
            addEntry(new Row(tracks.get(i), i));
        }
    }

    @Override
    public int getRowWidth() {
        return Math.max(120, this.width - 40);
    }

    // ------------------------------------------------------------------

    public final class Row extends ObjectSelectionList.Entry<Row> {

        private static final int BOX = 20;
        private static final int GAP = 2;

        private final CustomMusicConfig.TrackEntry track;
        private final int index;

        Row(CustomMusicConfig.TrackEntry track, int index) {
            this.track = track;
            this.index = index;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int width = getContentWidth();
            int height = getContentHeight();

            graphics.fill(x, y, x + width, y + height, hovered ? 0x50FFFFFF : 0x28000000);

            int textColor = track.enabled ? 0xFFFFFFFF : 0xFF808080;
            String marker = track.enabled ? "✔ " : "✘ ";
            graphics.text(Minecraft.getInstance().font,
                    marker + MusicLibrary.displayName(track.file),
                    x + 6, y + (height - 8) / 2, textColor);

            drawBox(graphics, previewBox(x, y, width, height),
                    PreviewPlayer.isPlaying(track.file) ? "■" : "▶", mouseX, mouseY);
            drawBox(graphics, moveBox(x, y, width, height, 0), "↑", mouseX, mouseY);
            drawBox(graphics, moveBox(x, y, width, height, 1), "↓", mouseX, mouseY);
        }

        private void drawBox(GuiGraphicsExtractor graphics, int[] box, String label, int mouseX, int mouseY) {
            boolean hover = inside(box, mouseX, mouseY);
            graphics.fill(box[0], box[1], box[2], box[3], hover ? 0xC0FFFFFF : 0x80000000);
            graphics.centeredText(Minecraft.getInstance().font, label,
                    (box[0] + box[2]) / 2, (box[1] + box[3]) / 2 - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            double mouseX = event.x();
            double mouseY = event.y();
            int x = getContentX();
            int y = getContentY();
            int width = getContentWidth();
            int height = getContentHeight();

            if (inside(previewBox(x, y, width, height), mouseX, mouseY)) {
                PreviewPlayer.toggle(track.file);
                return true;
            }
            int[] up = moveBox(x, y, width, height, 0);
            if (inside(up, mouseX, mouseY)) {
                PreviewPlayer.stop();
                CustomMusicClient.library().move(index, -1);
                screen.refresh();
                return true;
            }
            int[] down = moveBox(x, y, width, height, 1);
            if (inside(down, mouseX, mouseY)) {
                PreviewPlayer.stop();
                CustomMusicClient.library().move(index, 1);
                screen.refresh();
                return true;
            }
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                CustomMusicClient.library().setEnabled(index, !track.enabled);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(MusicLibrary.displayName(track.file));
        }

        private int[] previewBox(int x, int y, int width, int height) {
            int top = y + 2;
            int bottom = y + height - 2;
            return new int[]{x + width - BOX * 3 - GAP * 2 - 4, top, x + width - BOX * 2 - GAP * 2 - 4, bottom};
        }

        private int[] moveBox(int x, int y, int width, int height, int slot) {
            int top = y + 2;
            int bottom = y + height - 2;
            int right = x + width - 4 - slot * (BOX + GAP);
            return new int[]{right - BOX, top, right, bottom};
        }

        private static boolean inside(int[] box, double mouseX, double mouseY) {
            return mouseX >= box[0] && mouseX <= box[2] && mouseY >= box[1] && mouseY <= box[3];
        }
    }
}
