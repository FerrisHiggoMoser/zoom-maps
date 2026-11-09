package zoommaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zoommaps.ZoomMaps;

@Mixin(EmptyMapItem.class)
public class EmptyMapItemMixin {
    @WrapOperation(
        method = "use",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/MapItem;create(Lnet/minecraft/server/level/ServerLevel;IIBZZ)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack zoommaps$createZoomed(
        ServerLevel level,
        int x,
        int z,
        byte scale,
        boolean trackPosition,
        boolean unlimitedTracking,
        Operation<ItemStack> original,
        @Local(argsOnly = true) Player player,
        @Local(argsOnly = true) InteractionHand hand
    ) {
        int zoom = ZoomMaps.zoomFor(player, hand);
        if (zoom == 0) {
            return original.call(level, x, z, scale, trackPosition, unlimitedTracking);
        }
        return ZoomMaps.create(level, x, z, zoom, trackPosition, unlimitedTracking);
    }
}
