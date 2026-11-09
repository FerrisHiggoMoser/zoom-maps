package zoommaps.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zoommaps.ZoomMaps;
import zoommaps.ZoomedMap;

@Mixin(MapItem.class)
public class MapItemMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void zoommaps$drawZoomed(Level level, Entity player, MapItemSavedData data, CallbackInfo ci) {
        int zoom = ((ZoomedMap) data).zoommaps$getZoom();
        if (zoom > 0) {
            ZoomMaps.update(level, player, data, zoom);
            ci.cancel();
        }
    }
}
