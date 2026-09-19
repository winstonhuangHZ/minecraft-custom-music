package com.custommusic;

import com.custommusic.config.CustomMusicConfig;
import com.custommusic.hud.MusicHud;
import com.custommusic.music.MusicLibrary;
import com.custommusic.pack.PackManager;
import com.custommusic.sync.MusicSync;
import com.custommusic.ui.MusicScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
    private static boolean started;

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

        LOG.info("CustomMusic 已加载，音乐文件夹: {}", library.folder());
        // 装了 ModMenu 就能从模组列表进设置，没装也能按快捷键进
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            LOG.info("检测到 ModMenu：模组列表里可以直接打开本模组的设置界面");
        } else {
            LOG.info("没有装 ModMenu：按 M 打开界面；装上 ModMenu 后也能从模组列表进入（可选）");
        }
    }

    /** 键位由 OptionsKeyMixin 在 Options.load() 时插进去（不依赖 Fabric API）。 */
    public static KeyMapping createOpenKey() {
        return new KeyMapping(
                "key.custommusic.open",
                GLFW.GLFW_KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main")));
    }

    public static void setOpenKey(KeyMapping mapping) {
        openScreenKey = mapping;
    }

    /** MinecraftTickMixin 每 tick 调一次：第一 tick 做启动，之后处理快捷键。 */
    public static void onClientTick(Minecraft minecraft) {
        if (!started) {
            started = true;
            // 已经启用过就别再重载一次：重载窗口内所有播放请求都会失败
            PackManager.ensureEnabled();
            if (config.autoSyncOnStart) {
                MusicSync.start();
            }
            // 开发用：加 -Dcustommusic.debugScreen 启动后直接打开界面
            if (System.getProperty("custommusic.debugScreen") != null) {
                minecraft.setScreenAndShow(new MusicScreen(null));
            }
        }

        if (openScreenKey != null) {
            while (openScreenKey.consumeClick()) {
                minecraft.setScreenAndShow(new MusicScreen(minecraft.gui.screen()));
            }
        }
    }
}
