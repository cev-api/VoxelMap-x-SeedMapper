package com.mamiyaotaru.voxelmap.chunkanalysis;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.slf4j.Logger;

/** Vanilla world generation resources and a bounded, non-saving chunk world. */
final class ChunkAnalysisWorldgenContext implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int STRUCTURE_REFERENCE_RADIUS = 8;

    private final CloseableResourceManager resources;
    private final RegistryAccess.Frozen registries;
    private final LevelStorageSource.LevelStorageAccess temporaryStorage;
    private final Path temporaryDirectory;

    private ChunkAnalysisWorldgenContext(CloseableResourceManager resources, RegistryAccess.Frozen registries,
                                         LevelStorageSource.LevelStorageAccess temporaryStorage, Path temporaryDirectory) {
        this.resources = resources;
        this.registries = registries;
        this.temporaryStorage = temporaryStorage;
        this.temporaryDirectory = temporaryDirectory;
    }

    static CompletableFuture<ChunkAnalysisWorldgenContext> load(Executor executor) {
        Minecraft minecraft = Minecraft.getInstance();
        CloseableResourceManager resources = new MultiPackResourceManager(
                PackType.SERVER_DATA, List.of(minecraft.getVanillaPackResources().fullResources()));
        RegistryAccess.Frozen staticAccess = RegistryLayer.createRegistryAccess().getLayer(RegistryLayer.STATIC);
        List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(resources, staticAccess);
        // The static tags must be applied, not just collected. buildUpdatedLookups only
        // decides which lookups to hand to the loader; it does not bind anything, and
        // Registry.PendingTags.lookup() is the registry itself. Without this call the
        // block registry reaches feature placement unbound, and any feature reading a
        // block tag such as minecraft:wall_corals throws when the contents are accessed.
        staticTags.forEach(Registry.PendingTags::apply);
        List<HolderLookup.RegistryLookup<?>> worldgenContext = TagLoader.buildUpdatedLookups(staticAccess, staticTags);

        return RegistryDataLoader.load(resources, worldgenContext, RegistryDataLoader.WORLD_REGISTRIES, executor)
                .thenCompose(worldgen -> {
                    List<HolderLookup.RegistryLookup<?>> dimensionContext = new ArrayList<>(worldgenContext);
                    dimensionContext.addAll(worldgen.listRegistries().toList());
                    return RegistryDataLoader.load(resources, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES, executor)
                            .thenApply(dimensions -> {
                                List<Registry<?>> all = new ArrayList<>();
                                staticAccess.registries().forEach(entry -> all.add(entry.value()));
                                worldgen.registries().forEach(entry -> all.add(entry.value()));
                                dimensions.registries().forEach(entry -> all.add(entry.value()));
                                // Structure templates contain entity NBT. Include the built-in
                                // entity registry so vanilla does not warn once per villager/cat
                                // while loading those templates in the isolated world.
                                all.add(BuiltInRegistries.ENTITY_TYPE);
                                RegistryAccess.Frozen combined = new RegistryAccess.ImmutableRegistryAccess(dedupeRegistries(all)).freeze();
                                // Bind tags for every registry in the combined access. Filtering
                                // this down to the worldgen/dimension registries leaves static
                                // registries such as minecraft:block unbound, which throws as
                                // soon as a tag is read during feature placement.
                                TagLoader.loadTagsForExistingRegistries(resources, combined)
                                        .forEach(Registry.PendingTags::apply);
                                try {
                                    Path temp = Files.createTempDirectory("voxelmap-chunk-analysis-");
                                    LevelStorageSource source = new LevelStorageSource(
                                            temp.resolve("worlds"), temp.resolve("backups"),
                                            LevelStorageSource.parseValidator(temp.resolve("allowed_symlinks.txt")),
                                            minecraft.getFixerUpper());
                                    LevelStorageSource.LevelStorageAccess access = source.createAccess("memory");
                                    return new ChunkAnalysisWorldgenContext(resources, combined, access, temp);
                                } catch (IOException exception) {
                                    throw new IllegalStateException("Could not prepare the memory-only structure loader", exception);
                                }
                            });
                }).whenComplete((ignored, failure) -> {
                    if (failure != null) resources.close();
                });
    }

    /**
     * Keeps the first registry seen for each key.
     *
     * <p>{@code ImmutableRegistryAccess} collects into a map and throws on a duplicate
     * key, so any overlap between the static, worldgen and dimension sources is dropped
     * here instead of aborting the scan.</p>
     */
    private static List<Registry<?>> dedupeRegistries(List<Registry<?>> registries) {
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> byKey = new LinkedHashMap<>();
        for (Registry<?> registry : registries) {
            Registry<?> existing = byKey.putIfAbsent(registry.key(), registry);
            if (existing != null && existing != registry) {
                LOGGER.debug("ChunkAnalysis ignored a duplicate registry for {}", registry.key().identifier());
            }
        }
        return new ArrayList<>(byKey.values());
    }

    GeneratedArea generate(long seed, ResourceKey<Level> dimension, ChunkPos center, int radius, boolean includeFeatures) {
        ResourceKey<LevelStem> stemKey = stemFor(dimension);
        LevelStem stem = registries.lookup(Registries.LEVEL_STEM)
                .flatMap(registry -> registry.get(stemKey))
                .map(Holder::value)
                .orElseGet(() -> WorldPresets.createNormalWorldDimensions(registries).get(stemKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "Vanilla normal world preset is missing dimension " + stemKey.identifier())));
        if (!(stem.generator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("ChunkAnalysis currently supports vanilla noise-based dimensions only");
        }

        DimensionType dimensionType = stem.type().value();
        LevelHeightAccessor height = LevelHeightAccessor.create(dimensionType.minY(), dimensionType.height());
        PalettedContainerFactory containers = PalettedContainerFactory.create(registries);
        RandomState randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE),
                seed, generator.generatorSettings().value());
        ChunkGeneratorStructureState structureState = generator.createState(
                registries.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed);

        int generatedRadius = radius + 1;
        // Void scans do not inspect structures or feature blocks. Avoid building the
        // expensive 8-chunk structure-reference border for that fast path; terrain
        // still gets a one-chunk neighbor border for carvers and biome lookup.
        int supportRadius = includeFeatures
                ? generatedRadius + STRUCTURE_REFERENCE_RADIUS
                : generatedRadius + 1;
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        for (int z = center.z() - supportRadius; z <= center.z() + supportRadius; z++) {
            for (int x = center.x() - supportRadius; x <= center.x() + supportRadius; x++) {
                ChunkPos pos = new ChunkPos(x, z);
                chunks.put(pos.pack(), new ProtoChunk(pos, UpgradeData.EMPTY, height, containers, null));
            }
        }

        MemoryLevel memory = new MemoryLevel(registries, dimension, dimensionType, generator, randomState, seed, chunks);
        WorldGenLevel level = memory.proxy();
        StructureManager structureManager = new StructureManager(level, new WorldOptions(seed, true, false), null);
        StructureTemplateManager templates = new StructureTemplateManager(
                resources, temporaryStorage, Minecraft.getInstance().getFixerUpper(),
                registries.lookupOrThrow(Registries.BLOCK));
        ChunkAnalysisRuntime.enter(seed, generator, templates);
        try {

        List<BoundingBox> structurePieceBounds = new ArrayList<>();
        Set<StructureStart> structureStarts = new HashSet<>();
        if (includeFeatures) {
            // Structure starts need the full reference dependency border.
            forEachChunk(center, supportRadius, chunks, chunk -> {
                generator.createStructures(registries, structureState, structureManager, chunk, templates, dimension);
                chunk.setPersistedStatus(ChunkStatus.STRUCTURE_STARTS);
            });
            chunks.values().forEach(chunk -> chunk.getAllStarts().values().forEach(start -> {
                if (start.isValid() && structureStarts.add(start)) {
                    start.getPieces().forEach(piece -> structurePieceBounds.add(piece.getBoundingBox()));
                }
            }));
        }

        List<ProtoChunk> terrainChunks = chunksInRadius(center, generatedRadius, chunks);
        if (includeFeatures) {
            terrainChunks.forEach(chunk -> {
                generator.createReferences(level, structureManager, chunk);
                chunk.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
            });
        }

        // Match the vanilla status pyramid: references precede biomes, while every carver
        // source chunk still needs a populated biome palette.
        List<ProtoChunk> biomeChunks = chunksInRadius(center, supportRadius, chunks);
        CompletableFuture.allOf(biomeChunks.stream()
                .map(chunk -> generator.createBiomes(randomState, Blender.empty(), structureManager, chunk))
                .toArray(CompletableFuture[]::new)).join();
        biomeChunks.forEach(chunk -> chunk.setPersistedStatus(ChunkStatus.BIOMES));

        // 26.3 combines density fill, material surface rules and carvers into TERRAIN.
        // Use the vanilla pipeline, including its uncached carver-biome resolver.
        for (ProtoChunk chunk : terrainChunks) {
            memory.beginGeneration(chunk);
            Set<Holder<Biome>> possibleBiomes = new HashSet<>();
            ChunkPos pos = chunk.getPos();
            for (int z = pos.z() - 1; z <= pos.z() + 1; z++) {
                for (int x = pos.x() - 1; x <= pos.x() + 1; x++) {
                    chunks.get(ChunkPos.pack(x, z)).collectBiomesInPalette(possibleBiomes);
                }
            }
            generator.buildTerrain(chunk, Blender.empty(), randomState, structureManager,
                    memory.biomeManager, null, possibleBiomes).join();
            chunk.setPersistedStatus(ChunkStatus.TERRAIN);
        }

        if (includeFeatures) {
            memory.recordFeatureWrites = true;
            try {
                forEachChunk(center, generatedRadius, chunks, chunk -> {
                    memory.beginGeneration(chunk);
                    Heightmap.primeHeightmaps(chunk, EnumSet.of(
                            Heightmap.Types.MOTION_BLOCKING,
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Heightmap.Types.OCEAN_FLOOR,
                            Heightmap.Types.WORLD_SURFACE));
                    generator.applyBiomeDecoration(level, chunk, structureManager);
                    chunk.setPersistedStatus(ChunkStatus.FEATURES);
                });
            } finally {
                memory.recordFeatureWrites = false;
            }
        }

        Map<Long, ProtoChunk> result = new HashMap<>();
        forEachChunk(center, radius, chunks, chunk -> result.put(chunk.getPos().pack(), chunk));
        Map<Long, BitSet> featureWrites = new HashMap<>();
        result.keySet().forEach(key -> {
            BitSet touched = memory.featureWrites.get(key);
            if (touched != null) featureWrites.put(key, (BitSet) touched.clone());
        });
        return new GeneratedArea(result, featureWrites, List.copyOf(structurePieceBounds),
                dimensionType.minY(), dimensionType.height());
        } finally {
            ChunkAnalysisRuntime.exit();
        }
    }


    private static void forEachChunk(ChunkPos center, int radius, Map<Long, ProtoChunk> chunks,
                                     java.util.function.Consumer<ProtoChunk> consumer) {
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                consumer.accept(chunks.get(ChunkPos.pack(x, z)));
            }
        }
    }

    private static List<ProtoChunk> chunksInRadius(ChunkPos center, int radius, Map<Long, ProtoChunk> chunks) {
        int width = radius * 2 + 1;
        List<ProtoChunk> result = new ArrayList<>(width * width);
        forEachChunk(center, radius, chunks, result::add);
        return result;
    }

    private static ResourceKey<LevelStem> stemFor(ResourceKey<Level> dimension) {
        if (dimension == Level.NETHER) return LevelStem.NETHER;
        if (dimension == Level.END) return LevelStem.END;
        if (dimension == Level.OVERWORLD) return LevelStem.OVERWORLD;
        throw new IllegalArgumentException("No vanilla generator is known for dimension " + dimension.identifier());
    }

    @Override
    public void close() {
        resources.close();
        try {
            temporaryStorage.close();
        } catch (IOException ignored) {
        }
        try (var paths = Files.walk(temporaryDirectory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
        }
    }

    record GeneratedArea(Map<Long, ProtoChunk> chunks, Map<Long, BitSet> featureWrites,
                         List<BoundingBox> structurePieceBounds, int minY, int height) {
        ProtoChunk get(ChunkPos pos) { return chunks.get(pos.pack()); }

        boolean isInsideStructurePiece(BlockPos pos) {
            for (BoundingBox bounds : structurePieceBounds) {
                if (bounds.isInside(pos)) return true;
            }
            return false;
        }

        boolean wasFeatureWritten(BlockPos pos) {
            BitSet touched = featureWrites.get(ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ())));
            if (touched == null) return false;
            int localIndex = (pos.getY() - minY) * 256 + (pos.getZ() & 15) * 16 + (pos.getX() & 15);
            return localIndex >= 0 && localIndex < height * 256 && touched.get(localIndex);
        }
    }

    /** Invocation-proxy level keeps the large server-level surface bounded to what worldgen calls. */
    private static final class MemoryLevel implements InvocationHandler {
        private static final Identifier WORLDGEN_REGION_RANDOM = Identifier.withDefaultNamespace("worldgen_region_random");
        private final RegistryAccess registries;
        private final ResourceKey<Level> dimension;
        private final DimensionType dimensionType;
        private final ChunkGenerator generator;
        private final long seed;
        private final RandomState randomState;
        private final Map<Long, ProtoChunk> chunks;
        private RandomSource random;
        private final AtomicLong subTicks = new AtomicLong();
        private final BiomeManager biomeManager;
        private final LevelData levelData;
        private final ChunkSource chunkSource;
        private final WorldBorder border = new WorldBorder();
        private final WorldGenTickAccess<Block> blockTicks;
        private final WorldGenTickAccess<Fluid> fluidTicks;
        private final Map<Long, BitSet> featureWrites = new HashMap<>();
        private boolean recordFeatureWrites;

        MemoryLevel(RegistryAccess registries, ResourceKey<Level> dimension, DimensionType dimensionType,
                    ChunkGenerator generator, RandomState randomState, long seed, Map<Long, ProtoChunk> chunks) {
            this.registries = registries;
            this.dimension = dimension;
            this.dimensionType = dimensionType;
            this.generator = generator;
            this.randomState = randomState;
            this.seed = seed;
            this.chunks = chunks;
            this.random = RandomSource.create(seed);
            this.biomeManager = new BiomeManager(this::noiseBiome, BiomeManager.obfuscateSeed(seed));
            this.levelData = (LevelData) Proxy.newProxyInstance(LevelData.class.getClassLoader(),
                    new Class<?>[]{LevelData.class}, this::invokeLevelData);
            this.chunkSource = new ChunkSource() {
                @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) { return chunks.get(ChunkPos.pack(x, z)); }
                @Override public void tick(BooleanSupplier haveTime, boolean tickChunks) { }
                @Override public String gatherStats() { return "ChunkAnalysis memory chunks"; }
                @Override public int getLoadedChunksCount() { return chunks.size(); }
                @Override public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { return null; }
                @Override public BlockGetter getLevel() { return null; }
            };
            this.blockTicks = new WorldGenTickAccess<>(pos -> chunkAt(pos).getBlockTicks());
            this.fluidTicks = new WorldGenTickAccess<>(pos -> chunkAt(pos).getFluidTicks());
        }

        WorldGenLevel proxy() {
            return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class}, this);
        }

        void beginGeneration(ChunkAccess centerChunk) {
            this.random = randomState.getOrCreateRandomFactory(WORLDGEN_REGION_RANDOM)
                    .at(centerChunk.getPos().getWorldPosition());
        }

        private Holder<Biome> noiseBiome(int x, int y, int z) {
            return generator.getBiomeSource().createUncachedResolver(randomState).getNoiseBiome(x, y, z);
        }

        private ProtoChunk chunkAt(BlockPos pos) {
            return chunks.get(ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
        }

        private ProtoChunk chunkFromArguments(Object[] args) {
            if (args != null && args.length > 0 && args[0] instanceof BlockPos pos) {
                return chunkAt(pos);
            }
            if (args != null && args.length >= 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
                return chunks.get(ChunkPos.pack(x, z));
            }
            throw new IllegalArgumentException("Unsupported getChunk overload");
        }

        private int heightFromArguments(Object[] args) {
            net.minecraft.world.level.levelgen.Heightmap.Types type =
                    (net.minecraft.world.level.levelgen.Heightmap.Types) args[0];
            int x;
            int z;
            if (args.length == 2 && args[1] instanceof BlockPos pos) {
                x = pos.getX();
                z = pos.getZ();
            } else {
                x = (int) args[1];
                z = (int) args[2];
            }
            return chunkAt(new BlockPos(x, 0, z)).getHeight(type, x & 15, z & 15) + 1;
        }

        private boolean setBlock(WorldGenLevel level, BlockPos pos, BlockState state, int flags) {
            ProtoChunk chunk = chunkAt(pos);
            if (recordFeatureWrites) {
                int localIndex = (pos.getY() - dimensionType.minY()) * 256
                        + (pos.getZ() & 15) * 16 + (pos.getX() & 15);
                if (localIndex >= 0 && localIndex < dimensionType.height() * 256) {
                    featureWrites.computeIfAbsent(chunk.getPos().pack(), ignored -> new BitSet(dimensionType.height() * 256))
                            .set(localIndex);
                }
            }
            BlockState oldState = chunk.setBlockState(pos, state, flags);
            if (oldState == null) return false;
            if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock entityBlock) {
                BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
                if (blockEntity != null) chunk.setBlockEntity(blockEntity);
            } else if (oldState.hasBlockEntity()) {
                chunk.removeBlockEntity(pos);
            }
            if ((flags & 16) == 0) {
                BlockPos postProcessPos = state.getPostProcessPos(level, pos);
                if (postProcessPos != null) chunkAt(postProcessPos).markPosForPostProcessing(postProcessPos);
            }
            return true;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "getChunk" -> chunkFromArguments(args);
                case "hasChunk" -> chunks.containsKey(ChunkPos.pack((int) args[0], (int) args[1]));
                case "getBlockState" -> chunkAt((BlockPos) args[0]).getBlockState((BlockPos) args[0]);
                case "getFluidState" -> chunkAt((BlockPos) args[0]).getFluidState((BlockPos) args[0]);
                case "getBlockEntity" -> args.length == 1
                        ? chunkAt((BlockPos) args[0]).getBlockEntity((BlockPos) args[0])
                        : InvocationHandler.invokeDefault(proxy, method, args);
                case "setBlock" -> setBlock((WorldGenLevel) proxy, (BlockPos) args[0], (BlockState) args[1], (int) args[2]);
                case "removeBlock", "destroyBlock" -> setBlock((WorldGenLevel) proxy, (BlockPos) args[0], Blocks.AIR.defaultBlockState(), 3);
                case "getChunkSource" -> chunkSource;
                case "registryAccess" -> registries;
                case "dimensionType" -> dimensionType;
                case "getSeed" -> seed;
                case "getSeaLevel" -> generator.getSeaLevel();
                case "getMinY" -> dimensionType.minY();
                case "getHeight" -> args == null || args.length == 0 ? dimensionType.height() : heightFromArguments(args);
                case "getMaxY" -> dimensionType.minY() + dimensionType.height() - 1;
                case "getBiomeManager" -> biomeManager;
                case "getUncachedNoiseBiome", "getNoiseBiome" -> generator.getBiomeSource().createUncachedResolver(randomState)
                        .getNoiseBiome((int) args[0], (int) args[1], (int) args[2]);
                case "enabledFeatures" -> FeatureFlags.DEFAULT_FLAGS;
                case "environmentAttributes" -> EnvironmentAttributeReader.EMPTY;
                case "getWorldBorder" -> border;
                case "getLevelData" -> levelData;
                case "getRandom" -> random;
                case "nextSubTickCount" -> subTicks.incrementAndGet();
                case "getBlockTicks" -> blockTicks;
                case "getFluidTicks" -> fluidTicks;
                case "getCurrentDifficultyAt" -> new DifficultyInstance(Difficulty.NORMAL, 0L, 0L, 0.0F);
                case "getServer", "getLevel", "getEntity", "getNearestPlayer" -> null;
                case "isClientSide" -> false;
                case "getSkyDarken", "getBrightness", "getRawBrightness", "getDirectSignal", "getSignal", "getControlInputSignal" -> 0;
                case "getEntities", "getEntitiesOfClass", "players" -> List.of();
                case "isStateAtPosition" -> ((Predicate<BlockState>) args[1]).test(
                        chunkAt((BlockPos) args[0]).getBlockState((BlockPos) args[0]));
                case "isFluidAtPosition" -> ((Predicate<FluidState>) args[1]).test(
                        chunkAt((BlockPos) args[0]).getFluidState((BlockPos) args[0]));
                case "addFreshEntity", "shouldTickBlocksAt" -> false;
                case "playSound", "addParticle", "levelEvent", "gameEvent", "setCurrentlyGenerating", "updateNeighborsAt" -> null;
                case "toString" -> "ChunkAnalysisMemoryLevel[" + dimension.identifier() + "]";
                default -> {
                    if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, args);
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive()) yield null;
                    if (type == boolean.class) yield false;
                    if (type == long.class) yield 0L;
                    if (type == float.class) yield 0.0F;
                    if (type == double.class) yield 0.0D;
                    yield 0;
                }
            };
        }

        private Object invokeLevelData(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getRespawnData" -> LevelData.RespawnData.DEFAULT;
                case "getDifficulty" -> Difficulty.NORMAL;
                case "isHardcore", "isDifficultyLocked" -> false;
                case "getGameTime" -> 0L;
                default -> null;
            };
        }
    }
}
