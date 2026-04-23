# VoxelMap x SeedMapper by CevAPI

This repository is a heavily modified fork of VoxelMap Updated with SeedMapper integrated directly into the client.

It extends the original project with SeedMapper locating, overlays, loot tools, persistence, UI integration, and a range of rendering and behavior fixes.

## Overview

This fork combines VoxelMap's minimap and world map systems with SeedMapper's locating and analysis features in a single mod.

Compared to upstream, it includes:

- SeedMapper structure, biome, loot, and ESP tooling
- Integrated minimap and world map overlays with SeedMapper structures, Newer New Chunks and Explored Chunk Highlighting
- Extra persistence for SeedMapper state and tracer settings
- Expanded UI flows and settings pages
- Bug fixes and rendering improvements

## Platform support

- Builds are produced for Fabric, Forge, and NeoForge from one codebase
- Client command registration is wired for each loader
- Mod metadata and entrypoints were updated for the forked layout

## SeedMapper integration

This fork brings SeedMapper directly into VoxelMap, including:

- Structure locating and map rendering
- Client commands for locate, highlight, source, and export
- Structure and biome locating screens
- Loot locating screens
- ESP profile and target controls
- Saved seed management
- Datapack URL and structure management
- Loot viewer search and per-result actions
- Structure icons, arrow assets, and completion markers
- Can add tracer highlights to located results and loot results

![SeedMapMenu](https://i.imgur.com/jMBkgbm.png)

## Map and overlay changes

- SeedMapper structure overlays on the minimap and world map
- Chunk overlays for explored chunks, newer-new chunks, liquid exploit tracking, and block update exploit tracking
- Portal marker controls for Nether, End, and gateway markers
- Highlight tracer rendering for highlighted world points
- World map popup and waypoint interaction fixes, including delete confirmation behavior

#### Options menu
![ChunkOptions](https://i.imgur.com/jlwfn9V.png)

## UI and settings

The minimap options now include a dedicated SeedMapper tab.

General settings page 2 includes:

- Portal detection toggles
- Highlight tracer controls

The SeedMapper tab includes:

- Structure overlay toggle
- Lootable-structures-only toggle
- ESP target input
- ESP chunk radius input
- ESP fill toggle
- Datapack import and enable controls
- Loot viewer launcher

## Persistence

- Seed, ESP, datapack, and completion state are saved through the VoxelMap settings system
- Tracer settings persist alongside other map configuration
- World and server specific behavior is preserved

## SeedMapper screens

The SeedMapper tab provides access to:

- `Locate structure`
- `Locate biome`
- `Locate loot`
- `Loot viewer`
- `Run ESP highlight`
- `Run ore vein ESP`
- `Run canyon ESP`
- `Run cave ESP`
- `Run terrain ESP`
- `Clear ESP`
- `Datapack settings`
- `Saved seeds`

#### Locate structures
![LocateStructure](https://i.imgur.com/niui9AN.png)

#### ESP settings
![ESPSettings](https://i.imgur.com/3QQgUH6.png)

## SeedMapper commands

All commands are handled locally on the client.

- `/seedmap help`
- `/seedmap seed <seed>`
- `/seedmap locate structure <feature_id>`
- `/seedmap locate biome <biome_name>`
- `/seedmap locate orevein <iron|copper>`
- `/seedmap locate slime`
- `/seedmap locate loot <text>`
- `/seedmap highlight ore <block> [chunks]`
- `/seedmap highlight orevein [chunks]`
- `/seedmap highlight clear`
- `/seedmap export`

Alias:

- `/sm ...`

#### Terrain highlighting
![TerrainESP](https://i.imgur.com/XvwiaI3.png)

### Ore highlighting
![HardcOre](https://i.imgur.com/rEWn5PP.png)

## World map

- Right-click waypoint menus were updated so delete confirmation behaves correctly
- SeedMapper markers, loot actions, and completion state are integrated into the map popup flow
- Visible map bounds can be exported through SeedMapper
- Able to view any area of the map by entering its coordinates, input is revealed when clicking on current coordinates
- Can recenter the map to the player in the context menu
- Able to see chunk trial overlay 
- Deep zoom-out support on the large map (extended minimum zoom range)
- Performance mode for far zoom with configurable threshold
- Performance mode can optionally keep waypoints visible (`Waypoints In Performance Mode`)
- Explored chunk line rendering supports `Solid Chunk Line Mode`
- Explored chunk line thickness can be tuned with `Chunk Line Thickness`
- Clicking the `SeedMapper` title on the large map toggles all SeedMapper locations on/off
- Active zoom level can be shown while zooming

#### SeedMapper integration
![LargeMap](https://i.imgur.com/qDZdMvs.png)

#### Marking Nether portal as complete
![MarkComplete](https://i.imgur.com/F6f8TF6.png)

#### View other coordinates
![View](https://i.imgur.com/q0XYZRF.png)

## Minimap

- SeedMapper structure icons can render on the minimap
- Portal markers and waypoint rendering were expanded
- Display new chunks
- Added support for displaying and clearing explored chunks directly

#### New chunk detection and structure detection
![NewChunks](https://i.imgur.com/oggJc4d.png)

#### Explored chunks (breadcrumbs) and portal detection
![ExploredChunks+PortalDetection](https://i.imgur.com/c1Q41xy.png)

## Loot viewer

- Search the integrated SeedMapper loot database by item name, ID, enchantment, or NBT terms
- Toggle per-result highlight and waypoint actions directly from the results list
- Copy coordinates from any loot result

![LocateLoot](https://i.imgur.com/h7JtYR9.png)

## Build outputs

Root build output jars are generated under:

- `build/libs/voxelmap-fabric-26.1-1.16.5.jar`
- `build/libs/voxelmap-forge-26.1-1.16.5.jar`
- `build/libs/voxelmap-neoforge-26.1-1.16.5.jar`

## Notes

- This is not intended to be an upstream-equivalent release
- Some legacy localization keys may still remain after UI changes or removals
- The project prioritizes SeedMapper integration and UI consistency over strict upstream parity
