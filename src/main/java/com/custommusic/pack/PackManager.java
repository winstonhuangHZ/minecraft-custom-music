package com.custommusic.pack;

import com.custommusic.CustomMusicClient;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.Collection;

/** 把生成的资源包塞进资源包栈，并保证它优先级最高。 */
public final class PackManager {

    private PackManager() {
    }

    private static boolean isOurs(String id) {
        return id != null && (id.equals(PackPaths.FOLDER_NAME) || id.endsWith("/" + PackPaths.FOLDER_NAME));
    }

    /** 在资源包仓库里找我们生成的包，id 一般是 file/CustomMusicPack。 */
    public static String findPackId(PackRepository repository) {
        for (String id : repository.getAvailableIds()) {
            if (isOurs(id)) {
                return id;
            }
        }
        return null;
    }

    /** 从一批已选的 Pack 里找出我们那个。 */
    public static Pack findOurPack(Collection<Pack> packs) {
        for (Pack pack : packs) {
            if (isOurs(pack.getId())) {
                return pack;
            }
        }
        return null;
    }

    public static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        PackRepository repository = minecraft.getResourcePackRepository();
        String id = findPackId(repository);
        return id != null && repository.getSelectedIds().contains(id);
    }

    /** 启用或更新资源包并重载资源，必须在客户端主线程调用。 */
    public static void enableAndReload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        PackRepository repository = minecraft.getResourcePackRepository();

        repository.reload();
        String id = findPackId(repository);
        if (id == null) {
            CustomMusicClient.LOG.warn("资源包仓库里没有 {},跳过启用", PackPaths.FOLDER_NAME);
            return;
        }

        repository.addPack(id);

        Pack pack = repository.getPack(id);
        if (pack != null && !pack.getCompatibility().isCompatible()) {
            CustomMusicClient.LOG.warn("资源包被判定为不兼容({}),仍会启用", pack.getCompatibility());
        }

        minecraft.options.updateResourcePacks(repository);
        minecraft.options.save();
        minecraft.reloadResourcePacks();
    }
}
