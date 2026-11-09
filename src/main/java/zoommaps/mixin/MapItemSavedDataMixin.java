package zoommaps.mixin;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapFrame;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zoommaps.ZoomMaps;
import zoommaps.ZoomedMap;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin implements ZoomedMap {
    @Shadow @Final @Mutable public int centerX;
    @Shadow @Final @Mutable public int centerZ;
    @Shadow @Final public ResourceKey<Level> dimension;
    @Shadow @Final private boolean trackingPosition;
    @Shadow @Final private boolean unlimitedTracking;
    @Shadow @Final private Map<String, MapBanner> bannerMarkers;
    @Shadow @Final private Map<String, MapFrame> frameMarkers;

    @Unique
    private int zoommaps$zoom;

    @Shadow
    private void addDecoration(
        Holder<MapDecorationType> type, @Nullable LevelAccessor level, String key, double xPos, double zPos, double yRot, @Nullable Component name
    ) {
        throw new AssertionError();
    }

    @Shadow
    private static String getFrameKey(int id) {
        throw new AssertionError();
    }

    @Override
    public int zoommaps$getZoom() {
        return zoommaps$zoom;
    }

    @Override
    public void zoommaps$setZoom(int zoom) {
        zoommaps$zoom = zoom;
        // Markers loaded from disk were placed before the zoom was known.
        for (MapBanner banner : bannerMarkers.values()) {
            addDecoration(banner.getDecoration(), null, banner.getId(), banner.pos().getX(), banner.pos().getZ(), 180.0, banner.name().orElse(null));
        }
        for (MapFrame frame : frameMarkers.values()) {
            addDecoration(MapDecorationTypes.FRAME, null, getFrameKey(frame.entityId()), frame.pos().getX(), frame.pos().getZ(), frame.rotation(), null);
        }
    }

    @Override
    public void zoommaps$setCenter(int x, int z) {
        centerX = x;
        centerZ = z;
    }

    @Inject(method = "type", at = @At("RETURN"), cancellable = true)
    private static void zoommaps$saveZoom(MapId id, CallbackInfoReturnable<SavedDataType<MapItemSavedData>> cir) {
        cir.setReturnValue(ZoomMaps.withZoomCodec(cir.getReturnValue()));
    }

    @Inject(method = "locked", at = @At("RETURN"))
    private void zoommaps$lockKeepsZoom(CallbackInfoReturnable<MapItemSavedData> cir) {
        if (zoommaps$zoom > 0) {
            ((ZoomedMap) cir.getReturnValue()).zoommaps$setZoom(zoommaps$zoom);
        }
    }

    // Paper in a cartography table steps a zoomed map back out one level at a time.
    @Inject(method = "scaled", at = @At("HEAD"), cancellable = true)
    private void zoommaps$zoomOut(CallbackInfoReturnable<MapItemSavedData> cir) {
        if (zoommaps$zoom > 0) {
            cir.setReturnValue(ZoomMaps.createFresh(centerX, centerZ, zoommaps$zoom - 1, trackingPosition, unlimitedTracking, dimension));
        }
    }

    @ModifyVariable(method = "addDecoration", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double zoommaps$decorationX(double xPos) {
        return zoommaps$zoom == 0 ? xPos : centerX + ZoomMaps.zoomDelta(xPos - centerX, zoommaps$zoom);
    }

    @ModifyVariable(method = "addDecoration", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double zoommaps$decorationZ(double zPos) {
        return zoommaps$zoom == 0 ? zPos : centerZ + ZoomMaps.zoomDelta(zPos - centerZ, zoommaps$zoom);
    }

    @Inject(method = "toggleBanner", at = @At("HEAD"), cancellable = true)
    private void zoommaps$bannerOffMap(LevelAccessor level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (zoommaps$zoom > 0 && !ZoomMaps.isOnMap(pos.getX() + 0.5 - centerX, pos.getZ() + 0.5 - centerZ, zoommaps$zoom)) {
            cir.setReturnValue(false);
        }
    }
}
