# Zoomed Maps

A server-side Fabric mod for Minecraft 26.3 that adds zoomed-in maps. Players don't need to install anything; a normal client shows them fine.

| Map | Area | Pixels per block |
| --- | --- | --- |
| Normal 1:1 | 128 × 128 blocks | 1 |
| Zoomed | 64 × 64 blocks | 2 × 2 |
| Zoomed more | 32 × 32 blocks | 4 × 4 |

## How to make one

- Hold an empty map in your main hand and a spyglass in your off hand, then right-click: 64 × 64 map.
- Do the same while sneaking: 32 × 32 map.
- Without a spyglass you get a normal map.
- Paper in a cartography table zooms out one step at a time: 32 → 64 → normal 1:1 → vanilla 1:2 and up.

Copying, locking with a glass pane, player markers, item frame markers and banners all work as usual. Zoomed maps line up inside the normal map grid. The map tooltip still says "Scaling at 1:1", because the client writes that text itself.

## Install

Put the jar from [Releases](https://github.com/FerrisHiggoMoser/zoom-maps/releases) in the server's `mods` folder. Needs Fabric Loader and Minecraft 26.3; Fabric API is not required.

The zoom level is saved as `zoommaps:zoom` in each map's data file. If you remove the mod, zoomed maps turn back into normal maps of the same area the next time they are held.

## Build

Uses JDK 25 `javac` directly, no Gradle. Point `SERVER` at a Fabric 26.3 server folder that has been started once:

```sh
SERVER=~/minecraft-server ./build.sh
```

The jar ends up in `build/`.

## Test

`test/harness` is a test-only mod that uses a fake player to make maps, checks them pixel by pixel against a 1:1 map of the same spot, and tests markers, zooming out, locking and saving. Build it with `test/harness/build.sh`, put it in a test server's `mods` folder along with the mod and Fabric API, and start the server twice (the second start checks the saved maps). Results go to `zoomtest-results.txt` in the world folder.
