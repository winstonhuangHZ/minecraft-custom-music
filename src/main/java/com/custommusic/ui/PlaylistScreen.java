package com.custommusic.ui;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import com.custommusic.music.Playlist;
import com.custommusic.music.PlaylistEngine;
import com.custommusic.hud.NowPlaying;
import com.custommusic.hud.NowPlayingCard;
import com.custommusic.sync.MusicSync;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 歌单界面：选一张专辑立刻播放，或者排进队列（放完一张自动换下一张）。 */
public final class PlaylistScreen extends Screen {

    private final Screen parent;
    private PlaylistListWidget list;
    private Button shuffleButton;
    private Button hudButton;
    private Button allMusicButton;
    private List<Playlist> playlists = List.of();
    private int selected = -1;
    private int refreshTimer;

    public PlaylistScreen(Screen parent) {
        super(Component.translatable("custommusic.playlist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int top = 58;
        int listHeight = Math.max(40, this.height - top - 92);
        this.list = new PlaylistListWidget(this, this.minecraft, this.width, listHeight, top, 24);
        addRenderableWidget(this.list);
        refresh();

        int gap = 6;
        int rowY = this.height - 72;
        int row2Y = this.height - 48;
        int row3Y = this.height - 24;
        int third = Math.max(70, (this.width - 40 - gap * 2) / 3);
        int half = Math.max(70, (this.width - 40 - gap) / 2);

        addRenderableWidget(Button.builder(Component.translatable("custommusic.playlist.play"),
                        button -> playSelected())
                .bounds(20, rowY, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.playlist.enqueue"),
                        button -> enqueueSelected())
                .bounds(20 + third + gap, rowY, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.playlist.skip"),
                        button -> PlaylistEngine.skipPlaylist())
                .bounds(20 + (third + gap) * 2, rowY, third, 20).build());

        shuffleButton = addRenderableWidget(Button.builder(shuffleLabel(), button -> {
            CustomMusicConfig config = CustomMusicClient.config();
            config.shuffleTracks = !config.shuffleTracks;
            config.save();
            PlaylistEngine.reset();
            button.setMessage(shuffleLabel());
        }).bounds(20, row2Y, third, 20).build());

        allMusicButton = addRenderableWidget(Button.builder(allMusicLabel(), button -> {
            CustomMusicConfig config = CustomMusicClient.config();
            config.overrideAllMusic = !config.overrideAllMusic;
            config.save();
            button.setMessage(allMusicLabel());
            MusicSync.start();
        }).bounds(20 + third + gap, row2Y, third, 20).build());

        hudButton = addRenderableWidget(Button.builder(hudLabel(), button -> {
            CustomMusicConfig config = CustomMusicClient.config();
            config.showHud = !config.showHud;
            config.save();
            button.setMessage(hudLabel());
        }).bounds(20 + (third + gap) * 2, row2Y, third, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("custommusic.playlist.clear"),
                        button -> PlaylistEngine.clear())
                .bounds(20, row3Y, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.button.stop"),
                        button -> PlaylistEngine.stopPlayback())
                .bounds(20 + third + gap, row3Y, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.playlist.back"),
                        button -> onClose())
                .bounds(20 + (third + gap) * 2, row3Y, third, 20).build());

    }

    private Component shuffleLabel() {
        return Component.translatable(CustomMusicClient.config().shuffleTracks
                ? "custommusic.playlist.shuffle"
                : "custommusic.playlist.sequence");
    }

    private Component hudLabel() {
        return Component.translatable(CustomMusicClient.config().showHud
                ? "custommusic.playlist.hudOn"
                : "custommusic.playlist.hudOff");
    }

    private Component allMusicLabel() {
        return Component.translatable(CustomMusicClient.config().overrideAllMusic
                ? "custommusic.button.allMusicOn"
                : "custommusic.button.allMusicOff");
    }

    /** 重新读一次歌单（扫描完成后或切回本界面时）。 */
    public void refresh() {
        this.playlists = CustomMusicClient.library().playlists();
        if (selected >= playlists.size()) {
            selected = playlists.isEmpty() ? -1 : playlists.size() - 1;
        }
        // 默认选中第一张，这样「立即播放选中的」不会因为没选而毫无反应
        if (selected < 0 && !playlists.isEmpty()) {
            selected = 0;
        }
        if (this.list != null) {
            this.list.reload(playlists, selected);
        }
    }

    public void select(int index) {
        this.selected = index;
        refresh();
    }

    public void playPlaylist(Playlist playlist) {
        PlaylistEngine.playNow(playlist.id());
    }

    public void enqueuePlaylist(Playlist playlist) {
        PlaylistEngine.enqueue(playlist.id());
    }

    private void playSelected() {
        Playlist target = selectedOrFirst();
        if (target != null) {
            playPlaylist(target);
        }
    }

    private void enqueueSelected() {
        Playlist target = selectedOrFirst();
        if (target != null) {
            enqueuePlaylist(target);
        }
    }

    /** 选中的那张；一个都没选就用第一张，避免按钮点了没反应。 */
    private Playlist selectedOrFirst() {
        if (playlists.isEmpty()) {
            return null;
        }
        if (selected >= 0 && selected < playlists.size()) {
            return playlists.get(selected);
        }
        return playlists.get(0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(this.font, nowPlayingText(), this.width / 2, 28, 0xFFA0A0A0);
        graphics.centeredText(this.font, queueText(), this.width / 2, 40, 0xFF80C0FF);

        if (playlists.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("custommusic.playlist.empty"),
                    this.width / 2, this.height / 2, 0xFFFFD080);
        }

        // 右上角也画一份「正在播放」，方便调界面时直接看到效果
        if (NowPlaying.isPlaying()) {
            NowPlayingCard.render(graphics, this.font,
                    Math.max(0, graphics.guiWidth() - NowPlayingCard.WIDTH - 6), 6);
        }
    }

    private Component nowPlayingText() {
        String track = PlaylistEngine.currentTrack();
        if (track == null) {
            return Component.translatable("custommusic.playlist.idle");
        }
        return Component.translatable("custommusic.playlist.nowPlaying",
                MusicLibrary.displayName(track), PlaylistEngine.playedInPlaylist());
    }

    private Component queueText() {
        List<String> queue = CustomMusicClient.config().queue;
        if (queue.isEmpty()) {
            return Component.translatable("custommusic.playlist.queueEmpty");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < queue.size() && i < 6; i++) {
            Playlist playlist = MusicLibrary.findPlaylist(playlists, queue.get(i));
            if (sb.length() > 0) {
                sb.append("  →  ");
            }
            if (i == CustomMusicClient.config().queueIndex) {
                sb.append("▶ ");
            }
            sb.append(playlist == null ? queue.get(i) : playlist.name());
        }
        if (queue.size() > 6) {
            sb.append("  …");
        }
        return Component.literal(sb.toString());
    }

    @Override
    public void tick() {
        super.tick();
        // 扫描可能改了歌单，隔两秒刷一次列表
        if (++refreshTimer >= 40) {
            refreshTimer = 0;
            refresh();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }
}
