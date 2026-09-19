package com.custommusic.ui;

import com.custommusic.CustomMusicClient;
import com.custommusic.audio.AudioConverter;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import com.custommusic.music.PlaylistEngine;
import com.custommusic.pack.PackManager;
import com.custommusic.playback.SituationalEngine;
import com.custommusic.preview.PreviewPlayer;
import com.custommusic.sync.MusicSync;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.nio.file.Path;

/** 模组主界面，ModMenu 的设置按钮也指向这里。 */
public final class MusicScreen extends Screen {

    private final Screen parent;
    private TrackListWidget list;
    private Button modeButton;
    private MusicSync.State lastSyncState = MusicSync.State.IDLE;

    public MusicScreen(Screen parent) {
        super(Component.translatable("custommusic.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listTop = 46;
        int listHeight = Math.max(40, this.height - listTop - 60);
        this.list = new TrackListWidget(this, this.minecraft, this.width, listHeight, listTop, 24);
        this.list.reload();
        addRenderableWidget(this.list);

        int gap = 6;
        int topY = this.height - 52;
        int bottomY = this.height - 28;
        int third = Math.max(60, (this.width - 40 - gap * 2) / 3);
        int quarter = Math.max(48, (this.width - 40 - gap * 3) / 4);

        addRenderableWidget(Button.builder(Component.translatable("custommusic.button.sync"),
                        button -> MusicSync.start())
                .bounds(20, topY, quarter, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.button.reload"),
                        button -> PackManager.enableAndReload())
                .bounds(20 + quarter + gap, topY, quarter, 20).build());
        addRenderableWidget(Button.builder(playScreenLabel(),
                        button -> this.minecraft.setScreenAndShow(CustomMusicClient.config().isSituational()
                                ? new SituationalScreen(this)
                                : new PlaylistScreen(this)))
                .bounds(20 + (quarter + gap) * 2, topY, quarter, 20).build());

        // 切模式要重新生成资源包（播放器模式写覆盖事件，情境模式把覆盖撤掉），所以顺手重扫
        modeButton = addRenderableWidget(Button.builder(modeLabel(), button -> {
            CustomMusicConfig config = CustomMusicClient.config();
            config.mode = config.isSituational()
                    ? CustomMusicConfig.MODE_PLAYER
                    : CustomMusicConfig.MODE_SITUATIONAL;
            config.save();
            PlaylistEngine.reset();
            SituationalEngine.reset();
            button.setMessage(modeLabel());
            MusicSync.start();
        }).bounds(20 + (quarter + gap) * 3, topY, quarter, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("custommusic.button.enableAll"), button -> {
            CustomMusicClient.library().setAllEnabled(true);
            refresh();
        }).bounds(20, bottomY, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.button.disableAll"), button -> {
            CustomMusicClient.library().setAllEnabled(false);
            refresh();
        }).bounds(20 + third + gap, bottomY, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("custommusic.button.folder"),
                        button -> openMusicFolder())
                .bounds(20 + (third + gap) * 2, bottomY, third, 20).build());
    }

    /** 曲目列表变化后重建（上移、下移、扫描完成后调用）。 */
    public void refresh() {
        if (this.list != null) {
            this.list.reload();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(this.font, statusText(), this.width / 2, 28, 0xFFA0A0A0);

        if (!AudioConverter.available(CustomMusicClient.config().ffmpegPath)) {
            graphics.centeredText(this.font, Component.translatable("custommusic.hint.ffmpeg"),
                    this.width / 2, 38, 0xFFFF5555);
        } else if (CustomMusicClient.library().tracks().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("custommusic.hint.empty"),
                    this.width / 2, this.height / 2, 0xFFFFD080);
        }
    }

    private Component statusText() {
        if (MusicSync.isRunning()) {
            return Component.literal(MusicSync.message());
        }
        MusicLibrary library = CustomMusicClient.library();
        Component packState = Component.translatable(PackManager.isActive()
                ? "custommusic.state.active"
                : "custommusic.state.inactive");
        return Component.translatable("custommusic.status",
                library.enabledCount(), library.tracks().size(), packState);
    }

    private Component playScreenLabel() {
        return Component.translatable(CustomMusicClient.config().isSituational()
                ? "custommusic.button.situational"
                : "custommusic.button.playlists");
    }

    private Component modeLabel() {
        return Component.translatable(CustomMusicClient.config().isSituational()
                ? "custommusic.button.modeSituational"
                : "custommusic.button.modePlayer");
    }

    @Override
    public void tick() {
        super.tick();
        MusicSync.State state = MusicSync.state();
        if (state != lastSyncState) {
            lastSyncState = state;
            if (state == MusicSync.State.DONE || state == MusicSync.State.ERROR) {
                refresh();
            }
        }
    }

    private void openMusicFolder() {
        Path folder = CustomMusicClient.library().folder();
        Thread thread = new Thread(() -> {
            try {
                Desktop.getDesktop().open(folder.toFile());
            } catch (Exception e) {
                CustomMusicClient.LOG.warn("打不开文件夹 {}：{}", folder, e.toString());
            }
        }, "CustomMusic-OpenFolder");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void onClose() {
        PreviewPlayer.stop();
        this.minecraft.setScreenAndShow(this.parent);
    }
}
