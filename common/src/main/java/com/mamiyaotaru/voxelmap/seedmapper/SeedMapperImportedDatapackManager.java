package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SeedMapperImportedDatapackManager {
    private static final Map<String, ImportedDatapack> CACHE = new HashMap<>();
    private static final Identifier POTION_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/item/potion.png");
    private static final Identifier POTION_OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/item/potion_overlay.png");

    private SeedMapperImportedDatapackManager() {
    }

    static synchronized ImportedDatapack getImportedDatapack(String datapackRootPath) {
        if (datapackRootPath == null || datapackRootPath.isBlank()) {
            return ImportedDatapack.EMPTY;
        }

        return CACHE.computeIfAbsent(datapackRootPath, SeedMapperImportedDatapackManager::loadImportedDatapack);
    }

    private static ImportedDatapack loadImportedDatapack(String datapackRootPath) {
        Path root;
        try {
            root = Path.of(datapackRootPath);
        } catch (RuntimeException ex) {
            return ImportedDatapack.EMPTY;
        }

        Path structureSetRoot = root.resolve("data");
        if (!Files.isDirectory(structureSetRoot)) {
            return ImportedDatapack.EMPTY;
        }

        ArrayList<ImportedStructureSet> structureSets = new ArrayList<>();
        Map<String, StructureDisplayData> structureDisplayData = new HashMap<>();
        try {
            Files.walk(structureSetRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/worldgen/structure_set/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> parseStructureSet(root, path, structureSets, structureDisplayData));
        } catch (IOException ignored) {
            return ImportedDatapack.EMPTY;
        }

        int hash = 1;
        for (ImportedStructureSet structureSet : structureSets) {
            hash = 31 * hash + structureSet.hashCode();
        }
        return new ImportedDatapack(Collections.unmodifiableList(structureSets), hash);
    }

    private static void parseStructureSet(Path datapackRoot, Path structureSetPath, List<ImportedStructureSet> target, Map<String, StructureDisplayData> structureDisplayData) {
        try (Reader reader = Files.newBufferedReader(structureSetPath)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                return;
            }

            JsonObject json = element.getAsJsonObject();
            JsonObject placementJson = json.getAsJsonObject("placement");
            if (placementJson == null) {
                return;
            }

            Placement placement = parsePlacement(placementJson);
            if (placement == null) {
                return;
            }

            JsonArray structuresJson = json.getAsJsonArray("structures");
            if (structuresJson == null || structuresJson.isEmpty()) {
                return;
            }

            ArrayList<WeightedStructure> structures = new ArrayList<>();
            for (JsonElement structureElement : structuresJson) {
                if (!structureElement.isJsonObject()) {
                    continue;
                }

                JsonObject structureJson = structureElement.getAsJsonObject();
                String structureId = readStructureId(structureJson.get("structure"));
                if (structureId == null || structureId.isBlank()) {
                    continue;
                }

                int weight = structureJson.has("weight") ? Math.max(1, structureJson.get("weight").getAsInt()) : 1;
                StructureDisplayData displayData = structureDisplayData.computeIfAbsent(structureId, id -> loadStructureDisplayData(datapackRoot, id));
                structures.add(new WeightedStructure(structureId, weight, displayData.locateOffsetX(), displayData.locateOffsetZ()));
            }

            if (structures.isEmpty()) {
                return;
            }

            String setId = toStructureSetId(datapackRoot, structureSetPath);
            target.add(new ImportedStructureSet(setId, placement, Collections.unmodifiableList(structures)));
        } catch (Exception ignored) {
        }
    }

    private static String readStructureId(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            return normalizeId(element.getAsString());
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("structure")) {
                return readStructureId(object.get("structure"));
            }
            if (object.has("name")) {
                return normalizeId(object.get("name").getAsString());
            }
        }

        return null;
    }

    private static Placement parsePlacement(JsonObject placementJson) {
        String type = placementJson.has("type") ? placementJson.get("type").getAsString() : "";
        String normalizedType = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        if ("random_spread".equals(normalizedType)) {
            int spacing = placementJson.has("spacing") ? placementJson.get("spacing").getAsInt() : 32;
            int separation = placementJson.has("separation") ? placementJson.get("separation").getAsInt() : Math.max(1, spacing / 2);
            int salt = placementJson.has("salt") ? placementJson.get("salt").getAsInt() : 0;
            String spreadType = placementJson.has("spread_type") ? placementJson.get("spread_type").getAsString() : "linear";
            return new RandomSpreadPlacement(spacing, separation, salt, spreadType);
        }

        return null;
    }

    private static String toStructureSetId(Path datapackRoot, Path structureSetPath) {
        Path relative = datapackRoot.relativize(structureSetPath);
        String normalized = relative.toString().replace('\\', '/');
        String[] segments = normalized.split("/");
        if (segments.length >= 5 && "data".equals(segments[0]) && "worldgen".equals(segments[2]) && "structure_set".equals(segments[3])) {
            String namespace = segments[1];
            String file = segments[4];
            if (file.endsWith(".json")) {
                file = file.substring(0, file.length() - 5);
            }
            return normalizeId(namespace + ":" + file);
        }
        return normalizeId(normalized.replace('/', ':'));
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    public static List<SeedMapperMarker> queryImportedMarkers(String datapackRootPath, long seed, int minX, int maxX, int minZ, int maxZ) {
        ImportedDatapack datapack = getImportedDatapack(datapackRootPath);
        if (datapack == ImportedDatapack.EMPTY || datapack.structureSets().isEmpty()) {
            return List.of();
        }

        ArrayList<SeedMapperMarker> markers = new ArrayList<>();
        for (ImportedStructureSet structureSet : datapack.structureSets()) {
            markers.addAll(structureSet.query(seed, minX, maxX, minZ, maxZ));
        }
        return markers;
    }

    public static int colorForStructureId(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return 0xFFFFFFFF;
        }
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        int scheme = settings == null ? 1 : settings.datapackColorScheme;
        if (scheme == 2) {
            return hsvColorForStructureId(structureId, 0.45D, 0.95D, 0.50D);
        }
        if (scheme == 3) {
            return hsvColorForStructureId(structureId, 1.0D, 1.0D, 0.0D);
        }
        return colorForSchemeOne(structureId);
    }

    public static Identifier iconForStructureId(String structureId) {
        return POTION_TEXTURE;
    }

    public static Identifier iconOverlayForStructureId(String structureId) {
        return POTION_OVERLAY_TEXTURE;
    }

    public static boolean usesPotionIcon() {
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        return (settings == null ? 1 : settings.datapackIconStyle) == 3;
    }

    public static int iconSizeForPersistentMap() {
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        int style = settings == null ? 1 : settings.datapackIconStyle;
        return style == 1 ? 8 : 16;
    }

    public static int iconSizeForMinimap() {
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        int style = settings == null ? 1 : settings.datapackIconStyle;
        return style == 1 ? 4 : 8;
    }

    private static int colorForSchemeOne(String structureId) {
        int hash = structureId.hashCode();
        int red = 96 + (hash & 0x7F);
        int green = 96 + ((hash >>> 8) & 0x7F);
        int blue = 96 + ((hash >>> 16) & 0x7F);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int hsvColorForStructureId(String structureId, double saturation, double value, double hueShift) {
        int hash = structureId.hashCode();
        double hue = (Math.floorMod(hash, 100000) / 100000.0 + hueShift) % 1.0;
        if (saturation >= 0.999D && value >= 0.999D) {
            saturation = 0.95D;
        }
        return hsvToArgb(hue, saturation, value);
    }

    private static int hsvToArgb(double hue, double saturation, double value) {
        int rgb = java.awt.Color.HSBtoRGB((float) hue, (float) saturation, (float) value);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static StructureDisplayData loadStructureDisplayData(Path datapackRoot, String structureId) {
        try {
            Path structurePath = structurePath(datapackRoot, structureId);
            if (!Files.isRegularFile(structurePath)) {
                return StructureDisplayData.DEFAULT;
            }
            try (Reader reader = Files.newBufferedReader(structurePath)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    return StructureDisplayData.DEFAULT;
                }
                JsonObject json = element.getAsJsonObject();
                Vec2i locateOffset = parseLocateOffset(json.get("locate_offset"));
                return new StructureDisplayData(locateOffset.x(), locateOffset.z());
            }
        } catch (Exception ignored) {
        }
        return StructureDisplayData.DEFAULT;
    }

    private static Path structurePath(Path datapackRoot, String structureId) {
        String normalized = normalizeId(structureId);
        String namespace = "minecraft";
        String path = normalized;
        int separator = normalized.indexOf(':');
        if (separator >= 0) {
            namespace = normalized.substring(0, separator);
            path = normalized.substring(separator + 1);
        }
        return datapackRoot.resolve("data").resolve(namespace).resolve("worldgen").resolve("structure").resolve(path + ".json");
    }

    private static Vec2i parseLocateOffset(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new Vec2i(8, 8);
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() >= 3) {
                return new Vec2i(array.get(0).getAsInt(), array.get(2).getAsInt());
            }
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            int x = object.has("x") ? object.get("x").getAsInt() : 8;
            int z = object.has("z") ? object.get("z").getAsInt() : 8;
            return new Vec2i(x, z);
        }
        return new Vec2i(8, 8);
    }

    static final class ImportedDatapack {
        static final ImportedDatapack EMPTY = new ImportedDatapack(List.of(), 0);

        private final List<ImportedStructureSet> structureSets;
        private final int hash;

        ImportedDatapack(List<ImportedStructureSet> structureSets, int hash) {
            this.structureSets = structureSets;
            this.hash = hash;
        }

        List<ImportedStructureSet> structureSets() {
            return structureSets;
        }

        int hash() {
            return hash;
        }
    }

    private record ImportedStructureSet(String id, Placement placement, List<WeightedStructure> structures) {
        List<SeedMapperMarker> query(long seed, int minX, int maxX, int minZ, int maxZ) {
            if (!(placement instanceof RandomSpreadPlacement randomSpreadPlacement)) {
                return List.of();
            }

            int minChunkX = floorDiv(minX, 16);
            int maxChunkX = floorDiv(maxX, 16);
            int minChunkZ = floorDiv(minZ, 16);
            int maxChunkZ = floorDiv(maxZ, 16);
            int spacing = Math.max(1, randomSpreadPlacement.spacing());

            int minRegionX = floorDiv(minChunkX, spacing) - 1;
            int maxRegionX = floorDiv(maxChunkX, spacing) + 1;
            int minRegionZ = floorDiv(minChunkZ, spacing) - 1;
            int maxRegionZ = floorDiv(maxChunkZ, spacing) + 1;

            ArrayList<SeedMapperMarker> markers = new ArrayList<>();
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    WeightedStructure structure = selectStructure(seed, regionX, regionZ, randomSpreadPlacement.salt());
                    if (structure == null) {
                        continue;
                    }

                    ChunkCandidate candidate = randomSpreadPlacement.sampleChunk(seed, regionX, regionZ);
                    if (candidate.chunkX() < minChunkX || candidate.chunkX() > maxChunkX || candidate.chunkZ() < minChunkZ || candidate.chunkZ() > maxChunkZ) {
                        continue;
                    }

                    int blockX = candidate.chunkX() * 16 + structure.locateOffsetX();
                    int blockZ = candidate.chunkZ() * 16 + structure.locateOffsetZ();
                    markers.add(new SeedMapperMarker(SeedMapperFeature.DATAPACK_STRUCTURE, blockX, blockZ, structure.id()));
                }
            }

            return markers;
        }

        private WeightedStructure selectStructure(long seed, int regionX, int regionZ, int salt) {
            if (structures.isEmpty()) {
                return null;
            }
            if (structures.size() == 1) {
                return structures.get(0);
            }

            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureWithSalt(seed, regionX, regionZ, salt);

            int totalWeight = 0;
            for (WeightedStructure structure : structures) {
                totalWeight += structure.weight();
            }
            if (totalWeight <= 0) {
                return structures.get(0);
            }

            int roll = random.nextInt(totalWeight);
            int accumulated = 0;
            for (WeightedStructure structure : structures) {
                accumulated += structure.weight();
                if (roll < accumulated) {
                    return structure;
                }
            }
            return structures.get(structures.size() - 1);
        }
    }

    private record WeightedStructure(String id, int weight, int locateOffsetX, int locateOffsetZ) {
    }

    private sealed interface Placement permits RandomSpreadPlacement {
    }

    private record RandomSpreadPlacement(int spacing, int separation, int salt, String spreadType) implements Placement {
        ChunkCandidate sampleChunk(long seed, int regionX, int regionZ) {
            int safeSpacing = Math.max(1, spacing);
            int maxOffset = Math.max(1, safeSpacing - Math.max(0, separation));

            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureWithSalt(seed, regionX, regionZ, salt);

            int offsetX = sampleOffset(random, maxOffset);
            int offsetZ = sampleOffset(random, maxOffset);
            return new ChunkCandidate(regionX * safeSpacing + offsetX, regionZ * safeSpacing + offsetZ);
        }

        private int sampleOffset(WorldgenRandom random, int maxOffset) {
            if ("triangular".equalsIgnoreCase(spreadType)) {
                return (random.nextInt(maxOffset) + random.nextInt(maxOffset)) / 2;
            }
            return random.nextInt(maxOffset);
        }
    }

    private record ChunkCandidate(int chunkX, int chunkZ) {
    }

    private record StructureDisplayData(int locateOffsetX, int locateOffsetZ) {
        private static final StructureDisplayData DEFAULT = new StructureDisplayData(8, 8);
    }

    private record Vec2i(int x, int z) {
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) {
            r--;
        }
        return r;
    }
}
