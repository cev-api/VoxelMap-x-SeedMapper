# VoxelMap x SeedMapper by CevAPI

VoxelMap x SeedMapper is a heavily modified fork of [VoxelMap Updated](https://github.com/fantahund/VoxelMap) with SeedMapper integrated directly into the client.

This fork combines VoxelMap's minimap and fullscreen world map with SeedMapper locating, overlays, loot tools, persistence, UI integration, and a range of rendering and behavior fixes.

## Changes From Upstream

### SeedMapper Integration

- Integrated SeedMapper directly into VoxelMap as a built-in client feature.
- Added structure locating, biome locating, loot locating, slime chunk locating, ore vein locating, and terrain analysis.
- Added SeedMapper markers to both the minimap and fullscreen world map.
- Added SeedMapper completion state for marking located structures as done.
- Added support for saved seeds, manual seed input, and per-server/world SeedMapper state.
- Added cubiomes-based world generation logic and bundled native `cubiomes.dll` support.

#### SeedMapper Menu
![SeedMapMenu](https://i.imgur.com/jMBkgbm.png)

#### Locate structures
![LocateStructure](https://i.imgur.com/niui9AN.png)

### SeedMapper Commands

- Added local client commands for SeedMapper features.
- Added `/seedmap` and `/sm` command roots.
- Added commands for locating structures, biomes, ore veins, slime chunks, and loot.
- Added commands for highlighting ores, ore veins, terrain, canyons, and caves.
- Added commands for clearing ESP highlights.
- Added commands for exporting visible or custom map areas.
- Added commands for running SeedMapper source-style command chains.

Common commands:

- `/seedmap help`
- `/seedmap seed <seed>`
- `/seedmap locate structure <feature_id>`
- `/seedmap locate biome <biome_name>`
- `/seedmap locate orevein <iron|copper>`
- `/seedmap locate slime`
- `/seedmap locate loot <text>`
- `/seedmap highlight ore <block> [chunks]`
- `/seedmap highlight orevein [chunks]`
- `/seedmap highlight terrain [chunks]`
- `/seedmap highlight canyon [chunks]`
- `/seedmap highlight cave [chunks]`
- `/seedmap highlight clear`
- `/seedmap export [visible|radius <blocks>|area <x> <z> <radius>]`
- `/seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...`
- `/sm ...`

### ESP And Highlighting

- Added SeedMapper ESP rendering for blocks, ore veins, caves, canyons, and terrain.
- Added configurable ESP styles, fill rendering, outline colors, alpha values, and timeout behavior.
- Added hotkeys for toggling SeedMapper overlay, running ESP scans, clearing ESP, opening the loot viewer, and opening SeedMapper settings.
- Added tracer/highlight support for located structures and loot results.
- Added auto-hide behavior for highlights when the player gets near a target.

#### ESP settings
![ESPSettings](https://i.imgur.com/3QQgUH6.png)

#### Terrain highlighting
![TerrainESP](https://i.imgur.com/XvwiaI3.png)

#### Ore highlighting
![HardcOre](https://i.imgur.com/rEWn5PP.png)

### Map And Overlay Features

- Added Newer New Chunks detection and rendering.
- Added explored chunk tracking with persistent chunk history.
- Added chunk overlays for explored chunks, new chunks, liquid exploit tracking, and block update exploit tracking.
- Added chunk grid and slime chunk display options.
- Added improved chunk line rendering, including solid line mode and configurable line thickness.
- Added portal marker overlays for Nether portals, End portals, and End gateways.
- Added automatic portal recording and improved portal detection logic.

#### Chunk overlay options
![ChunkOptions](https://i.imgur.com/jlwfn9V.png)

### World Map Improvements

- Added SeedMapper structure icons and loot markers to the fullscreen world map.
- Added world map popup actions for SeedMapper markers, loot actions, completion toggles, and waypoint interactions.
- Added coordinate editing/recentering so the world map can jump to arbitrary coordinates.
- Added context menu option to recenter the world map on the player.
- Added visible-area export support for SeedMapper.
- Extended deep zoom-out behavior.
- Added performance mode for far zoom levels with configurable threshold.
- Added option to keep waypoints visible while world map performance mode is active.
- Added optional zoom level display while zooming.
- Improved region texture processing and rendering behavior at extreme zoom levels.

#### SeedMapper integration
![LargeMap](https://i.imgur.com/qDZdMvs.png)

#### Marking Nether portal as complete
![MarkComplete](https://i.imgur.com/F6f8TF6.png)

#### View other coordinates
![View](https://i.imgur.com/q0XYZRF.png)

### Minimap Improvements

- Added SeedMapper marker rendering on the minimap.
- Added Newer New Chunks display on the minimap.
- Added explored chunk breadcrumb-style display.
- Added portal marker rendering.
- Added minimap toggle hotkey.
- Improved minimap zoom behavior and rendering at extreme zoom levels.
- Added scoreboard positioning option below the minimap.

#### New chunk detection and structure detection
![NewChunks](https://i.imgur.com/oggJc4d.png)

#### Explored chunks and portal detection
![ExploredChunks+PortalDetection](https://i.imgur.com/c1Q41xy.png)

### Loot Viewer

- Added an integrated SeedMapper loot viewer.
- Added loot searching by item name, item ID, enchantment, and NBT-style terms.
- Added per-result actions for highlighting, waypoints, and coordinate copying.
- Added loot result highlighting on the map.

![LocateLoot](https://i.imgur.com/h7JtYR9.png)

### Datapack Support

- Added datapack import support for SeedMapper structures.
- Added datapack URL, cache path, autoload, enable/disable, color scheme, and icon style settings.
- Added UI for managing imported datapack structures.
- Added persistent per-world enabled/disabled datapack structure state.
- Added datapack-located marker persistence.

### Waypoint Changes

- Added waypoint import from Xaero's Minimap.
- Added waypoint import from Wurst.
- Added multi-select waypoint handling.
- Added optional waypoint delete confirmation.
- Added automatic portal waypoint creation.
- Added expanded waypoint compass settings.
- Added waypoint name/distance placement controls.
- Improved waypoint list filtering, sorting, deleting, and highlighting behavior.

![WayPoints](https://i.imgur.com/w2s7jOl.png)

### UI And Settings

- Improved options menu design
- Added a dedicated SeedMapper tab in the minimap options screen.
- Added new SeedMapper screens for locating, loot viewing, ESP profiles, datapacks, saved seeds, and saved string maps.
- Added chunk overlay settings screen.
- Added new world map options for performance mode, zoom, chunk lines, and waypoint visibility.
- Added new minimap options for chunk grid, slime chunks, portal waypoints, scoreboard placement, and coordinate display modes.
- Updated the welcome screen branding to `VoxelMap x SeedMapper by CevAPI`.
- Added localization strings for the new UI, controls, commands, and SeedMapper features.

![UI](https://i.imgur.com/AW5yD4q.png)

### Persistence

- Seed, ESP, datapack, and completion state are saved through the VoxelMap settings system.
- Tracer settings persist alongside other map configuration.
- Explored chunks and portal markers are saved per world/server.
- World and server specific SeedMapper behavior is preserved.

### Rendering And Compatibility

- Added custom render pipeline/render type support for new overlays.
- Added mixins for client-level updates, packet-based chunk tracking, and world renderer integration.
- Added compatibility helpers for Wurst waypoint data.
- Added Minecraft palette classes used by the fork's map/chunk handling.
- Improved map update handling around chunk changes and loaded chunk checks.

### Loader And Build Changes

- Added SeedMapper command registration for Fabric, Forge, and NeoForge.
- Updated mod metadata to use `voxelmap-cevapi`.
- Renamed the mod display name to `VoxelMap x SeedMapper by CevAPI`.
- Updated project links to this fork.
- Added fork versioning via `forkVersion`.
- Changed generated jar names to `voxelmap-x-seedmapper_<loader>_v<version>.jar`.
- Added VS Code launch configuration.

Root build output jars are generated under names like:

- `build/libs/voxelmap-x-seedmapper_fabric_v0.03.jar`
- `build/libs/voxelmap-x-seedmapper_forge_v0.03.jar`
- `build/libs/voxelmap-x-seedmapper_neoforge_v0.03.jar`

### Documentation And Assets

- Rewrote the README around the forked SeedMapper feature set.
- Added SeedMapper icons for structures, loot, portals, ores, slime chunks, waypoints, and player markers.
- Added SeedMapper UI assets such as arrows and chest container imagery.

## Platform Support

- Fabric
- Forge (Needs Testing)
- NeoForge (Needs Testing)

Builds are produced for all supported loaders from one codebase, with client command registration and mod metadata wired separately for each loader.

## Notes

- This is not intended to be an upstream-equivalent release.
- Some legacy localization keys may still remain after UI changes or removals.
- The project prioritizes SeedMapper integration and UI consistency over strict upstream parity.
- Project is still undergoing and there may be some bugs. 
