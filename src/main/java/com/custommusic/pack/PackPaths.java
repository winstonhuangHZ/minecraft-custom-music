package com.custommusic.pack;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** 生成的资源包放在 .minecraft/resourcepacks/CustomMusicPack/。 */
public final class PackPaths {

    public static final String FOLDER_NAME = "CustomMusicPack";

    private PackPaths() {
    }

    public static Path resourcePacksDir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    }

    public static Path packDir() {
        return resourcePacksDir().resolve(FOLDER_NAME);
    }

    public static Path packMeta() {
        return packDir().resolve("pack.mcmeta");
    }

    public static Path soundsJson() {
        return packDir().resolve("assets/minecraft/sounds.json");
    }

    /** 试听事件必须放在 custommusic 命名空间下。 */
    public static Path previewSoundsJson() {
        return packDir().resolve("assets/custommusic/sounds.json");
    }

    public static Path musicDir() {
        return packDir().resolve("assets/minecraft/sounds/music");
    }
}
