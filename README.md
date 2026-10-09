# VoxelMap x SeedMapper by CevAPI

![LOGO](https://i.imgur.com/D4uG0Fd.png)

VoxelMap x SeedMapper is a heavily modified fork of [VoxelMap Updated](https://github.com/fantahund/VoxelMap) that integrates SeedMapper directly into the client and extends VoxelMap with advanced chunk overlays, data sharing, and world-map tooling. Perfect for base and structure loot hunting!

![GIF](https://i.imgur.com/Yi7C2ee.gif)

## Current Features

### SeedMapper Core
- Integrates SeedMapper into the client (no external mod workflow required).
- Supports locating structures, biomes, slime chunks, ore veins, terrain, caves, canyons, and loot.
- Supports mineshaft, desert well, amethyst geode, canyon, nether fossil, and 26.3 abandoned camp locating.
- Includes infested ore (silverfish blocks) in ore highlighting.
- Renders SeedMapper results on the world map and minimap.
- Tracks completion state for located targets.
- Provides saved seeds, manual seed input, and per-world/per-server SeedMapper state.
- Stores custom structure salts per world and server for datapack and modded structures.
- Provides a buried-treasure cluster finder that scans the world border for rare multi-treasure formations.
- Provides Trial Chambers vault loot prediction with normal and ominous reward tables.
- Bundles cubiomes support through [SeedMapper's Fork](https://github.com/xpple/cubiomes).

### Entity Display & Markers
- Detects loaded-chunk containers, workstations, redstone components, spawners, and trial spawners on both the minimap and fullscreen world map.
- Container filters include single chests, double chests, trapped chests, ender chests, shulker boxes, barrels, hoppers, dispensers, droppers, brewing stands, and crafters.
- Each category can be enabled independently, with a `...` submenu for choosing individual block types.
- Marks End-ship markers with a red slash when their item frame no longer contains an elytra, including after a player removes it.
- Supports optional marker clustering: matching nearby markers collapse to one Minecraft icon with a white count and separate again while zooming in.
- Persists detected markers per server and dimension, restores them after reconnecting, and updates them when their chunk is rescanned.
- Updates loaded entities through live block/chunk hooks without waiting for a full render-distance sweep.

![Entities](https://i.imgur.com/ijTzp7d.png)

#### Map Icon Scaling
- Provides separate `Map Entities Scale` and `Minimap Entities Scale` controls for portals, end portals, end gateways, containers, workstations, redstone, and spawners.
- Minimap entity scale defaults to `0.6x` and is capped at `1.2x`; fullscreen map entity scale remains independent.
- Provides separate SeedMapper structure-icon scale controls for the minimap and fullscreen world map under SeedMapper's World and Structures settings.

### SeedMap

- Predicts biomes and terrain from the configured seed, independently of explored terrain data.
- Uses world-aligned sampling across zoom levels, with individual-block biome sampling at close zooms and multiple samples for coarse texels.
- Gives the visible map priority over off-screen padding when allocating preview resolution, so dragging does not lower the requested detail level.
- Shows the biome preview first, then refines terrain shading on a bounded, interpolated height grid. SeedMap terrain shading is a prediction rather than a full block-by-block world render.
- Provides **Preview Resolution** up to **4096** under **World Map → Seed Map**, alongside preview cache, padding, terrain-style, and update-while-moving controls. Existing saved resolution values are preserved.
- Keeps cursor biome lookups asynchronous and cancels obsolete preview jobs when the map closes or its context changes.

![SeedMap](https://i.imgur.com/cgqdPWi.jpeg)

#### SeedMapper Menu
![SeedMapMenu](https://i.imgur.com/CBPfP6z.png)

#### Locate Structures
![LocateStructure](https://i.imgur.com/niui9AN.png)

### SeedMapper Commands
- Exposes local command roots: `/seedmap`, `/sm`, `/voxelmap`, `/vmap`.
- Supports locate, highlight, vault, treasure-cluster, and source-chain commands.
- Provides a standalone SeedMap screen and a buried-treasure cluster search.

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
- `/seedmap chunkanalysis esp ore <block> [chunks]`
- `/seedmap chunkanalysis ore <block> [chunks]`
- `/seedmap chunkanalysis voids [radius]`
- `/seedmap chunkanalysis audit [radius]`
- `/seedmap chunkanalysis unexpected [radius]`
- `/seedmap chunkanalysis continuous <off|voids|audit|both>`
- `/seedmap chunkanalysis clear`
- `/seedmap chunkanalysis status`
- `/seedmap chunkanalysis ghost [on|off]`
- `/seedmap chunkanalysis <status|clear>`
- `/seedmap export [visible|radius <blocks>|area <x> <z> <radius>]`
- `/seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...`

### ChunkAnalysis
- Generates vanilla `ProtoChunk`s in memory from the configured seed and current vanilla dimension, through structures and features, without saving chunk data.
- Best results if client version matches server version.
- Conservatively compares seed-derived block types with loaded multiplayer chunks: red is expected-but-missing, blue is unexpected, and yellow is a changed block type.
- Ignores exact state properties, fluids, and randomly ticking blocks because normal simulation can change them without player input.
- High-confidence filtering also removes vegetation/decorative noise, unexpected natural terrain, and ambiguous swaps between natural terrain materials.
- Detects player-shaped voids with a fast connected-component morphology pass and promotes likely tunnels, shafts, stairs, rooms, and excavations to deep red.
- Provides `/seedmap chunkanalysis voids [radius]`, which skips biome decoration and features for a faster void-only scan.
- Tracks decoration-stage writes separately from stable terrain, so arbitrary replacements in seed-derived terrain are detected without a block whitelist; chunks with strongly bidirectional cave disagreement are reported and filtered as incompatible baselines.
- Uses transparent tinted block-model ghosts or optional solid ESP fills, with fair sampling across scanned chunks and configurable performance limits.
- Supports `scan`, `voids`, `audit`, `unexpected`, `continuous`, `clear`, `status`, and `ghost` modes from both commands and the ChunkAnalysis settings screen.
- Provides vanilla-worldgen Ore ESP through `/seedmap chunkanalysis esp ore <block> [chunks]` (or `/seedmap chunkanalysis ore <block> [chunks]`).
- Generates Ore ESP positions from ChunkAnalysis's in-memory vanilla `ProtoChunk`s rather than SeedMapper's Cubiomes ore-placement path.
- Provides Ore ESP target and chunk-radius controls with autocomplete for diamond, iron, gold, emerald, copper, coal, lapis, redstone, Nether ores, ancient debris, and infested blocks.
- Provides a dedicated Ore ESP color in the ChunkAnalysis color options.
- Able to find tunnels, holes, stairs faster than traditional "TunnelHoleStairESP" hacks in modded clients.

![ChunkAnal](https://i.imgur.com/d6iBiUF.jpeg)
![Interesting](https://i.imgur.com/zTJ2jKq.jpeg)
![Everything](https://i.imgur.com/s7bmNVt.jpeg)

#### ChunkAnalysis ESP
- SeedMapper predicts ore placement with Cubiomes. Chunk Analysis regenerates the chunk using Minecraft’s own world-generation code, then scans the generated result for the selected ore.
- Differences can depend on MC/Client version mismatches or anti-xray implementation. One may benefit you over the other.
- Blue is ChunkAnalysis ESP and Red is SeedMapper's ESP. 
![Blue](https://i.imgur.com/OL7SdPC.jpeg)
![Red](https://i.imgur.com/6pEXKWl.jpeg)

### SeedMapper ESP, Tracing, and Loot Workflow
- Renders ESP for blocks, ore veins, caves, canyons, and terrain.
- Provides surface ESP, which highlights only the topmost predicted block of each column.
- Supports terrain ESP in the Nether and End as well as the Overworld.
- Provides configurable ESP style profiles for fill, outline, color, alpha, and timeout behavior.
- Provides a highlight/tracer workflow for located structures and loot results.
- Hides highlights automatically when the player is near a target.
- Provides an integrated loot viewer with search by name, ID, enchantments, and NBT-like terms.
- Opens loot views for Trial Chambers, Ancient Cities, and Trail Ruins.
- Provides vault loot prediction directly from Trial Chambers markers on the world map.
- Retains loot tables so container loot remains identifiable after it has been generated.
- Keeps ESP and ChunkAnalysis overlays visible above terrain when viewed from above.
- Able to ignore checking against the world which may help against anti-xray.

#### ESP Settings
![ESPSettings](https://i.imgur.com/3QQgUH6.png)

#### Terrain Highlighting
![TerrainESP](https://i.imgur.com/XvwiaI3.png)

#### Ore Highlighting
![HardcOre](https://i.imgur.com/rEWn5PP.png)

#### Loot Viewer
![LocateLoot](https://i.imgur.com/h7JtYR9.png)

### Datapack Structure Support
- Imports datapack structures for SeedMapper.
- Applies custom structure salts to world-map markers, locator queries, and loot lookups.
- Provides datapack URL, cache path, autoload, and enable controls.
- Provides icon style and color scheme controls.
- Persists datapack structure enable/disable state per world.
- Persists markers located from datapacks.
- Clears SeedMapper loading text reliably when the map is hidden or disabled and after exact results resolve.

### Baritone Integration
- Integrates with Baritone through `BaritoneHelper`.
- Provides an automatic ore vein miner that detects exposed ore veins and uses Baritone to strip-mine them.
- Requires [Baritone](https://github.com/cabaletta/baritone) to be installed separately.

### CPU Renderer
- Provides a full CPU-based radar renderer as an alternative to the GPU pipeline.
- Makes CPU rendering available through the `Enable CPU Rendering` setting.
- Supports block-helmet rendering on the CPU path.
- Provides VoxelMap compatibility with Vulkan and other renderer-modifying mods.

### Rendering and Compatibility
- Routes minimap and world-map rendering through Iris-compatible helpers so minimap text, icons, and mob icons remain visible with Iris shaderpacks.
- Uses Geckolib-backed entity rendering for compatible mob icons, including armor and entity variants.
- Handles retina display scaling for world-map rendering and coordinates.
- Rebuilds resource-backed map, icon, and entity data correctly after resource reloads.
- Keeps rendering pipelines and overlay depth ordering compatible with the ChunkAnalysis, ESP, entity, and world-map layers.

### World Map Improvements
- Renders SeedMapper marker icons and loot markers on the fullscreen map.
- Renders buried-treasure cluster markers with treasure counts on the fullscreen map.
- Provides a standalone SeedMap screen with drag-to-pan, scroll-to-zoom, and coordinate inputs.
- Provides a Biome Sample Y control so seed-map biomes are sampled at the selected height.
- Supports deeper SeedMap zoom in and out, with Shift for faster steps.
- Supports touchpad/trackpad pinch zoom on the SeedMap and fullscreen map.
- Provides marker context actions for completion toggles, loot actions, and waypoint interactions.
- Provides configurable transport shortcuts for teleport, flight, pathing, and other client/server commands.
- Provides transport shortcut controls for excluding Y coordinates and showing all shortcuts in the main menu.
- Transport shortcut names, commands, visibility, and client-command flags persist across launches.
- Persists world-map plot lines with editing, duplication, deletion, color, thickness, and cross-dimension support.
- Supports visible-area export.
- Provides coordinate recentering/editing and a player recenter action.
- Handles deep zoom-out and performance mode with improved texture processing and rendering.
- Keeps waypoints visible in world-map performance mode when selected.
- Displays the zoom ratio and blocks per pixel in the fullscreen map header.
- Provides improved waypoint layering, depth handling, label ordering, icon rendering, and highlighted-waypoint alpha.
- Handles world-map cache locations and chunk readiness checks safely.
- Protects cache writes and decompression; stale cache data may require clearing after an upgrade.
- Handles world-map input for right-click actions, coordinate editing, autocomplete, and resizing without losing screen values.
- Supports player and dimension-aware world-map state, including custom-server dimensions and aliases.

#### Detail, Visibility, and Performance

Fullscreen map detail, visibility, rendering, and zoom controls are in the sidebar options screen under **World Map**.

- **Detail** provides **Full Detail** and **Balanced** presets, independent chunk-trail and new/old-chunk resolutions, explored-terrain resolution, and automatic layer hiding controls. Full Detail keeps all layers visible, uses exact chunk overlays, and selects all explored-terrain pixels.
- **Visibility by Zoom** provides separate sliders for terrain, chunk trails, trail nodes, new/old chunks, waypoints, waypoint names, entities, and SeedMap biomes. Each slider ends with **Always**.
- **Rendering** offers **Automatic**, **Cached image**, and **Geometry** overlay modes, image scaling, smoothing, and optional marker reduction while moving or at high counts.
- **Zoom & Storage** controls the zoom limits, scroll steps per doubling, zoom snapping, and **Retained Zoom View Cache**. Completed views are reused for repeat zooming within a configurable memory budget; older views can be evicted.
- **Display → Show Loading Bars** enables or disables the individual loading bars at the bottom left.
- Culls off-screen overlays, caches sparse chunk queries and completed map views, and schedules preview work separately from explored-terrain loading. Geometry is split into batches to stay within the renderer's vertex limit.

Full Detail applies to explored terrain and overlays. SeedMap's preview resolution and terrain shading have their own sampling limits; higher preview resolution requires more computation and memory.

#### SeedMapper Integration
![LargeMap](https://i.imgur.com/8ryURxr.png)

#### Mark Complete Example
![MarkComplete](https://i.imgur.com/F6f8TF6.png)

#### Coordinate View
![View](https://i.imgur.com/q0XYZRF.png)

### Plot Lines & Chunk Trials
![Example](https://i.imgur.com/i3ssjxy.png)

### Minimap and Chunk Overlay Improvements
- Renders SeedMapper markers on the minimap.
- Renders Newer New Chunks overlays on the minimap and world map (see New Chunks System above).
- Provides chunk grid and slime chunk options.
- Provides solid chunk-line rendering and thickness controls.
- Provides scoreboard positioning below the minimap.
- Displays loaded players on the minimap and uses a configurable default minimap location.

![NewChunks](https://i.imgur.com/oggJc4d.png)

### Portal and Waypoint Enhancements
- Renders portal marker overlays for Nether portals, End portals, and End beacons.
- Records portals automatically and detects them across supported dimensions.
- Imports waypoints from Xaero's Minimap and Wurst.
- Supports multi-select waypoint handling and optional delete confirmation.
- Provides expanded waypoint compass and label placement options.
- Provides dimension filtering and copy-to-clipboard in share flows.
- Provides a waypoint icon picker that preserves the selected icon across the current settings flow.

![ExploredChunks+PortalDetection](https://i.imgur.com/c1Q41xy.png)

![WayPoints](https://i.imgur.com/w2s7jOl.png)

### New Chunks System
- Provides a dedicated New Chunks feature set with its own options category and screen.
- Detects and renders:
    - Newly generated chunks
    - Explored chunk history
    - Liquid-exploit chunk signals
    - Block-update exploit chunk signals
- Visualizes New Chunks layers on the minimap and fullscreen world map.
- Persists chunk-history storage so detected states survive restarts.
- Provides chunk overlay styling controls including line mode, thickness, and visibility toggles.

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
- Provides a dedicated SeedMapper options tab and related screens.
- Provides screens for locator, loot viewer, ESP profiles, datapacks, saved seeds/maps, and the standalone SeedMap.
- Provides SeedMapper settings for custom structure salts, treasure cluster scans, Biome Sample Y, and ChunkAnalysis modes.
- Provides a category-based settings sidebar with per-option tooltips, autocomplete, dependency-aware controls, and category dropdowns.
- Provides **Advanced → Servers & Seeds → Manage Server Seeds**, with search and **All servers / With seeds / Without seeds** filters.
- Lists saved Minecraft servers, VoxelMap server/world seed records, and SeedMapper overrides, with controls to copy, edit, or delete a seed. Seed edits preserve waypoint records; deleting a seed does not delete the server's map data.
- Provides Chunk management UI.
- Provides ChunkSync management UI for passphrase, sharing, receiving, manual import/export, player layers, and status.
- Uses the `VoxelMap x SeedMapper by CevAPI` branding.
- Includes localization keys for SeedMapper, ChunkSync, and the current 26.3 settings.
- Keeps server-only settings disabled outside a server and preserves input values while screens resize.

![UI](https://i.imgur.com/rwCntHC.png)

### Persistence and Compatibility
- Persists SeedMapper state, datapack state, ESP settings, and completion state through VoxelMap settings.
- Persists custom structure salts per server and world alongside saved seeds.
- Reuses buried-treasure cluster results and SeedMapper query caches per seed instead of rebuilding them.
- Releases generated SeedMapper caches under memory pressure instead of holding them for the whole session.
- Persists explored chunks and portal markers per world/server context.
- Provides compatibility helpers for Wurst waypoint data.
- Provides the rendering pipeline, overlay types, and mixin integrations required by the current overlays.
- Migrates world-map data and settings safely when legacy layouts are encountered, including recovery after cancelled world-preview migration.

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

### Loader and Build Support
- Registers SeedMapper and ChunkSync client commands for the supported loaders.
- Extracts the cubiomes native library into the game directory and falls back to the launcher natives directory when needed.
- Reports cubiomes loading failures in SeedMapper instead of showing a blank seed map.
- Targets Minecraft 26.3 with Fabric API `0.161.0+26.3` and NeoForge `26.3.0.16-beta`.
- Uses VoxelConfig as a shared library and prebuilds it in GitHub Actions for CI builds.
- Uses the current project metadata and fork versioning.
- Names build artifacts `voxelmap-x-seedmapper_<minecraft-version>_<loader>_v<version>.jar`.
- Integrates with Fabric Mod Menu for the configuration screen.
- Provides Cubiomes Minecraft-version selection with an automatic client-version mode.

Example outputs:
- `build/libs/voxelmap-x-seedmapper_26.3_fabric_v0.13.jar`
- `build/libs/voxelmap-x-seedmapper_26.3_neoforge_v0.13.jar`

## Platform Support
- Fabric for Minecraft 26.3.
- NeoForge for Minecraft 26.3.
- Forge sources remain available for future porting; Minecraft 26.3 does not have a Forge release.

## Notes
- This fork is feature-focused and not intended as strict upstream parity.
- Some legacy localization keys may still exist after UI refactors.
- On Linux, the bundled `libcubiomes.so` requires glibc 2.34 or newer (Ubuntu 22.04+, Debian 12+).
- Existing world-map cache files can contain stale or damaged region data; remove `.minecraft/voxelmap/cache` once when upgrading if visual artifacts persist.
- The project remains under active development, and occasional regressions are possible.
