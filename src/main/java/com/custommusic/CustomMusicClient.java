package com.custommusic;

import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import com.custommusic.pack.PackManager;
import com.custommusic.sync.MusicSync;
import com.custommusic.ui.MusicScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomMusicClient implements ClientModInitializer {

    public static final String MOD_ID = "custommusic";
    public static final Logger LOG = LoggerFactory.getLogger("CustomMusic");

    private static CustomMusicConfig config;
    private static MusicLibrary library;
    private static KeyMapping openScreenKey;

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    }

    public static CustomMusicConfig config() {
        return config;
    }

    public static MusicLibrary library() {
        return library;
    }

    @Override
    public void onInitializeClient() {
        config = CustomMusicConfig.load();
        library = new MusicLibrary(config);

        try {
            Files.createDirectories(configDir());
            Files.createDirectories(library.folder());
        } catch (IOException e) {
            LOG.error("创建模组目录失败", e);
        }

        // 先按上次的记录快速重建一遍资源包（不转码），这样启动时就能被资源包仓库发现
        library.scan();

        openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.custommusic.open",
                GLFW.GLFW_KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (openScreenKey.consumeClick()) {
                minecraft.setScreenAndShow(new MusicScreen(minecraft.gui.screen()));
            }
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
            PackManager.enableAndReload();
            if (config.autoSyncOnStart) {
                MusicSync.start();
            }
            // 开发用：加 -Dcustommusic.debugScreen 启动后直接打开界面
            if (System.getProperty("custommusic.debugScreen") != null) {
                minecraft.setScreenAndShow(new MusicScreen(null));
            }
        });

        LOG.info("CustomMusic 已加载，音乐文件夹: {}", library.folder());
    }
}
