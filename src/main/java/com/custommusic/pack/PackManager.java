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
        ensureSelected();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.reloadResourcePacks();
        }
    }

    /**
     * 启动时用：没选中就勾上（并且重载），本来就选中就什么都不做。
     * 上一个会话已经启用过的话，游戏启动时那次资源加载已经带上它了，
     * 再重载一次只会让声音引擎白掉一次线，重载窗口内所有播放请求都会失败。
     */
    public static void ensureEnabled() {
        if (!ensureSelected()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.reloadResourcePacks();
        }
    }

    /** 勾选资源包并持久化；返回 true 表示「这次真的改动了」。 */
    private static boolean ensureSelected() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        PackRepository repository = minecraft.getResourcePackRepository();

        repository.reload();
        String id = findPackId(repository);
        if (id == null) {
            CustomMusicClient.LOG.warn("资源包仓库里没有 {}，还没生成过资源包", PackPaths.FOLDER_NAME);
            return false;
        }

        boolean changed = !repository.getSelectedIds().contains(id);
        repository.addPack(id);

        Pack pack = repository.getPack(id);
        if (pack != null && !pack.getCompatibility().isCompatible()) {
            CustomMusicClient.LOG.warn("资源包被判定为不兼容({}),仍会启用", pack.getCompatibility());
        }

        minecraft.options.updateResourcePacks(repository);
        minecraft.options.save();
        return changed;
    }
}
