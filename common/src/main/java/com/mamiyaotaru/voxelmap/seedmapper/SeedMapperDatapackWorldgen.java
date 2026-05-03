package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class SeedMapperDatapackWorldgen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<CacheKey, LoadedDatapack> CACHE = new HashMap<>();
    private static final Method STRUCTURE_PLACEMENT_SALT = resolvePlacementSaltMethod();

    private SeedMapperDatapackWorldgen() {
    }

    static List<SeedMapperMarker> query(String datapackRootPath, long seed, int dimension, int minX, int maxX, int minZ, int maxZ, Set<String> disabledIds) {
        LoadedDatapack loaded = get(datapackRootPath, seed);
        if (loaded == null) {
            return List.of();
        }
        DimensionContext context = loaded.worldgen().getDimensionContext(dimension);
        if (context == null) {
            return List.of();
        }

        int minChunkX = Mth.floor((double) minX / 16.0D);
        int maxChunkX = Mth.floor((double) maxX / 16.0D);
        int minChunkZ = Mth.floor((double) minZ / 16.0D);
        int maxChunkZ = Mth.floor((double) maxZ / 16.0D);
        Set<String> disabled = disabledIds == null ? Set.of() : disabledIds;
        Predicate<StructureSetEntry> entryFilter = entry -> entry != null && entry.custom() && !disabled.contains(entry.id());
        ArrayList<SeedMapperMarker> markers = new ArrayList<>();

        for (CustomStructureSet set : loaded.worldgen().getStructureSetsForDimension(dimension)) {
            if (!hasEnabledEntry(set, entryFilter)) {
                continue;
            }
            StructurePlacement placement = set.placement();
            if (placement instanceof RandomSpreadStructurePlacement randomPlacement) {
                int spacing = Math.max(1, randomPlacement.spacing());
                int minRegionX = Math.floorDiv(minChunkX, spacing) - 1;
                int maxRegionX = Math.floorDiv(maxChunkX, spacing) + 1;
                int minRegionZ = Math.floorDiv(minChunkZ, spacing) - 1;
                int maxRegionZ = Math.floorDiv(maxChunkZ, spacing) + 1;
                for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                    for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                        RandomSpreadCandidate candidate = set.sampleRandomSpread(seed, regionX, regionZ);
                        if (candidate == null) {
                            continue;
                        }
                        addIfValid(loaded.worldgen(), set, context, placement, candidate.chunkPos(), candidate.random(), entryFilter, markers, minX, maxX, minZ, maxZ);
                    }
                }
                continue;
            }

            if (placement instanceof ConcentricRingsStructurePlacement ringPlacement) {
                for (ChunkPos chunkPos : context.structureState().getRingPositionsFor(ringPlacement)) {
                    WorldgenRandom random = createSelectionRandom(seed, chunkPos.x(), chunkPos.z(), placement);
                    addIfValid(loaded.worldgen(), set, context, placement, chunkPos, random, entryFilter, markers, minX, maxX, minZ, maxZ);
                }
                continue;
            }

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                    WorldgenRandom random = createSelectionRandom(seed, chunkX, chunkZ, placement);
                    addIfValid(loaded.worldgen(), set, context, placement, chunkPos, random, entryFilter, markers, minX, maxX, minZ, maxZ);
                }
            }
        }

        return markers;
    }

    static List<String> structureIds(String datapackRootPath, long seed) {
        LoadedDatapack loaded = get(datapackRootPath, seed);
        if (loaded == null) {
            return List.of();
        }
        Set<String> ids = new HashSet<>();
        for (CustomStructureSet set : loaded.sets()) {
            for (StructureSetEntry entry : set.entries()) {
                if (entry.custom()) {
                    ids.add(entry.id());
                }
            }
        }
        ArrayList<String> sorted = new ArrayList<>(ids);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private static boolean hasEnabledEntry(CustomStructureSet set, Predicate<StructureSetEntry> entryFilter) {
        for (StructureSetEntry entry : set.entries()) {
            if (entryFilter.test(entry)) {
                return true;
            }
        }
        return false;
    }

    private static void addIfValid(DatapackWorldgen worldgen, CustomStructureSet set, DimensionContext context, StructurePlacement placement, ChunkPos chunkPos,
                                   WorldgenRandom random, Predicate<StructureSetEntry> entryFilter, List<SeedMapperMarker> out,
                                   int minX, int maxX, int minZ, int maxZ) {
        if (chunkPos.x() < Mth.floor((double) minX / 16.0D) || chunkPos.x() > Mth.floor((double) maxX / 16.0D)
                || chunkPos.z() < Mth.floor((double) minZ / 16.0D) || chunkPos.z() > Mth.floor((double) maxZ / 16.0D)) {
            return;
        }
        if (!placement.isStructureChunk(context.structureState(), chunkPos.x(), chunkPos.z())) {
            return;
        }
        StructureResult result = worldgen.resolveStructure(set, context, chunkPos, random, entryFilter);
        if (result == null || !result.isPresent() || result.entry() == null || !entryFilter.test(result.entry())) {
            return;
        }
        int blockX = result.position().getX();
        int blockZ = result.position().getZ();
        if (blockX < minX || blockX > maxX || blockZ < minZ || blockZ > maxZ) {
            return;
        }
        out.add(new SeedMapperMarker(SeedMapperFeature.DATAPACK_STRUCTURE, blockX, blockZ, result.entry().id()));
    }

    private static LoadedDatapack get(String datapackRootPath, long seed) {
        if (datapackRootPath == null || datapackRootPath.isBlank()) {
            return null;
        }
        Path root;
        try {
            root = Path.of(datapackRootPath).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return null;
        }
        CacheKey key = new CacheKey(root.toString(), seed);
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) {
                return CACHE.get(key);
            }
        }
        LoadedDatapack loaded = load(root, seed);
        if (loaded != null) {
            synchronized (CACHE) {
                CACHE.put(key, loaded);
            }
        }
        return loaded;
    }

    private static LoadedDatapack load(Path datapackRoot, long seed) {
        try {
            DatapackWorldgen worldgen = DatapackWorldgen.load(datapackRoot, seed);
            List<CustomStructureSet> sets = worldgen.customStructureSets();
            if (sets.isEmpty()) {
                worldgen.close();
                return null;
            }
            return new LoadedDatapack(worldgen, sets);
        } catch (Exception e) {
            VoxelConstants.getLogger().warn("Failed to load SeedMapper datapack worldgen from " + datapackRoot, e);
            return null;
        }
    }

    private record CacheKey(String path, long seed) {
    }

    private record LoadedDatapack(DatapackWorldgen worldgen, List<CustomStructureSet> sets) {
    }

    private static final class DatapackWorldgen implements AutoCloseable {
        private final CloseableResourceManager resourceManager;
        private final RegistryAccess.Frozen registryAccess;
        private final StructureTemplateManager templateManager;
        private final LevelStorageSource.LevelStorageAccess storageAccess;
        private final long seed;
        private final List<CustomStructureSet> customStructureSets;
        private final Map<Integer, DimensionContext> dimensionContexts = new HashMap<>();
        private final Map<Integer, List<CustomStructureSet>> dimensionStructureSets = new HashMap<>();
        private final Map<String, Map<Long, StructureResult>> structureCache = new HashMap<>();

        private DatapackWorldgen(CloseableResourceManager resourceManager, RegistryAccess.Frozen registryAccess, StructureTemplateManager templateManager,
                                 LevelStorageSource.LevelStorageAccess storageAccess, long seed, List<CustomStructureSet> customStructureSets) {
            this.resourceManager = resourceManager;
            this.registryAccess = registryAccess;
            this.templateManager = templateManager;
            this.storageAccess = storageAccess;
            this.seed = seed;
            this.customStructureSets = customStructureSets;
        }

        private static DatapackWorldgen load(Path datapackRoot, long seed) throws IOException {
            PackResources vanilla = createVanillaPack();
            Path sanitized = sanitizeDatapack(datapackRoot);
            PackResources customPack = createPathPack("voxelmap_seedmapper_datapack", sanitized, PackSource.WORLD);
            Path fallbackDatapack = createTemporaryFallbackDatapack();
            PackResources fallbackPack = createPathPack("voxelmap_seedmapper_fallback", fallbackDatapack, PackSource.BUILT_IN);
            CloseableResourceManager resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla, fallbackPack, customPack));
            RegistryAccess.Frozen registryAccess = loadRegistryAccess(resourceManager);
            Set<Identifier> vanillaStructures = loadVanillaStructureIds();
            LevelStorageSource.LevelStorageAccess storageAccess = createTempStorageAccess();
            StructureTemplateManager templateManager = createTemplateManager(resourceManager, storageAccess);
            List<CustomStructureSet> sets = buildCustomStructureSets(registryAccess, vanillaStructures);
            return new DatapackWorldgen(resourceManager, registryAccess, templateManager, storageAccess, seed, sets);
        }

        private List<CustomStructureSet> customStructureSets() {
            return this.customStructureSets;
        }

        private List<CustomStructureSet> getStructureSetsForDimension(int dimensionId) {
            if (this.dimensionStructureSets.containsKey(dimensionId)) {
                List<CustomStructureSet> cached = this.dimensionStructureSets.get(dimensionId);
                return cached != null ? cached : Collections.emptyList();
            }
            DimensionContext context = getDimensionContext(dimensionId);
            if (context == null) {
                this.dimensionStructureSets.put(dimensionId, null);
                return Collections.emptyList();
            }
            Set<Holder<Biome>> possibleBiomes = context.biomeSource().possibleBiomes();
            List<CustomStructureSet> filtered = new ArrayList<>();
            for (CustomStructureSet set : this.customStructureSets) {
                boolean matchesDimension = false;
                for (StructureSetEntry entry : set.entries()) {
                    Structure structure = entry.structure().value();
                    for (Holder<Biome> biome : structure.biomes()) {
                        if (possibleBiomes.contains(biome)) {
                            matchesDimension = true;
                            break;
                        }
                    }
                    if (matchesDimension) {
                        break;
                    }
                }
                if (matchesDimension) {
                    filtered.add(set);
                }
            }
            List<CustomStructureSet> result = Collections.unmodifiableList(filtered);
            this.dimensionStructureSets.put(dimensionId, result);
            return result;
        }

        private DimensionContext getDimensionContext(int dimensionId) {
            if (this.dimensionContexts.containsKey(dimensionId)) {
                DimensionContext cached = this.dimensionContexts.get(dimensionId);
                if (cached != null) {
                    return cached;
                }
                this.dimensionContexts.remove(dimensionId);
            }
            ResourceKey<LevelStem> stemKey = stemKeyForDimension(dimensionId);
            if (stemKey == null) {
                this.dimensionContexts.put(dimensionId, null);
                return null;
            }
            Optional<Registry<LevelStem>> optionalStemRegistry = this.registryAccess.lookup(Registries.LEVEL_STEM);
            if (optionalStemRegistry.isEmpty()) {
                optionalStemRegistry = getClientLevelStemRegistry();
            }
            LevelStem stem = null;
            if (optionalStemRegistry.isPresent()) {
                Registry<LevelStem> stemRegistry = optionalStemRegistry.get();
                if (stemRegistry.containsKey(stemKey)) {
                    stem = stemRegistry.getValueOrThrow(stemKey);
                }
            }
            if (stem == null) {
                stem = resolveLevelStemFromPreset(this.registryAccess, stemKey).orElse(null);
            }
            if (stem == null) {
                this.dimensionContexts.put(dimensionId, null);
                return null;
            }
            ChunkGenerator chunkGenerator = stem.generator();
            BiomeSource biomeSource = chunkGenerator.getBiomeSource();
            RandomState randomState = createRandomState(chunkGenerator, this.registryAccess, dimensionId, this.seed);
            Registry<StructureSet> structureSetRegistry = this.registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
            ChunkGeneratorStructureState structureState = chunkGenerator.createState(structureSetRegistry, randomState, this.seed);
            structureState.ensureStructuresGenerated();
            LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(chunkGenerator.getMinY(), chunkGenerator.getGenDepth());
            DimensionContext context = new DimensionContext(dimensionId, chunkGenerator, biomeSource, randomState, structureState, heightAccessor);
            this.dimensionContexts.put(dimensionId, context);
            return context;
        }

        private StructureResult resolveStructure(CustomStructureSet set, DimensionContext context, ChunkPos chunkPos, WorldgenRandom selectionRandom, Predicate<StructureSetEntry> entryFilter) {
            Predicate<StructureSetEntry> filter = entryFilter == null ? entry -> true : entryFilter;
            String cacheKey = set.id() + ":" + context.dimensionId();
            Map<Long, StructureResult> cache = this.structureCache.computeIfAbsent(cacheKey, ignored -> new HashMap<>());
            long chunkKey = chunkPos.pack();
            StructureResult cached = cache.get(chunkKey);
            if (cached != null && (cached.entry() == null || filter.test(cached.entry()))) {
                return cached;
            }
            StructureSetEntry entry = set.selectEntry(selectionRandom, filter);
            if (entry == null) {
                cache.put(chunkKey, StructureResult.EMPTY);
                return StructureResult.EMPTY;
            }
            Structure structure = entry.structure().value();
            Predicate<Holder<Biome>> validBiome = structure.biomes()::contains;
            Structure.GenerationContext generationContext = new Structure.GenerationContext(
                    this.registryAccess,
                    context.chunkGenerator(),
                    context.biomeSource(),
                    context.randomState(),
                    this.templateManager,
                    this.seed,
                    chunkPos,
                    context.heightAccessor(),
                    validBiome
            );
            Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(generationContext);
            if (stub.isEmpty()) {
                cache.put(chunkKey, StructureResult.EMPTY);
                return StructureResult.EMPTY;
            }
            StructureResult result = new StructureResult(entry, stub.get().position());
            cache.put(chunkKey, result);
            return result;
        }

        @Override
        public void close() throws Exception {
            if (this.resourceManager != null) {
                this.resourceManager.close();
            }
            if (this.storageAccess != null) {
                this.storageAccess.close();
            }
        }
    }

    private record DimensionContext(int dimensionId, ChunkGenerator chunkGenerator, BiomeSource biomeSource, RandomState randomState,
                                    ChunkGeneratorStructureState structureState, LevelHeightAccessor heightAccessor) {
    }

    private record StructureResult(StructureSetEntry entry, net.minecraft.core.BlockPos position) {
        private static final StructureResult EMPTY = new StructureResult(null, null);

        private boolean isPresent() {
            return this.entry != null && this.position != null;
        }
    }

    private static final class CustomStructureSet {
        private final ResourceKey<StructureSet> key;
        private final StructureSet set;
        private final List<StructureSetEntry> entries;
        private final int totalWeight;

        private CustomStructureSet(ResourceKey<StructureSet> key, StructureSet set, List<StructureSetEntry> entries) {
            this.key = key;
            this.set = set;
            this.entries = Collections.unmodifiableList(entries);
            int sum = 0;
            for (StructureSetEntry entry : entries) {
                sum += entry.weight();
            }
            this.totalWeight = Math.max(sum, 0);
        }

        private String id() {
            return this.key.identifier().toString();
        }

        private StructurePlacement placement() {
            return this.set.placement();
        }

        private List<StructureSetEntry> entries() {
            return this.entries;
        }

        private StructureSetEntry selectEntry(WorldgenRandom random, Predicate<StructureSetEntry> filter) {
            if (this.totalWeight <= 0) {
                return null;
            }
            Predicate<StructureSetEntry> entryFilter = filter == null ? entry -> true : filter;
            int filteredSum = 0;
            for (StructureSetEntry entry : this.entries) {
                if (entryFilter.test(entry)) {
                    filteredSum += entry.weight();
                }
            }
            if (filteredSum <= 0) {
                return null;
            }
            int roll = random.nextInt(filteredSum);
            int accumulator = 0;
            for (StructureSetEntry entry : this.entries) {
                if (!entryFilter.test(entry)) {
                    continue;
                }
                accumulator += entry.weight();
                if (roll < accumulator) {
                    return entry;
                }
            }
            return null;
        }

        private RandomSpreadCandidate sampleRandomSpread(long seed, int regionX, int regionZ) {
            if (!(this.set.placement() instanceof RandomSpreadStructurePlacement randomPlacement)) {
                return null;
            }
            int spacing = randomPlacement.spacing();
            ChunkPos chunkPos = randomPlacement.getPotentialStructureChunk(seed, regionX * spacing, regionZ * spacing);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureWithSalt(seed, regionX, regionZ, getPlacementSalt(randomPlacement));
            return new RandomSpreadCandidate(chunkPos, random);
        }
    }

    private record RandomSpreadCandidate(ChunkPos chunkPos, WorldgenRandom random) {
    }

    private record StructureSetEntry(String id, Holder<Structure> structure, int weight, boolean custom) {
    }

    private static StructureTemplateManager createTemplateManager(ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess storageAccess) throws IOException {
        StructureTemplateManager manager = new StructureTemplateManager(resourceManager, storageAccess, DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
        manager.onResourceManagerReload(resourceManager);
        return manager;
    }

    private static LevelStorageSource.LevelStorageAccess createTempStorageAccess() throws IOException {
        Path root = Files.createTempDirectory("voxelmap-seedmapper-datapack");
        LevelStorageSource source = LevelStorageSource.createDefault(root);
        return source.createAccess("seedmapper");
    }

    private static Set<Identifier> loadVanillaStructureIds() throws IOException {
        PackResources vanilla = createVanillaPack();
        try (CloseableResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla))) {
            RegistryAccess.Frozen access = loadRegistryAccess(manager);
            Registry<Structure> structureRegistry = access.lookupOrThrow(Registries.STRUCTURE);
            return new HashSet<>(structureRegistry.keySet());
        }
    }

    private static List<CustomStructureSet> buildCustomStructureSets(RegistryAccess access, Set<Identifier> vanillaStructures) {
        Registry<StructureSet> structureSets = access.lookupOrThrow(Registries.STRUCTURE_SET);
        List<CustomStructureSet> result = new ArrayList<>();
        for (Map.Entry<ResourceKey<StructureSet>, StructureSet> entry : structureSets.entrySet()) {
            ResourceKey<StructureSet> key = entry.getKey();
            StructureSet set = entry.getValue();
            List<StructureSetEntry> entries = new ArrayList<>();
            boolean hasCustom = false;
            for (StructureSet.StructureSelectionEntry selection : set.structures()) {
                Holder<Structure> holder = selection.structure();
                Optional<ResourceKey<Structure>> structureKey = holder.unwrapKey();
                if (structureKey.isEmpty()) {
                    continue;
                }
                Identifier id = structureKey.get().identifier();
                boolean custom = !vanillaStructures.contains(id);
                if (custom) {
                    hasCustom = true;
                }
                entries.add(new StructureSetEntry(id.toString(), holder, selection.weight(), custom));
            }
            if (hasCustom && !entries.isEmpty()) {
                result.add(new CustomStructureSet(key, set, entries));
            }
        }
        return result;
    }

    private static Path createTemporaryFallbackDatapack() throws IOException {
        Path temp = Files.createTempDirectory("voxelmap_seedmapper_fallback_datapack");
        Path dataDir = temp.resolve("data").resolve("minecraft").resolve("worldgen").resolve("density_function");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("preliminary_surface_level.json"), "{\n  \"type\": \"minecraft:constant\",\n  \"argument\": 64.0\n}\n");
        return temp;
    }

    private static Path sanitizeDatapack(Path baseDir) throws IOException {
        if (baseDir == null) {
            return null;
        }
        Path sanitized = Files.createTempDirectory("voxelmap_seedmapper_sanitized_");
        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.forEach(source -> {
                try {
                    Path rel = baseDir.relativize(source);
                    String relStr = rel.toString().replace('\\', '/');
                    if (shouldSkipSanitizedDatapackPath(relStr)) {
                        return;
                    }
                    Path target = sanitized.resolve(rel);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        if (shouldSanitizeBiomePath(relStr)) {
                            Files.writeString(target, sanitizeBiomeJson(Files.readString(source)));
                        } else {
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return sanitized;
    }

    private static PackResources createPathPack(String id, Path path, PackSource source) {
        PackLocationInfo info = new PackLocationInfo(id, net.minecraft.network.chat.Component.literal(id), source, Optional.empty());
        return new PathPackResources(info, path);
    }

    private static PackResources createVanillaPack() {
        PackLocationInfo info = new PackLocationInfo("voxelmap_seedmapper_vanilla", net.minecraft.network.chat.Component.literal("Vanilla"), PackSource.BUILT_IN, Optional.empty());
        return new VanillaPackResourcesBuilder()
                .applyDevelopmentConfig()
                .setMetadata(ResourceMetadata.EMPTY)
                .pushJarResources()
                .exposeNamespace("minecraft", "c")
                .build(info);
    }

    private static RegistryAccess.Frozen loadRegistryAccess(ResourceManager resourceManager) {
        ResourceManager structureSafeResourceManager = new FilteringResourceManager(resourceManager, SeedMapperDatapackWorldgen::isUnsafeForStructureImportResource);
        List<RegistryLoadAttempt> attempts = List.of(
                new RegistryLoadAttempt("structure_safe_full_worldgen", structureSafeResourceManager, key -> !isUnsafeForStructureImportRegistry(key)),
                new RegistryLoadAttempt("structure_safe_skip_presets", structureSafeResourceManager, key -> key != null && !isUnsafeForStructureImportRegistry(key) && !Registries.WORLD_PRESET.equals(key)),
                new RegistryLoadAttempt("structures_only", new FilteringResourceManager(structureSafeResourceManager, SeedMapperDatapackWorldgen::isIrrelevantToStructureImportResource),
                        key -> key != null && !isUnsafeForStructureImportRegistry(key) && (
                                Registries.STRUCTURE.equals(key)
                                        || Registries.STRUCTURE_SET.equals(key)
                                        || Registries.BIOME.equals(key)
                                        || Registries.LEVEL_STEM.equals(key)
                                        || Registries.DIMENSION_TYPE.equals(key)
                                        || Registries.CONFIGURED_CARVER.equals(key)
                                        || Registries.PLACED_FEATURE.equals(key)
                                        || Registries.CONFIGURED_FEATURE.equals(key)
                                        || Registries.PROCESSOR_LIST.equals(key)
                                        || Registries.TEMPLATE_POOL.equals(key)
                                        || Registries.NOISE_SETTINGS.equals(key)
                                        || Registries.NOISE.equals(key)
                                        || Registries.DENSITY_FUNCTION.equals(key)
                                        || Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST.equals(key)))
        );
        Exception lastError = null;
        for (RegistryLoadAttempt attempt : attempts) {
            try {
                return loadRegistryAccessInternal(attempt.resourceManager(), attempt.registryPredicate());
            } catch (Exception e) {
                lastError = e;
                LOGGER.warn("Datapack registry load attempt failed: {}", attempt.name(), e);
            }
        }
        if (lastError instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new RuntimeException(lastError);
    }

    private static RegistryAccess.Frozen loadRegistryAccessInternal(ResourceManager resourceManager, Predicate<ResourceKey<? extends Registry<?>>> registryFilter) {
        LayeredRegistryAccess<RegistryLayer> layered = RegistryLayer.createRegistryAccess();
        RegistryAccess.Frozen staticAccess = layered.getLayer(RegistryLayer.STATIC);
        List<HolderLookup.RegistryLookup<?>> staticLookups = staticAccess.listRegistries().collect(Collectors.toList());
        List<RegistryDataLoader.RegistryData<?>> worldgenRegistries = filterRegistryData(RegistryDataLoader.WORLDGEN_REGISTRIES, registryFilter);
        RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resourceManager, staticLookups, worldgenRegistries, Runnable::run).join();
        List<HolderLookup.RegistryLookup<?>> dimensionLookups = Stream.concat(staticLookups.stream(), worldgen.listRegistries()).collect(Collectors.toList());
        List<RegistryDataLoader.RegistryData<?>> dimensionRegistries = filterRegistryData(RegistryDataLoader.DIMENSION_REGISTRIES, registryFilter);
        RegistryAccess.Frozen dimensions = RegistryDataLoader.load(resourceManager, dimensionLookups, dimensionRegistries, Runnable::run).join();
        Optional<Registry<LevelStem>> loadedStems = dimensions.lookup(Registries.LEVEL_STEM);
        if (loadedStems.isEmpty() || loadedStems.get().keySet().isEmpty()) {
            dimensions = layered.getLayer(RegistryLayer.DIMENSIONS);
        }
        RegistryAccess.Frozen composite = layered.replaceFrom(RegistryLayer.WORLDGEN, worldgen, dimensions).compositeAccess();
        try {
            List<Registry.PendingTags<?>> pendingTags = TagLoader.loadTagsForExistingRegistries(resourceManager, composite);
            for (Registry.PendingTags<?> pending : pendingTags) {
                pending.apply();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to apply datapack tags", e);
        }
        return composite;
    }

    private static List<RegistryDataLoader.RegistryData<?>> filterRegistryData(List<RegistryDataLoader.RegistryData<?>> registries, Predicate<ResourceKey<? extends Registry<?>>> registryFilter) {
        if (registries == null || registries.isEmpty()) {
            return List.of();
        }
        return registries.stream().filter(data -> data != null && registryFilter.test(data.key())).toList();
    }

    private static boolean isIrrelevantToStructureImportResource(Identifier id) {
        if (id == null || id.getPath() == null) {
            return false;
        }
        String path = id.getPath();
        return !(path.startsWith("worldgen/structure")
                || path.startsWith("worldgen/structure_set")
                || path.startsWith("worldgen/biome")
                || path.startsWith("worldgen/dimension")
                || path.startsWith("worldgen/dimension_type")
                || path.startsWith("worldgen/configured_carver")
                || path.startsWith("worldgen/placed_feature")
                || path.startsWith("worldgen/configured_feature")
                || path.startsWith("worldgen/processor_list")
                || path.startsWith("worldgen/template_pool")
                || path.startsWith("tags/worldgen/biome")
                || path.startsWith("tags/biome")
                || path.startsWith("tags/worldgen/structure")
                || path.startsWith("tags/worldgen/structure_set")
                || path.startsWith("tags/worldgen/configured_carver")
                || path.startsWith("tags/worldgen/placed_feature")
                || path.startsWith("tags/worldgen/configured_feature")
                || path.startsWith("tags/worldgen/processor_list")
                || path.startsWith("tags/worldgen/template_pool")
                || path.startsWith("structures/"));
    }

    private static boolean shouldSkipSanitizedDatapackPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        String relStr = relativePath.replace('\\', '/');
        if (!relStr.startsWith("data/")) {
            return false;
        }
        int namespaceSeparator = relStr.indexOf('/', "data/".length());
        if (namespaceSeparator < 0 || namespaceSeparator + 1 >= relStr.length()) {
            return false;
        }
        String dataPath = relStr.substring(namespaceSeparator + 1);
        if (dataPath.startsWith("chicken_variant/")
                || dataPath.startsWith("wolf_variant/")
                || dataPath.startsWith("villager_trade/")
                || dataPath.startsWith("enchantment/")
                || dataPath.startsWith("enchantment_provider/")
                || dataPath.startsWith("tags/villager_trade/")
                || dataPath.startsWith("tags/enchantment/")
                || dataPath.startsWith("tags/enchantments/")
                || dataPath.startsWith("tags/items/enchantable/")) {
            return true;
        }
        return dataPath.startsWith("worldgen/noise_settings")
                || dataPath.startsWith("worldgen/density_function")
                || dataPath.startsWith("worldgen/world_preset")
                || dataPath.startsWith("worldgen/placed_feature")
                || dataPath.startsWith("worldgen/configured_feature")
                || dataPath.startsWith("tags/worldgen/placed_feature/")
                || dataPath.startsWith("tags/worldgen/configured_feature/");
    }

    private static boolean shouldSanitizeBiomePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        String relStr = relativePath.replace('\\', '/');
        if (!relStr.startsWith("data/")) {
            return false;
        }
        int namespaceSeparator = relStr.indexOf('/', "data/".length());
        if (namespaceSeparator < 0 || namespaceSeparator + 1 >= relStr.length()) {
            return false;
        }
        String dataPath = relStr.substring(namespaceSeparator + 1);
        return dataPath.startsWith("worldgen/biome/") && dataPath.endsWith(".json");
    }

    private static String sanitizeBiomeJson(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            return json;
        }
        JsonObject root = parsed.getAsJsonObject();
        root.add("features", new JsonArray());
        root.add("carvers", new JsonArray());
        if (root.has("generation_settings") && root.get("generation_settings").isJsonObject()) {
            JsonObject generationSettings = root.getAsJsonObject("generation_settings");
            generationSettings.remove("features");
            generationSettings.remove("carvers");
        }
        return root.toString();
    }

    private static boolean isUnsafeForStructureImportResource(Identifier id) {
        if (id == null || id.getPath() == null) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith("villager_trade/")
                || path.startsWith("tags/villager_trade/")
                || path.startsWith("chicken_variant/")
                || path.startsWith("tags/chicken_variant/")
                || path.startsWith("wolf_variant/")
                || path.startsWith("tags/wolf_variant/")
                || path.startsWith("enchantment/")
                || path.startsWith("enchantment_provider/")
                || path.startsWith("tags/enchantment/")
                || path.startsWith("tags/enchantments/")
                || path.startsWith("tags/items/enchantable/");
    }

    private static boolean isUnsafeForStructureImportRegistry(ResourceKey<? extends Registry<?>> key) {
        if (key == null) {
            return true;
        }
        return Registries.VILLAGER_TRADE.equals(key)
                || Registries.TRADE_SET.equals(key)
                || Registries.ENCHANTMENT.equals(key)
                || Registries.ENCHANTMENT_PROVIDER.equals(key)
                || Registries.CHICKEN_VARIANT.equals(key)
                || Registries.CHICKEN_SOUND_VARIANT.equals(key)
                || Registries.WOLF_VARIANT.equals(key)
                || Registries.WOLF_SOUND_VARIANT.equals(key);
    }

    private record RegistryLoadAttempt(String name, ResourceManager resourceManager, Predicate<ResourceKey<? extends Registry<?>>> registryPredicate) {
    }

    private static final class FilteringResourceManager implements ResourceManager {
        private final ResourceManager delegate;
        private final Predicate<Identifier> deny;

        private FilteringResourceManager(ResourceManager delegate, Predicate<Identifier> deny) {
            this.delegate = delegate;
            this.deny = deny;
        }

        @Override
        public Optional<Resource> getResource(Identifier id) {
            return this.deny.test(id) ? Optional.empty() : this.delegate.getResource(id);
        }

        @Override
        public List<Resource> getResourceStack(Identifier id) {
            return this.deny.test(id) ? List.of() : this.delegate.getResourceStack(id);
        }

        @Override
        public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter) {
            return this.delegate.listResources(path, id -> !this.deny.test(id) && filter.test(id));
        }

        @Override
        public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter) {
            return this.delegate.listResourceStacks(path, id -> !this.deny.test(id) && filter.test(id));
        }

        @Override
        public Set<String> getNamespaces() {
            return this.delegate.getNamespaces();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return this.delegate.listPacks();
        }
    }

    private static ResourceKey<LevelStem> stemKeyForDimension(int dimensionId) {
        if (dimensionId == Cubiomes.DIM_NETHER() || dimensionId == -1) {
            return LevelStem.NETHER;
        }
        if (dimensionId == Cubiomes.DIM_END() || dimensionId == 1) {
            return LevelStem.END;
        }
        if (dimensionId == Cubiomes.DIM_OVERWORLD() || dimensionId == 0) {
            return LevelStem.OVERWORLD;
        }
        return null;
    }

    private static ResourceKey<NoiseGeneratorSettings> noiseSettingsForDimension(int dimensionId) {
        if (dimensionId == Cubiomes.DIM_NETHER() || dimensionId == -1) {
            return NoiseGeneratorSettings.NETHER;
        }
        if (dimensionId == Cubiomes.DIM_END() || dimensionId == 1) {
            return NoiseGeneratorSettings.END;
        }
        return NoiseGeneratorSettings.OVERWORLD;
    }

    private static RandomState createRandomState(ChunkGenerator chunkGenerator, RegistryAccess access, int dimensionId, long seed) {
        if (chunkGenerator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGenerator) {
            Holder<NoiseGeneratorSettings> holder = noiseGenerator.generatorSettings();
            Optional<ResourceKey<NoiseGeneratorSettings>> key = holder.unwrapKey();
            if (key.isPresent()) {
                return RandomState.create(access, key.get(), seed);
            }
            HolderGetter<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> noiseParams = access.lookupOrThrow(Registries.NOISE);
            return RandomState.create(holder.value(), noiseParams, seed);
        }
        return RandomState.create(access, noiseSettingsForDimension(dimensionId), seed);
    }

    private static WorldgenRandom createSelectionRandom(long seed, int chunkX, int chunkZ, StructurePlacement placement) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, chunkX, chunkZ, getPlacementSalt(placement));
        return random;
    }

    private static Optional<Registry<LevelStem>> getClientLevelStemRegistry() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return Optional.empty();
        }
        return minecraft.level.registryAccess().lookup(Registries.LEVEL_STEM);
    }

    private static Optional<LevelStem> resolveLevelStemFromPreset(RegistryAccess registryAccess, ResourceKey<LevelStem> stemKey) {
        Optional<Registry<WorldPreset>> presets = getRegistryFromDatapackOrClient(registryAccess, Registries.WORLD_PRESET);
        if (presets.isEmpty()) {
            return Optional.empty();
        }
        Identifier presetId = Identifier.withDefaultNamespace("normal");
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        if (settings != null && settings.largeBiomes) {
            presetId = Identifier.withDefaultNamespace("large_biomes");
        }
        WorldPreset preset = presets.get().getValue(presetId);
        if (preset == null) {
            preset = presets.get().getValue(Identifier.withDefaultNamespace("normal"));
        }
        if (preset == null) {
            return Optional.empty();
        }
        WorldDimensions dimensions = preset.createWorldDimensions();
        return dimensions.get(stemKey);
    }

    private static <T> Optional<Registry<T>> getRegistryFromDatapackOrClient(RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> key) {
        Optional<Registry<T>> datapackRegistry = registryAccess.lookup(key);
        if (datapackRegistry.isPresent()) {
            return datapackRegistry;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            return minecraft.level.registryAccess().lookup(key);
        }
        return Optional.empty();
    }

    private static int getPlacementSalt(StructurePlacement placement) {
        if (placement == null || STRUCTURE_PLACEMENT_SALT == null) {
            return 0;
        }
        try {
            return (int) STRUCTURE_PLACEMENT_SALT.invoke(placement);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to read structure placement salt", e);
            return 0;
        }
    }

    private static Method resolvePlacementSaltMethod() {
        try {
            Method method = StructurePlacement.class.getDeclaredMethod("salt");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            for (Method method : StructurePlacement.class.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == int.class) {
                    try {
                        method.setAccessible(true);
                        return method;
                    } catch (Exception ignored) {
                    }
                }
            }
            LOGGER.warn("Failed to resolve StructurePlacement.salt()", e);
            return null;
        }
    }
}
