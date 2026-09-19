package com.custommusic.ui;

import com.custommusic.music.Playlist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 歌单列表：一行一张专辑，右侧 [▶] 立即播放、[+] 加入队列。 */
public final class PlaylistListWidget extends ObjectSelectionList<PlaylistListWidget.Row> {

    private final PlaylistScreen screen;

    public PlaylistListWidget(PlaylistScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
    }

    public void reload(java.util.List<Playlist> playlists, int selected) {
        clearEntries();
        for (int i = 0; i < playlists.size(); i++) {
            addEntry(new Row(playlists.get(i), i, i == selected));
        }
    }

    @Override
    public int getRowWidth() {
        return Math.max(120, this.width - 40);
    }

    public final class Row extends ObjectSelectionList.Entry<Row> {

        private static final int BOX = 20;
        private static final int GAP = 2;

        private final Playlist playlist;
        private final int index;
        private final boolean selected;

        Row(Playlist playlist, int index, boolean selected) {
            this.playlist = playlist;
            this.index = index;
            this.selected = selected;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int width = getContentWidth();
            int height = getContentHeight();

            int background = selected ? 0x8000A0FF : (hovered ? 0x50FFFFFF : 0x28000000);
            graphics.fill(x, y, x + width, y + height, background);

            graphics.text(Minecraft.getInstance().font,
                    playlist.name() + "  (" + playlist.size() + ")",
                    x + 6, y + (height - 8) / 2, 0xFFFFFFFF);

            drawBox(graphics, box(x, y, width, height, 1), "▶", mouseX, mouseY);
            drawBox(graphics, box(x, y, width, height, 0), "+", mouseX, mouseY);
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

            if (inside(box(x, y, width, height, 1), mouseX, mouseY)) {
                screen.playPlaylist(playlist);
                return true;
            }
            if (inside(box(x, y, width, height, 0), mouseX, mouseY)) {
                screen.enqueuePlaylist(playlist);
                return true;
            }
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                screen.select(index);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(playlist.name());
        }

        private int[] box(int x, int y, int width, int height, int slot) {
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
