package zoomtest;

import com.mojang.authlib.GameProfile;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelResource;
import zoommaps.ZoomedMap;

/** Test-only mod: drives a fake player through the zoomed map paths, then stops the server. */
public class Harness implements ModInitializer {
    private static final int X = 200;
    private static final int Z = -300;
    private final List<String> results = new ArrayList<>();
    private int failures;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Path state = server.getWorldPath(LevelResource.ROOT).resolve("zoomtest-ids.txt");
                if (Files.exists(state)) {
                    afterRestart(server, state);
                } else {
                    firstBoot(server, state);
                }
            } catch (Throwable t) {
                check("no exception", false, t.toString());
                t.printStackTrace();
            }
            results.add(failures == 0 ? "ALL PASSED" : failures + " FAILED");
            try {
                Files.write(server.getWorldPath(LevelResource.ROOT).resolve("zoomtest-results.txt"), results);
            } catch (Exception ignored) {
            }
            results.forEach(line -> System.out.println("[ZOOMTEST] " + line));
            server.halt(false);
        });
    }

    private void firstBoot(MinecraftServer server, Path state) throws Exception {
        ServerLevel level = server.overworld();
        level.getChunk(X >> 4, Z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, X, Z);
        FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Tester"));
        player.setPos(X + 0.5, y, Z + 0.5);

        ItemStack plain = makeMap(level, player, false, false);
        ItemStack zoom1 = makeMap(level, player, true, false);
        ItemStack zoom2 = makeMap(level, player, true, true);
        MapItemSavedData plainData = MapItem.getSavedData(plain, level);
        MapItemSavedData data1 = MapItem.getSavedData(zoom1, level);
        MapItemSavedData data2 = MapItem.getSavedData(zoom2, level);

        check("plain map has zoom 0", zoom(plainData) == 0, "zoom " + zoom(plainData));
        check("spyglass map has zoom 1", zoom(data1) == 1, "zoom " + zoom(data1));
        check("spyglass + sneak map has zoom 2", zoom(data2) == 2, "zoom " + zoom(data2));
        check("zoomed maps stay scale 0", data1.scale == 0 && data2.scale == 0, data1.scale + "/" + data2.scale);
        // 200 lies in [192, 256) for 64-wide maps and [192, 224) for 32-wide maps; -300 in [-320, -256) and [-320, -288).
        check("zoom 1 center", data1.centerX == 224 && data1.centerZ == -288, data1.centerX + "," + data1.centerZ);
        check("zoom 2 center", data2.centerX == 208 && data2.centerZ == -304, data2.centerX + "," + data2.centerZ);

        for (int i = 0; i < 32; i++) {
            tickHeld(level, player, plain);
            tickHeld(level, player, zoom1);
            tickHeld(level, player, zoom2);
        }
        compareWithPlain(plainData, data1, "zoom 1");
        compareWithPlain(plainData, data2, "zoom 2");
        savePng(server, plainData, "plain.png");
        savePng(server, data1, "zoom1.png");
        savePng(server, data2, "zoom2.png");

        // Player marker: 0.5 blocks east of block 200, on a map whose west edge is 192 at 4 px per block.
        MapDecoration marker = playerMarker(data2);
        int expectedX = (int) ((X + 0.5 - data2.centerX) * 4 * 2 + 0.5);
        int expectedZ = (int) ((Z + 0.5 - data2.centerZ) * 4 * 2 + 0.5);
        check("player marker on zoom 2 map", marker != null && marker.x() == expectedX && marker.y() == expectedZ,
            marker == null ? "none" : marker.x() + "," + marker.y() + " expected " + expectedX + "," + expectedZ);
        check("player marker on plain map", playerMarker(plainData) != null
            && playerMarker(plainData).x() == (byte) ((X + 0.5 - plainData.centerX) * 2 + 0.5), "" + playerMarker(plainData));

        // 100 blocks away is off a 32-block map but within 320, so the edge dot should show.
        player.setPos(X + 100.5, y, Z + 0.5);
        tickHeld(level, player, zoom2);
        MapDecoration offMap = decoration(data2);
        check("off-map dot at 100 blocks", offMap != null && offMap.type().is(MapDecorationTypes.PLAYER_OFF_MAP), "" + offMap);
        player.setPos(X + 400.5, y, Z + 0.5);
        tickHeld(level, player, zoom2);
        check("no dot past 320 blocks", decoration(data2) == null, "" + decoration(data2));
        player.setPos(X + 0.5, y, Z + 0.5);

        byte[] before = data2.colors.clone();
        ItemStack scaled = zoom2.copy();
        scaled.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        Items.FILLED_MAP.onCraftedPostProcess(scaled, level);
        MapItemSavedData out1 = MapItem.getSavedData(scaled, level);
        check("paper zooms 32 out to 64", zoom(out1) == 1 && out1.centerX == 224 && out1.centerZ == -288,
            zoom(out1) + " " + out1.centerX + "," + out1.centerZ);
        scaled.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        Items.FILLED_MAP.onCraftedPostProcess(scaled, level);
        MapItemSavedData out0 = MapItem.getSavedData(scaled, level);
        check("paper zooms 64 out to a normal 1:1 map", zoom(out0) == 0 && out0.scale == 0
            && out0.centerX == plainData.centerX && out0.centerZ == plainData.centerZ, zoom(out0) + " " + out0.centerX + "," + out0.centerZ);
        scaled.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        Items.FILLED_MAP.onCraftedPostProcess(scaled, level);
        check("then paper does vanilla 1:2", MapItem.getSavedData(scaled, level).scale == 1, "");

        ItemStack locked = zoom2.copy();
        locked.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
        Items.FILLED_MAP.onCraftedPostProcess(locked, level);
        MapItemSavedData lockedData = MapItem.getSavedData(locked, level);
        check("glass pane lock keeps zoom", lockedData.locked && zoom(lockedData) == 2 && Arrays.equals(lockedData.colors, before), "");

        Tag encoded = MapItemSavedData.type(zoom2.get(DataComponents.MAP_ID)).codec().encodeStart(NbtOps.INSTANCE, data2).getOrThrow();
        check("zoom written to map file", ((CompoundTag) encoded).getIntOr("zoommaps:zoom", -1) == 2, encoded.toString().substring(0, 80));
        Tag plainEncoded = MapItemSavedData.type(plain.get(DataComponents.MAP_ID)).codec().encodeStart(NbtOps.INSTANCE, plainData).getOrThrow();
        check("plain map file untouched", !((CompoundTag) plainEncoded).contains("zoommaps:zoom"), "");

        Files.write(state, List.of(
            String.valueOf(zoom2.get(DataComponents.MAP_ID).id()),
            String.valueOf(zoom1.get(DataComponents.MAP_ID).id()),
            String.valueOf(Arrays.hashCode(data2.colors))
        ));
        server.saveEverything(false, true, true);
    }

    private void afterRestart(MinecraftServer server, Path state) throws Exception {
        ServerLevel level = server.overworld();
        List<String> lines = Files.readAllLines(state);
        MapItemSavedData data2 = level.getMapData(new MapId(Integer.parseInt(lines.get(0))));
        MapItemSavedData data1 = level.getMapData(new MapId(Integer.parseInt(lines.get(1))));
        check("zoom 2 survives restart", data2 != null && zoom(data2) == 2 && data2.centerX == 208 && data2.centerZ == -304,
            data2 == null ? "missing" : zoom(data2) + " " + data2.centerX + "," + data2.centerZ);
        check("zoom 1 survives restart", data1 != null && zoom(data1) == 1 && data1.centerX == 224, data1 == null ? "missing" : "" + zoom(data1));
        check("pixels survive restart", Arrays.hashCode(data2.colors) == Integer.parseInt(lines.get(2)), "");

        // Holding it again must keep drawing zoomed; a vanilla redraw would change the pixels.
        level.getChunk(X >> 4, Z >> 4);
        FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Tester2"));
        player.setPos(X + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, X, Z), Z + 0.5);
        ItemStack held = new ItemStack(Items.FILLED_MAP);
        held.set(DataComponents.MAP_ID, new MapId(Integer.parseInt(lines.get(0))));
        for (int i = 0; i < 32; i++) {
            tickHeld(level, player, held);
        }
        check("still draws zoomed after restart", Arrays.hashCode(data2.colors) == Integer.parseInt(lines.get(2)), "");
    }

    private ItemStack makeMap(ServerLevel level, FakePlayer player, boolean spyglass, boolean sneak) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAP));
        player.setItemInHand(InteractionHand.OFF_HAND, spyglass ? new ItemStack(Items.SPYGLASS) : ItemStack.EMPTY);
        player.setShiftKeyDown(sneak);
        player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        ItemStack map = player.getMainHandItem().copy();
        check("right-click gives a filled map" + (spyglass ? " (spyglass" + (sneak ? ", sneaking)" : ")") : ""),
            map.is(Items.FILLED_MAP), map.toString());
        return map;
    }

    private void tickHeld(ServerLevel level, FakePlayer player, ItemStack map) {
        player.setItemInHand(InteractionHand.MAIN_HAND, map);
        Items.FILLED_MAP.inventoryTick(map, level, player, EquipmentSlot.MAINHAND);
    }

    private void compareWithPlain(MapItemSavedData plain, MapItemSavedData zoomed, String name) {
        int zoom = zoom(zoomed);
        int pixels = 1 << zoom;
        int span = 128 >> zoom;
        int minX = zoomed.centerX - span / 2;
        int minZ = zoomed.centerZ - span / 2;
        int mismatches = 0;
        int drawn = 0;
        for (int col = 0; col < span; col++) {
            for (int row = 0; row < span; row++) {
                int plainX = minX + col - (plain.centerX - 64);
                int plainZ = minZ + row - (plain.centerZ - 64);
                byte expected = plain.colors[plainX + plainZ * 128];
                for (int px = 0; px < pixels; px++) {
                    for (int pz = 0; pz < pixels; pz++) {
                        byte actual = zoomed.colors[col * pixels + px + (row * pixels + pz) * 128];
                        if (actual != expected) {
                            mismatches++;
                        }
                        if (actual != 0) {
                            drawn++;
                        }
                    }
                }
            }
        }
        check(name + " is the 1:1 map blown up", mismatches == 0 && drawn > 128 * 128 * 9 / 10,
            mismatches + " mismatched pixels, " + drawn + " drawn of 16384");
    }

    private void savePng(MinecraftServer server, MapItemSavedData data, String file) throws Exception {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 128 * 128; i++) {
            image.setRGB(i % 128, i / 128, MapColor.getColorFromPackedId(data.colors[i] & 255));
        }
        ImageIO.write(image, "png", server.getWorldPath(LevelResource.ROOT).resolve(file).toFile());
    }

    private static MapDecoration playerMarker(MapItemSavedData data) {
        MapDecoration found = decoration(data);
        return found != null && found.type().is(MapDecorationTypes.PLAYER) ? found : null;
    }

    private static MapDecoration decoration(MapItemSavedData data) {
        for (MapDecoration decoration : data.getDecorations()) {
            return decoration;
        }
        return null;
    }

    private static int zoom(MapItemSavedData data) {
        return ((ZoomedMap) data).zoommaps$getZoom();
    }

    private void check(String name, boolean ok, String detail) {
        if (!ok) {
            failures++;
        }
        results.add((ok ? "PASS " : "FAIL ") + name + (ok || detail.isEmpty() ? "" : " -- " + detail));
    }
}
