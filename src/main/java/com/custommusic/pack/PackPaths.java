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

    /** 每个命名空间一份覆盖用的 sounds.json（key 不能带命名空间，命名空间由目录决定）。 */
    public static Path soundsJson(String namespace) {
        return packDir().resolve("assets/" + namespace + "/sounds.json");
    }

    /** 试听事件必须放在 custommusic 命名空间下。 */
    public static Path previewSoundsJson() {
        return packDir().resolve("assets/custommusic/sounds.json");
    }

    /** 曲名翻译写在 minecraft 命名空间下（资源包加语言键的惯例位置）。 */
    public static Path langFile(String locale) {
        return packDir().resolve("assets/minecraft/lang/" + locale + ".json");
    }

    public static Path musicDir() {
        return packDir().resolve("assets/minecraft/sounds/music");
    }
}
