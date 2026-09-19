package com.custommusic.mixin;

import com.custommusic.pack.PackManager;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 把自定义音乐包提到已选列表的最前面。
 * 原版会把「必需包」（原版资源包、服务器下发的资源包）插到列表顶部，
 * 而列表顺序就是加载优先级，所以默认服务器包会盖掉玩家的包。
 * 这里在 rebuildSelected 返回前把我们的包挪到第一位。
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void custommusic$promoteOurPack(Collection<String> ids,
                                            CallbackInfoReturnable<List<Pack>> cir) {
        List<Pack> selected = cir.getReturnValue();
        if (selected == null || selected.isEmpty()) {
            return;
        }

        Pack ours = PackManager.findOurPack(selected);
        if (ours == null || selected.get(0) == ours) {
            return;
        }

        List<Pack> reordered = new ArrayList<>(selected.size());
        reordered.add(ours);
        for (Pack pack : selected) {
            if (pack != ours) {
                reordered.add(pack);
            }
        }
        cir.setReturnValue(reordered);
    }
}
