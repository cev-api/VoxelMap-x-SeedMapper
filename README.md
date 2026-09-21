# VoxelMap x SeedMapper by CevAPI

![LOGO](https://i.imgur.com/D4uG0Fd.png)

VoxelMap x SeedMapper is a heavily modified fork of [VoxelMap Updated](https://github.com/fantahund/VoxelMap) that integrates SeedMapper directly into the client and extends VoxelMap with advanced chunk overlays, data sharing, and world-map tooling. Perfect for base and structure loot hunting!

## What This Fork Adds

### SeedMapper Core
- Integrated SeedMapper into the client (no external mod workflow required).
- Added locating for structures, biomes, slime chunks, ore veins, terrain, caves, canyons, and loot.
- Added mineshaft, desert well, amethyst geode, canyon, nether fossil, and 26.3 abandoned camp locating.
- Added infested ore (silverfish blocks) to ore highlighting.
- Added world-map and minimap marker rendering for SeedMapper results.
- Added completion tracking for located targets.
- Added saved seeds, manual seed input, and per-world/per-server SeedMapper state.
- Added per-world/per-server custom structure salts for datapack and modded structures.
- Added a buried-treasure cluster finder that scans the world border for rare multi-treasure formations.
- Added Trial Chambers vault loot prediction, with normal and ominous reward tables.
- Added bundled cubiomes support ([SeedMapper's Fork](https://github.com/xpple/cubiomes)).

### Entity Display & Markers
- Added live loaded-chunk detection for containers, workstations, redstone components, spawners, and trial spawners on both the minimap and fullscreen world map.
- Container filters include single chests, double chests, trapped chests, ender chests, shulker boxes, barrels, hoppers, dispensers, droppers, brewing stands, and crafters.
- Each category can be enabled independently, with a `...` submenu for choosing individual block types.
- Added Elytra Detection: End-ship markers receive a red slash when the item frame no longer contains an elytra, including after a player removes it.
- Added optional marker clustering: matching nearby markers collapse to one Minecraft icon with a white count, then separate again while zooming in.
- Added optional Marker Persistence. Detected markers are stored per server and dimension, restored after reconnecting, and updated when their chunk is rescanned.
- Added live block/chunk update hooks so loaded entities update without waiting for a full render-distance sweep.

![Entities](https://i.imgur.com/ijTzp7d.png)

#### Map Icon Scaling
- Added separate `Map Entities Scale` and `Minimap Entities Scale` controls for portals, end portals, end gateways, containers, workstations, redstone, and spawners.
- Minimap entity scale defaults to `0.6x` and is capped at `1.2x`; fullscreen map entity scale remains independent.
- Added separate SeedMapper structure-icon scale controls for the minimap and fullscreen world map under SeedMapper's World and Structures settings.

### SeedMap
![SeedMap](https://i.imgur.com/cgqdPWi.jpeg)

#### SeedMapper Menu
![SeedMapMenu](https://i.imgur.com/CBPfP6z.png)

#### Locate Structures
![LocateStructure](https://i.imgur.com/niui9AN.png)

### SeedMapper Commands
- Added local command roots: `/seedmap`, `/sm`, `/voxelmap`, `/vmap`.
- Added locate, highlight, vault, treasure-cluster, and source-chain command support.
- Added a standalone SeedMap screen and a buried-treasure cluster search.

Common commands:
- `/seedmap help`
- `/seedmap seed <seed> [--structureSalt <structure>=<salt> ...]`
- `/seedmap version [auto|supported version]` (choose the cubiomes Minecraft version; `auto` follows the client)
- `/seedmap map`
- `/seedmap locate structure <feature_id>`
- `/seedmap locate treasurecluster`
- `/seedmap locate biome <biome_name>`
- `/seedmap locate orevein <iron|copper>`
- `/seedmap locate slime`
- `/seedmap locate loot <text>`
- `/seedmap vault predict [offset] [ominous] [amount]`
- `/seedmap highlight ore <block> [chunks]`
- `/seedmap highlight orevein [chunks]`
- `/seedmap highlight terrain [chunks]`
- `/seedmap highlight surface [chunks]`
- `/seedmap highlight canyon [chunks]`
- `/seedmap highlight cave [chunks]`
- `/seedmap highlight clear`
- `/seedmap chunkanalysis scan [radius]` (defaults to a 9x9 loaded-chunk comparison)
- `/seedmap chunkanalysis ghost [on|off]`
- `/seedmap chunkanalysis <status|clear>`
- `/seedmap export [visible|radius <blocks>|area <x> <z> <radius>]`
- `/seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...`

### ChunkAnalysis
- Generates vanilla `ProtoChunk`s in memory from the configured seed and current vanilla dimension, through structures and features, without saving chunk data.
- Conservatively compares seed-derived block types with loaded multiplayer chunks: red is expected-but-missing, blue is unexpected, and yellow is a changed block type.
- Ignores exact state properties, fluids, and randomly ticking blocks because normal simulation can change them without player input.
- High-confidence filtering also removes vegetation/decorative noise, unexpected natural terrain, and ambiguous swaps between natural terrain materials.
- Optional player-shaped void detection performs a fast connected-component morphology pass and promotes likely tunnels, shafts, stairs, rooms, and excavations to deep red.
- `/seedmap chunkanalysis voids [radius]` skips biome decoration/features and compares only the terrain/surface/carver baseline for a substantially faster void-only scan.
- High-confidence filtering tracks decoration-stage writes separately from stable terrain, so arbitrary replacements in seed-derived terrain are detected without a block whitelist; chunks with strongly bidirectional cave disagreement are reported and filtered as incompatible baselines.
- Uses transparent tinted block-model ghosts or optional solid ESP fills, with fair sampling across the scanned chunks and configurable performance limits.

### ESP, Tracing, and Loot Workflow
- Added ESP rendering for blocks, ore veins, caves, canyons, and terrain.
- Added surface ESP, which highlights only the topmost predicted block of each column.
- Added terrain ESP support for the Nether and End instead of overworld-only.
- Added configurable ESP style profiles (fill/outline/color/alpha/timeout behavior).
- Added highlight/tracer workflow for located structures and loot results.
- Added auto-hide for highlights when near a target.
- Added integrated loot viewer with search (name, id, enchantments, NBT-like terms).
- Added loot viewing for Trial Chambers, Ancient Cities, and Trail Ruins, which previously could not be opened.
- Added vault loot prediction directly from Trial Chambers markers on the world map.
- Added loot-table retention so container loot can still be identified after it has been generated.
- Fixed ESP and chunk-analysis overlays being hidden by terrain when viewed from above.

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
- Added custom structure salt support, applied to world-map markers, locator queries, and loot lookups.
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
- Added buried-treasure cluster markers with treasure counts on the fullscreen map.
- Added a standalone SeedMap screen with drag-to-pan, scroll-to-zoom, and coordinate inputs.
- Added a Biome Sample Y control so seed-map biomes are sampled at the height you choose.
- Added deeper SeedMap zoom in and out, with shift for faster steps.
- Added touchpad/trackpad pinch zoom on the SeedMap and fullscreen map.
- Added marker context actions (completion toggles, loot actions, waypoint interactions).
- Added configurable transport shortcuts for teleport, flight, pathing, and other client/server commands.
- Added transport shortcut controls for excluding Y coordinates and showing all shortcuts in the main menu.
- Transport shortcut names, commands, visibility, and client-command flags persist across launches.
- Added persistent world-map plot lines with editing, duplication, deletion, color, thickness, and cross-dimension support.
- Added visible-area export support.
- Added coordinate recentering/editing and player recenter action.
- Added deep zoom-out and performance-mode behavior improvements.
- Added option to keep waypoints visible in world-map performance mode.
- Added optional zoom level display while zooming.
- Improved extreme-zoom texture processing and rendering.
- Improved waypoint layering, depth handling, label ordering, icon rendering, and highlighted-waypoint alpha.
- Improved world-map cache location handling and chunk readiness checks.
- Added cache write/decompression safeguards; stale cache data may need to be cleared after upgrading.

#### SeedMapper Integration
![LargeMap](https://i.imgur.com/8ryURxr.png)

#### Mark Complete Example
![MarkComplete](https://i.imgur.com/F6f8TF6.png)

#### Coordinate View
![View](https://i.imgur.com/q0XYZRF.png)

### Plot Lines & Chunk Trials
![Example](https://i.imgur.com/i3ssjxy.png)

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
![ChunkOptions](https://i.imgur.com/Q4gLJTr.png)

### ChunkSync/Sharing
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

![ChunkSync](https://i.imgur.com/nq5VNlH.png)

### UI and Settings
- Added dedicated SeedMapper options tab and related screens.
- Added screens for locator, loot viewer, ESP profiles, datapacks, saved seeds/maps, and the standalone SeedMap.
- Added SeedMapper settings for custom structure salts, treasure cluster scans, and Biome Sample Y.
- Added Chunk management UI
- Added ChunkSync management UI (passphrase/share/receive/manual import/export/player layers/status).
- Updated branding to `VoxelMap x SeedMapper by CevAPI`.
- Added/updated localization keys for SeedMapper and chunk-sync features.

![UI](https://i.imgur.com/rwCntHC.png)

### Persistence and Compatibility
- SeedMapper state, datapack state, ESP settings, and completion state persist via VoxelMap settings.
- Custom structure salts persist per server and world alongside saved seeds.
- Buried-treasure cluster results and SeedMapper query caches are reused per seed instead of being rebuilt.
- Generated SeedMapper caches release their memory under pressure instead of holding it for the whole session.
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
- Added cubiomes native extraction into the game directory, with a fallback to the launcher natives directory.
- Added SeedMapper error reporting when the cubiomes native library cannot be loaded, instead of a blank seed map.
- Updated metadata and fork versioning for this project.
- Changed output artifact naming to `voxelmap-x-seedmapper_<loader>_v<version>.jar`.
- Added Fabric Mod Menu configuration-screen integration.
- Added Cubiomes Minecraft-version selection with an automatic client-version mode.

Example outputs:
- `build/libs/voxelmap-x-seedmapper_fabric_v0.10.jar`
- `build/libs/voxelmap-x-seedmapper_forge_v0.10.jar`
- `build/libs/voxelmap-x-seedmapper_neoforge_v0.10.jar`

## Platform Support
- Fabric
- Forge (build available; runtime testing recommended)
- NeoForge (build available; runtime testing recommended)

## Notes
- This fork is feature-focused and not intended as strict upstream parity.
- Some legacy localization keys may still exist after UI refactors.
- On Linux, the bundled `libcubiomes.so` requires glibc 2.34 or newer (Ubuntu 22.04+, Debian 12+).
- Existing world-map cache files can contain stale or damaged region data; remove `.minecraft/voxelmap/cache` once when upgrading if visual artifacts persist.
- Active development is ongoing; occasional regressions are still possible.
