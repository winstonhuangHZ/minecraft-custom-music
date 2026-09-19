package com.custommusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 情境模式的列表：既能列情境，也能列歌单/曲目。 */
public final class SituationalListWidget extends ObjectSelectionList<SituationalListWidget.Row> {

    public enum Kind {
        PRESET, CONTEXT, PLAYLIST, TRACK
    }

    /** 一行数据。marked 表示「已分配/有配置」。 */
    public record Item(Kind kind, String id, String label, String detail, boolean marked) {
    }

    private final SituationalScreen screen;

    public SituationalListWidget(SituationalScreen screen, Minecraft minecraft,
                                 int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
    }

    public void reload(List<Item> items) {
        clearEntries();
        for (Item item : items) {
            addEntry(new Row(item));
        }
    }

    @Override
    public int getRowWidth() {
        return Math.max(120, this.width - 40);
    }

    public final class Row extends ObjectSelectionList.Entry<Row> {

        private static final int BOX = 20;

        private final Item item;

        Row(Item item) {
            this.item = item;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int width = getContentWidth();
            int height = getContentHeight();

            graphics.fill(x, y, x + width, y + height, hovered ? 0x50FFFFFF : 0x28000000);

            boolean isGroupRow = item.kind() == Kind.PRESET || item.kind() == Kind.CONTEXT;
            int labelColor = isGroupRow && !item.marked() ? 0xFFD0D0D0 : 0xFFFFFFFF;
            String label = item.kind() == Kind.PRESET ? "【组】" + item.label() : item.label();
            graphics.text(Minecraft.getInstance().font, label,
                    x + 6, y + (height - 8) / 2, labelColor);
            if (!item.detail().isEmpty()) {
                graphics.text(Minecraft.getInstance().font, item.detail(),
                        x + width - BOX - 12 - Minecraft.getInstance().font.width(item.detail()),
                        y + (height - 8) / 2, 0xFFA0A0A0);
            }

            boolean canPick = item.kind() == Kind.PLAYLIST || item.kind() == Kind.TRACK;
            String glyph = item.marked() ? (canPick ? "✓" : "✕") : "＋";
            int[] box = box(x, y, width, height);
            boolean hover = inside(box, mouseX, mouseY);
            graphics.fill(box[0], box[1], box[2], box[3], hover ? 0xC0FFFFFF : 0x80000000);
            graphics.centeredText(Minecraft.getInstance().font, glyph,
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

            if (inside(box(x, y, width, height), mouseX, mouseY)) {
                screen.onRowBox(item);
                return true;
            }
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                screen.onRowClick(item);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(item.label());
        }

        private int[] box(int x, int y, int width, int height) {
            int right = x + width - 4;
            return new int[]{right - BOX, y + 2, right, y + height - 2};
        }

        private static boolean inside(int[] box, double mouseX, double mouseY) {
            return mouseX >= box[0] && mouseX <= box[2] && mouseY >= box[1] && mouseY <= box[3];
        }
    }
}
