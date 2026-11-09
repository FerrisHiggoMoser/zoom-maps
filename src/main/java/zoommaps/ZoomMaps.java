package zoommaps;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class ZoomMaps {
    public static final int MAX_ZOOM = 2;
    private static final String ZOOM_KEY = "zoommaps:zoom";
    private static final Codec<MapItemSavedData> CODEC = new ZoomCodec(MapItemSavedData.CODEC);

    private ZoomMaps() {
    }

    /** Spyglass in the other hand zooms in once, sneaking as well zooms in twice. */
    public static int zoomFor(Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!player.getItemInHand(other).is(Items.SPYGLASS)) {
            return 0;
        }
        return player.isShiftKeyDown() ? 2 : 1;
    }

    public static ItemStack create(ServerLevel level, int x, int z, int zoom, boolean trackPosition, boolean unlimitedTracking) {
        MapItemSavedData data = createFresh(x, z, zoom, trackPosition, unlimitedTracking, level.dimension());
        MapId id = level.getFreeMapId();
        level.setMapData(id, data);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, id);
        return map;
    }

    public static MapItemSavedData createFresh(
        double x, double z, int zoom, boolean trackPosition, boolean unlimitedTracking, ResourceKey<Level> dimension
    ) {
        MapItemSavedData data = MapItemSavedData.createFresh(x, z, (byte) 0, trackPosition, unlimitedTracking, dimension);
        if (zoom > 0) {
            // Same grid rule as vanilla, so zoomed maps tile neatly inside a 1:1 map.
            int span = 128 >> zoom;
            int centerX = Mth.floor((x + 64.0) / span) * span + span / 2 - 64;
            int centerZ = Mth.floor((z + 64.0) / span) * span + span / 2 - 64;
            ZoomedMap zoomed = (ZoomedMap) data;
            zoomed.zoommaps$setCenter(centerX, centerZ);
            zoomed.zoommaps$setZoom(zoom);
        }
        return data;
    }

    /** Turns a distance in blocks from the map center into map units, which are blocks on a 1:1 map. */
    public static double zoomDelta(double blocks, int zoom) {
        double zoomed = blocks * (1 << zoom);
        // Keep the off-map player dot out to 320 real blocks, like a 1:1 map does.
        if (Math.abs(blocks) < 320.0 && Math.abs(zoomed) >= 320.0) {
            zoomed = Math.copySign(319.0, zoomed);
        }
        return zoomed;
    }

    public static boolean isOnMap(double xBlocks, double zBlocks, int zoom) {
        int scale = 1 << zoom;
        return Math.abs(xBlocks * scale) <= 63.0 && Math.abs(zBlocks * scale) <= 63.0;
    }

    public static SavedDataType<MapItemSavedData> withZoomCodec(SavedDataType<MapItemSavedData> type) {
        return new SavedDataType<>(type.id(), type.constructor(), CODEC, type.dataFixType());
    }

    /**
     * Vanilla MapItem.update at 1:1, except each block is drawn as a square of pixels.
     * Colors and shading match a 1:1 map of the same spot exactly.
     */
    public static void update(Level level, Entity entity, MapItemSavedData data, int zoom) {
        if (level.dimension() != data.dimension || !(entity instanceof Player player)) {
            return;
        }

        int pixels = 1 << zoom;
        int span = 128 >> zoom;
        int minX = data.centerX - span / 2;
        int minZ = data.centerZ - span / 2;
        boolean hasCeiling = level.dimensionType().hasCeiling();
        int radius = hasCeiling ? 64 : 128;
        int playerX = Mth.floor(player.getX());
        int playerZ = Mth.floor(player.getZ());

        MapItemSavedData.HoldingPlayer holder = data.getHoldingPlayer(player);
        holder.step++;
        MutableBlockPos pos = new MutableBlockPos();
        MutableBlockPos belowPos = new MutableBlockPos();

        // A sixteenth of the columns per tick, like vanilla.
        for (int column = holder.step & 15; column < span; column += 16) {
            int x = minX + column;
            double previousHeight = 0.0;

            // Row -1 is only read so the first row can be shaded against its northern neighbour.
            for (int row = -1; row < span; row++) {
                int z = minZ + row;
                LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
                if (chunk.isEmpty()) {
                    continue;
                }

                MapColor color;
                double height;
                int waterDepth = 0;
                if (hasCeiling) {
                    int noise = x + z * 231871;
                    noise = noise * noise * 31287121 + noise * 11;
                    BlockState state = (noise >> 20 & 1) == 0 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    color = state.getMapColor(level, BlockPos.ZERO);
                    height = 100.0;
                } else {
                    pos.set(x, 0, z);
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                    BlockState state;
                    if (y <= level.getMinY()) {
                        state = Blocks.BEDROCK.defaultBlockState();
                    } else {
                        do {
                            pos.setY(--y);
                            state = chunk.getBlockState(pos);
                        } while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinY());

                        if (y > level.getMinY() && !state.getFluidState().isEmpty()) {
                            int belowY = y - 1;
                            belowPos.set(pos);
                            BlockState below;
                            do {
                                belowPos.setY(belowY--);
                                below = chunk.getBlockState(belowPos);
                                waterDepth++;
                            } while (belowY > level.getMinY() && !below.getFluidState().isEmpty());

                            state = surfaceState(level, state, pos);
                        }
                    }

                    data.checkBanners(level, x, z);
                    color = state.getMapColor(level, pos);
                    height = y;
                }

                int checker = x + z & 1;
                MapColor.Brightness brightness;
                if (color == MapColor.WATER) {
                    double shade = waterDepth * 0.1 + checker * 0.2;
                    brightness = shade < 0.5 ? MapColor.Brightness.HIGH : shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double shade = (height - previousHeight) * 0.8 + (checker - 0.5) * 0.4;
                    brightness = shade > 0.6 ? MapColor.Brightness.HIGH : shade < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previousHeight = height;

                int dx = x - playerX;
                int dz = z - playerZ;
                if (row < 0 || dx * dx + dz * dz >= radius * radius) {
                    continue;
                }

                byte packed = color.getPackedId(brightness);
                for (int px = 0; px < pixels; px++) {
                    for (int pz = 0; pz < pixels; pz++) {
                        data.updateColor(column * pixels + px, row * pixels + pz, packed);
                    }
                }
            }
        }
    }

    private static BlockState surfaceState(Level level, BlockState state, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluid.createLegacyBlock() : state;
    }

    /** The map's own codec plus one extra field, so the zoom lives in the map's .dat file. */
    private record ZoomCodec(Codec<MapItemSavedData> inner) implements Codec<MapItemSavedData> {
        @Override
        public <T> DataResult<Pair<MapItemSavedData, T>> decode(DynamicOps<T> ops, T input) {
            return inner.decode(ops, input).map(pair -> {
                ops.get(input, ZOOM_KEY).flatMap(ops::getNumberValue).result().ifPresent(zoom -> {
                    int value = Mth.clamp(zoom.intValue(), 0, MAX_ZOOM);
                    if (value > 0) {
                        ((ZoomedMap) pair.getFirst()).zoommaps$setZoom(value);
                    }
                });
                return pair;
            });
        }

        @Override
        public <T> DataResult<T> encode(MapItemSavedData data, DynamicOps<T> ops, T prefix) {
            DataResult<T> result = inner.encode(data, ops, prefix);
            int zoom = ((ZoomedMap) data).zoommaps$getZoom();
            return zoom == 0 ? result : result.flatMap(map -> ops.mergeToMap(map, ops.createString(ZOOM_KEY), ops.createInt(zoom)));
        }
    }
}
