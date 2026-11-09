package zoommaps;

/** Added to MapItemSavedData. Zoom 0 is a normal map, 1 shows 64x64 blocks, 2 shows 32x32. */
public interface ZoomedMap {
    int zoommaps$getZoom();

    void zoommaps$setZoom(int zoom);

    void zoommaps$setCenter(int x, int z);
}
