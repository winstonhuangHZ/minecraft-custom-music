package com.custommusic.ui;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import com.custommusic.music.Playlist;
import com.custommusic.pack.MusicEvents;
import com.custommusic.playback.SituationalEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * 情境模式界面：左边选一个情境（比如「下界 · 下界荒地」），再挑歌单或单曲分配给它们。
 * 点一行就能加入/取消分配，改完立刻生效，不用重新生成资源包。
 */
public final class SituationalScreen extends Screen {

    private enum View {
        CONTEXTS, ASSIGN_PLAYLISTS, ASSIGN_TRACKS
    }

    private final Screen parent;
    private View view = View.CONTEXTS;
    private String target;
    private Map<String, SortedSet<String>> discovered = new TreeMap<>();
    private SituationalListWidget list;
    private int refreshTimer;

    public SituationalScreen(Screen parent) {
        super(Component.translatable("custommusic.situational.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int top = 58;
        int listHeight = Math.max(40, this.height - top - 70);
        this.list = new SituationalListWidget(this, this.minecraft, this.width, listHeight, top, 22);
        addRenderableWidget(this.list);
        if (discovered.isEmpty()) {
            discovered = MusicEvents.discover();
        }
        refresh();

        int gap = 6;
        int rowY = this.height - 48;
        int row2Y = this.height - 24;
        int third = Math.max(70, (this.width - 40 - gap * 2) / 3);

        if (view == View.CONTEXTS) {
            addRenderableWidget(Button.builder(Component.translatable("custommusic.situational.rescan"),
                            button -> {
                                discovered = MusicEvents.discover();
                                refresh();
                            })
                    .bounds(20, rowY, third, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("custommusic.situational.clearAll"),
                            button -> {
                                CustomMusicClient.config().assignments.clear();
                                CustomMusicClient.config().save();
                                SituationalEngine.reset();
                                refresh();
                            })
                    .bounds(20 + third + gap, rowY, third, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("custommusic.situational.back"),
                            button -> {
                                view = View.CONTEXTS;
                                target = null;
                                rebuildWidgets();
                            })
                    .bounds(20, rowY, third, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("custommusic.situational.clearOne"), button -> {
                if (target != null) {
                    CustomMusicClient.config().assignments.remove(target);
                    CustomMusicClient.config().save();
                    SituationalEngine.reset();
                }
                refresh();
            }).bounds(20 + third + gap, rowY, third, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(view == View.ASSIGN_PLAYLISTS
                                    ? "custommusic.situational.toTracks"
                                    : "custommusic.situational.toPlaylists"),
                            button -> {
                                view = view == View.ASSIGN_PLAYLISTS ? View.ASSIGN_TRACKS : View.ASSIGN_PLAYLISTS;
                                rebuildWidgets();
                                refresh();
                            })
                    .bounds(20 + (third + gap) * 2, rowY, third, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable(CustomMusicClient.config().showHud
                                ? "custommusic.playlist.hudOn"
                                : "custommusic.playlist.hudOff"),
                        button -> {
                            CustomMusicConfig config = CustomMusicClient.config();
                            config.showHud = !config.showHud;
                            config.save();
                            button.setMessage(Component.translatable(config.showHud
                                    ? "custommusic.playlist.hudOn"
                                    : "custommusic.playlist.hudOff"));
                        })
                .bounds(20, row2Y, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.situational.done"),
                        button -> onClose())
                .bounds(20 + third + gap, row2Y, third * 2 + gap, 20).build());
    }

    /** 按当前视图重建列表内容。 */
    public void refresh() {
        if (this.list == null) {
            return;
        }
        List<SituationalListWidget.Item> items = new ArrayList<>();
        CustomMusicConfig config = CustomMusicClient.config();

        if (view == View.CONTEXTS) {
            for (String id : contextIds()) {
                List<String> assigned = config.assignments.get(id);
                items.add(new SituationalListWidget.Item(SituationalListWidget.Kind.CONTEXT, id,
                        MusicEvents.displayName(id), summary(assigned),
                        assigned != null && !assigned.isEmpty()));
            }
        } else if (view == View.ASSIGN_PLAYLISTS) {
            List<String> assigned = assignedOf(target);
            for (Playlist playlist : CustomMusicClient.library().playlists()) {
                items.add(new SituationalListWidget.Item(SituationalListWidget.Kind.PLAYLIST, playlist.id(),
                        playlist.name(), playlist.size() + " 首", assigned.contains(playlist.id())));
            }
        } else {
            List<String> assigned = assignedOf(target);
            for (CustomMusicConfig.TrackEntry entry : CustomMusicClient.library().enabledTracks()) {
                items.add(new SituationalListWidget.Item(SituationalListWidget.Kind.TRACK, entry.file,
                        MusicLibrary.displayName(entry.file), "", assigned.contains(entry.file)));
            }
        }
        this.list.reload(items);
    }

    private List<String> contextIds() {
        List<String> ids = new ArrayList<>();
        discovered.forEach((namespace, keys) -> keys.forEach(key -> ids.add(namespace + ":" + key)));
        ids.sort(Comparator.comparing(MusicEvents::displayName));
        return ids;
    }

    private List<String> assignedOf(String context) {
        if (context == null) {
            return List.of();
        }
        List<String> assigned = CustomMusicClient.config().assignments.get(context);
        return assigned == null ? List.of() : assigned;
    }

    private String summary(List<String> assigned) {
        if (assigned == null || assigned.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < assigned.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            String selector = assigned.get(i);
            Playlist playlist = MusicLibrary.findPlaylist(CustomMusicClient.library().playlists(), selector);
            sb.append(playlist != null ? playlist.name() : MusicLibrary.displayName(selector));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ 交互

    public void onRowClick(SituationalListWidget.Item item) {
        if (item.kind() == SituationalListWidget.Kind.CONTEXT) {
            target = item.id();
            view = View.ASSIGN_PLAYLISTS;
            rebuildWidgets();
            refresh();
            return;
        }
        toggle(item.id());
    }

    public void onRowBox(SituationalListWidget.Item item) {
        if (item.kind() == SituationalListWidget.Kind.CONTEXT) {
            CustomMusicClient.config().assignments.remove(item.id());
            CustomMusicClient.config().save();
            SituationalEngine.reset();
            refresh();
            return;
        }
        toggle(item.id());
    }

    private void toggle(String selector) {
        if (target == null) {
            return;
        }
        CustomMusicConfig config = CustomMusicClient.config();
        List<String> current = config.assignments.computeIfAbsent(target, key -> new ArrayList<>());
        if (!current.remove(selector)) {
            current.add(selector);
        }
        if (current.isEmpty()) {
            config.assignments.remove(target);
        }
        config.save();
        SituationalEngine.reset();
        refresh();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(this.font, headerText(), this.width / 2, 24, 0xFFA0A0A0);
        graphics.centeredText(this.font, hintText(), this.width / 2, 38, 0xFF80C0FF);

        if (view == View.CONTEXTS && discovered.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("custommusic.situational.empty"),
                    this.width / 2, this.height / 2, 0xFFFFD080);
        }
    }

    private Component headerText() {
        int configured = CustomMusicClient.config().assignments.size();
        int total = contextIds().size();
        return Component.translatable("custommusic.situational.status", configured, total);
    }

    private Component hintText() {
        return target == null
                ? Component.translatable("custommusic.situational.pickContext")
                : Component.translatable("custommusic.situational.assigning", MusicEvents.displayName(target));
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTimer >= 60) {
            refreshTimer = 0;
            if (view == View.CONTEXTS) {
                refresh();
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }
}
