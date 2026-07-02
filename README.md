# VoxelMap x SeedMapper by CevAPI

![LOGO](https://i.imgur.com/D4uG0Fd.png)

VoxelMap x SeedMapper is a heavily modified fork of [VoxelMap Updated](https://github.com/fantahund/VoxelMap) that integrates SeedMapper directly into the client and extends VoxelMap with advanced chunk overlays, data sharing, and world-map tooling. Perfect for base and structure loot hunting!

## What This Fork Adds

### SeedMapper Core
- Integrated SeedMapper into the client (no external mod workflow required).
- Added locating for structures, biomes, slime chunks, ore veins, terrain, caves, canyons, and loot.
- Added world-map and minimap marker rendering for SeedMapper results.
- Added completion tracking for located targets.
- Added saved seeds, manual seed input, and per-world/per-server SeedMapper state.
- Added bundled cubiomes support ([SeedMapper's Fork](https://github.com/xpple/cubiomes)).

### SeedMap
![SeedMap](https://i.imgur.com/XikKPVK.png)

#### SeedMapper Menu
![SeedMapMenu](https://i.imgur.com/iBHVqbI.png)

#### Locate Structures
![LocateStructure](https://i.imgur.com/niui9AN.png)

### SeedMapper Commands
- Added local command roots: `/seedmap`, `/sm`, `/voxelmap`, `/vmap`.
- Added locate, highlight, export, and source-chain command support.

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

### ESP, Tracing, and Loot Workflow
- Added ESP rendering for blocks, ore veins, caves, canyons, and terrain.
- Added configurable ESP style profiles (fill/outline/color/alpha/timeout behavior).
- Added highlight/tracer workflow for located structures and loot results.
- Added auto-hide for highlights when near a target.
- Added integrated loot viewer with search (name, id, enchantments, NBT-like terms).

#### ESP Settings
![ESPSettings](https://i.imgur.com/3QQgUH6.png)

#### Terrain Highlighting
![TerrainESP](https://i.imgur.com/XvwiaI3.png)

#### Ore Highlighting
![HardcOre](https://i.imgur.com/rEWn5PP.png)

#### Loot Viewer
![LocateLoot](https://i.imgur.com/h7JtYR9.png)

### Datapack Structure Support
- Added datapack import for SeedMapper structures.
- Added datapack URL/cache path/autoload/enable controls.
- Added icon style and color scheme controls.
- Added per-world datapack structure enable/disable persistence.
- Added datapack-located marker persistence.
- Improved world map loading state behavior so SeedMapper loading text clears reliably when hidden/disabled and after exact results resolve.

### Baritone Integration
- Added custom Baritone integration via `BaritoneHelper`.
- Added automatic vein miner for ores — detects exposed ore veins and leverages Baritone to strip-mine them.
- Requires [Baritone](https://github.com/cabaletta/baritone) to be installed separately.

### CPU Renderer
- Added a full CPU-based radar renderer as an alternative to the GPU pipeline.
- Toggleable via a new option in settings (`Enable CPU Rendering`).
- Includes block helmet rendering support on the CPU path.
- Enables full VoxelMap compatibility with Vulkan and other renderer-modifying mods.

### World Map Improvements
- Added SeedMapper marker icons and loot markers on the fullscreen map.
- Added marker context actions (completion toggles, loot actions, waypoint interactions).
- Added visible-area export support.
- Added coordinate recentering/editing and player recenter action.
- Added deep zoom-out and performance-mode behavior improvements.
- Added option to keep waypoints visible in world-map performance mode.
- Added optional zoom level display while zooming.
- Improved extreme-zoom texture processing and rendering.

#### SeedMapper Integration
![LargeMap](https://i.imgur.com/qDZdMvs.png)

#### Mark Complete Example
![MarkComplete](https://i.imgur.com/F6f8TF6.png)

#### Coordinate View
![View](https://i.imgur.com/q0XYZRF.png)

### Minimap and Chunk Overlay Improvements
- Added SeedMapper marker rendering on the minimap.
- Added Newer New Chunks overlay support on minimap and world map (see New Chunks System above).
- Added chunk grid/slime chunk options.
- Improved chunk line rendering (solid mode and thickness controls).
- Added scoreboard positioning option below minimap.

![NewChunks](https://i.imgur.com/oggJc4d.png)

### Portal and Waypoint Enhancements
- Added portal marker overlays for Nether portals, End portals, and End beacons.
- Added automatic portal recording and improved portal detection.
- Added waypoint import from Xaero's Minimap and Wurst.
- Added multi-select waypoint handling and optional delete confirmation.
- Added expanded waypoint compass and label placement options.
- Added dimension filtering and copy-to-clipboard in share flows.

![ExploredChunks+PortalDetection](https://i.imgur.com/c1Q41xy.png)

![WayPoints](https://i.imgur.com/w2s7jOl.png)

### New Chunks System
- Added a dedicated New Chunks feature set with its own options category/screen.
- Added detection and rendering for:
    - Newly generated chunks
    - Explored chunk history
    - Liquid-exploit chunk signals
    - Block-update exploit chunk signals
- Added minimap and fullscreen world-map visualization for New Chunks layers.
- Added persistent chunk-history storage so detected states survive restarts.
- Added chunk overlay styling controls including line mode/thickness and visibility toggles.

#### Chunk Options
![ChunkOptions](https://i.imgur.com/DmX10jt.png)

### ChunkSync
ChunkSync lets you securely share chunk-layer data with other players.

#### One-Time Setup
- `/chunksync key <passphrase>`
- `/chunksync host <litterbox|file.io>`

#### Share
- `/chunksync share`
- `/chunksync share to <name>`

    - Exports your chunk-share bundle.
    - Encrypts it with your configured passphrase.
    - Uploads it to the selected host.
    - Posts (or whispers) an encoded import token.

#### Receive
- `/chunksync get <code>`
- `/chunksync get <code> as <name>`

    - Default `get` merges into your own layer.
    - `as <name>` imports as a separate colored player layer.
    - Chat prompt import actions use the separate-layer flow automatically.

#### Manual File Transfer
- `/chunksync export [name]` writes `voxelmap/chunk_share/<name>/` (folder or zip workflow supported).
- `/chunksync import [name] [as <name>]` imports a local folder or `.zip` bundle.

#### Manage Imported Layers
- `/chunksync players`
- `/chunksync remove <name>`

![ChunkSync](https://i.imgur.com/N43aPyV.png)

### UI and Settings
- Added dedicated SeedMapper options tab and related screens.
- Added screens for locator, loot viewer, ESP profiles, datapacks, and saved seeds/maps.
- Added Chunk management UI
- Added ChunkSync management UI (passphrase/share/receive/manual import/export/player layers/status).
- Updated branding to `VoxelMap x SeedMapper by CevAPI`.
- Added/updated localization keys for SeedMapper and chunk-sync features.

![UI](https://i.imgur.com/UOuLcFS.png)

### Persistence and Compatibility
- SeedMapper state, datapack state, ESP settings, and completion state persist via VoxelMap settings.
- Explored chunks and portal markers persist per world/server context.
- Added compatibility helpers for Wurst waypoint data.
- Added rendering pipeline/types and mixin integrations needed by new overlays.

### Update Checking
This fork includes an in-client update checker/notification system.

Common commands:
- `/voxelmap updatechecker status`
- `/voxelmap updatechecker off`
- `/voxelmap updatechecker on`
- `/voxelmap updatechecker toggle`
- `/voxelmap updatechecker check`

Quick disable:
- Run `/voxelmap updatechecker off` to stop update notifications.

### Loader and Build Changes
- Added SeedMapper/ChunkSync client command registration for Fabric, Forge, and NeoForge.
- Updated metadata and fork versioning for this project.
- Changed output artifact naming to `voxelmap-x-seedmapper_<loader>_v<version>.jar`.

Example outputs:
- `build/libs/voxelmap-x-seedmapper_fabric_v0.x.jar`
- `build/libs/voxelmap-x-seedmapper_forge_v0.x.jar`
- `build/libs/voxelmap-x-seedmapper_neoforge_v0.0x.jar`

## Platform Support
- Fabric
- Forge (Needs Testing)
- NeoForge (Needs Testing)

## Notes
- This fork is feature-focused and not intended as strict upstream parity.
- Some legacy localization keys may still exist after UI refactors.
- Active development is ongoing; occasional regressions are still possible.
