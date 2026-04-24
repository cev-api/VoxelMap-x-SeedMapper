package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.interfaces.ISubSettingsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Mth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SeedMapperSettingsManager implements ISubSettingsManager {
    public boolean enabled = true;
    public boolean showDistant = true;
    public boolean showLootableOnly = false;
    public boolean largeBiomes = false;

    public String manualSeed = "";
    public String lootSearch = "";
    public String espTarget = "";
    public String datapackUrl = "";
    public boolean datapackAutoload = false;
    public boolean datapackEnabled = false;
    public String datapackCachePath = "";
    public int datapackColorScheme = 1;
    public int datapackIconStyle = 1;

    public boolean espEnabled = false;
    public int espDefaultChunks = 4;
    public double espTimeoutMinutes = 5.0D;
    public int worldMapMarkerLimit = 5000;

    private final Set<SeedMapperFeature> enabledFeatures = EnumSet.allOf(SeedMapperFeature.class);
    private final Set<String> completedFeatureEntries = new HashSet<>();
    private final Set<String> datapackLocatedEntries = new HashSet<>();
    private final Map<SeedMapperEspTarget, SeedMapperEspStyle> espStyles = new EnumMap<>(SeedMapperEspTarget.class);
    private final List<Integer> datapackRandomColors = new ArrayList<>();
    private final Map<String, String> datapackSavedUrls = new HashMap<>();
    private final Map<String, String> datapackSavedCachePaths = new HashMap<>();
    private final Map<String, String> savedSeeds = new HashMap<>();
    private final Map<String, Set<String>> datapackStructureDisabled = new HashMap<>();

    public SeedMapperSettingsManager() {
        for (SeedMapperEspTarget target : SeedMapperEspTarget.values()) {
            espStyles.put(target, SeedMapperEspStyle.useCommandColorDefaults());
        }
    }

    @Override
    public void loadAll(File settingsFile) {
        try (BufferedReader in = new BufferedReader(new FileReader(settingsFile))) {
            String sCurrentLine;
            while ((sCurrentLine = in.readLine()) != null) {
                String[] curLine = sCurrentLine.split(":", 2);
                if (curLine.length < 2) {
                    continue;
                }
                switch (curLine[0]) {
                    case "SeedMapper Enabled" -> enabled = Boolean.parseBoolean(curLine[1]);
                    case "SeedMapper Show Distant" -> showDistant = Boolean.parseBoolean(curLine[1]);
                    case "SeedMapper Show Lootable Only" -> showLootableOnly = Boolean.parseBoolean(curLine[1]);
                    case "SeedMapper Large Biomes" -> largeBiomes = Boolean.parseBoolean(curLine[1]);
                    case "SeedMapper Show Player Direction Arrow" -> {
                    }
                    case "SeedMapper Manual Seed" -> manualSeed = curLine[1];
                    case "SeedMapper Loot Search" -> lootSearch = curLine[1];
                    case "SeedMapper ESP Target" -> espTarget = curLine[1];
                    case "SeedMapper Datapack URL" -> datapackUrl = curLine[1];
                    case "SeedMapper Datapack Autoload" -> datapackAutoload = Boolean.parseBoolean(curLine[1]);
                    case "SeedMapper Datapack Enabled" -> datapackEnabled = Boolean.parseBoolean(curLine[1]);
                    case "SeedMapper Datapack Cache Path" -> datapackCachePath = curLine[1];
                    case "SeedMapper Datapack Color Scheme" -> datapackColorScheme = Mth.clamp(Integer.parseInt(curLine[1]), 1, 3);
                    case "SeedMapper Datapack Icon Style" -> datapackIconStyle = Mth.clamp(Integer.parseInt(curLine[1]), 1, 3);
                    case "SeedMapper ESP Enabled" -> espEnabled = Boolean.parseBoolean(curLine[1]);
                    case "SeedMapper ESP Default Chunks" -> espDefaultChunks = Mth.clamp(Integer.parseInt(curLine[1]), 0, 8);
                    case "SeedMapper ESP Timeout Minutes" -> espTimeoutMinutes = Math.max(0.0D, Double.parseDouble(curLine[1]));
                    case "SeedMapper WorldMap Marker Limit" -> worldMapMarkerLimit = Mth.clamp(Integer.parseInt(curLine[1]), 200, 20000);
                    case "SeedMapper Datapack Random Colors" -> loadIntList(curLine[1], datapackRandomColors);
                    case "SeedMapper Datapack Saved URLs" -> loadMap(curLine[1], datapackSavedUrls);
                    case "SeedMapper Datapack Saved Cache Paths" -> loadMap(curLine[1], datapackSavedCachePaths);
                    case "SeedMapper Saved Seeds" -> loadMap(curLine[1], savedSeeds);
                    case "SeedMapper Datapack Structure Disabled" -> loadWorldSetMap(curLine[1], datapackStructureDisabled);
                    case "SeedMapper Completed" -> {
                        completedFeatureEntries.clear();
                        if (!curLine[1].isBlank()) {
                            for (String entry : curLine[1].split(",")) {
                                String trimmed = entry.trim();
                                if (!trimmed.isBlank()) {
                                    completedFeatureEntries.add(trimmed);
                                }
                            }
                        }
                    }
                    case "SeedMapper Datapack Located" -> {
                        datapackLocatedEntries.clear();
                        if (!curLine[1].isBlank()) {
                            for (String entry : curLine[1].split(",")) {
                                String trimmed = entry.trim();
                                if (!trimmed.isBlank()) {
                                    datapackLocatedEntries.add(trimmed);
                                }
                            }
                        }
                    }
                    default -> loadExtendedSetting(curLine[0], curLine[1]);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void loadExtendedSetting(String key, String value) {
        if (key.startsWith("SeedMapper Feature ")) {
            String featureId = key.substring("SeedMapper Feature ".length()).trim().toUpperCase(Locale.ROOT);
            try {
                SeedMapperFeature feature = SeedMapperFeature.valueOf(featureId);
                if (Boolean.parseBoolean(value)) {
                    enabledFeatures.add(feature);
                } else {
                    enabledFeatures.remove(feature);
                }
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }

        if (key.startsWith("SeedMapper ESP ")) {
            for (SeedMapperEspTarget target : SeedMapperEspTarget.values()) {
                String prefix = target.configPrefix() + " ";
                if (key.startsWith(prefix)) {
                    getEspStyle(target).loadLine(key.substring(prefix.length()), value);
                    return;
                }
            }
        }
    }

    @Override
    public void saveAll(PrintWriter out) {
        out.println("SeedMapper Enabled:" + enabled);
        out.println("SeedMapper Show Distant:" + showDistant);
        out.println("SeedMapper Show Lootable Only:" + showLootableOnly);
        out.println("SeedMapper Large Biomes:" + largeBiomes);
        out.println("SeedMapper Manual Seed:" + manualSeed);
        out.println("SeedMapper Loot Search:" + lootSearch);
        out.println("SeedMapper ESP Target:" + espTarget);
        out.println("SeedMapper Datapack URL:" + datapackUrl);
        out.println("SeedMapper Datapack Autoload:" + datapackAutoload);
        out.println("SeedMapper Datapack Enabled:" + datapackEnabled);
        out.println("SeedMapper Datapack Cache Path:" + datapackCachePath);
        out.println("SeedMapper Datapack Color Scheme:" + datapackColorScheme);
        out.println("SeedMapper Datapack Icon Style:" + datapackIconStyle);
        out.println("SeedMapper ESP Enabled:" + espEnabled);
        out.println("SeedMapper ESP Default Chunks:" + espDefaultChunks);
        out.println("SeedMapper ESP Timeout Minutes:" + espTimeoutMinutes);
        out.println("SeedMapper WorldMap Marker Limit:" + worldMapMarkerLimit);
        out.println("SeedMapper Datapack Random Colors:" + saveIntList(datapackRandomColors));
        out.println("SeedMapper Datapack Saved URLs:" + saveMap(datapackSavedUrls));
        out.println("SeedMapper Datapack Saved Cache Paths:" + saveMap(datapackSavedCachePaths));
        out.println("SeedMapper Saved Seeds:" + saveMap(savedSeeds));
        out.println("SeedMapper Datapack Structure Disabled:" + saveWorldSetMap(datapackStructureDisabled));
        out.println("SeedMapper Completed:" + String.join(",", completedFeatureEntries));
        out.println("SeedMapper Datapack Located:" + String.join(",", datapackLocatedEntries));
        for (SeedMapperEspTarget target : SeedMapperEspTarget.values()) {
            getEspStyle(target).save(out, target.configPrefix());
        }
        for (SeedMapperFeature feature : SeedMapperFeature.values()) {
            out.println("SeedMapper Feature " + feature.name() + ":" + enabledFeatures.contains(feature));
        }
    }

    public long resolveSeed(String fallback) {
        String value = manualSeed == null || manualSeed.isBlank() ? fallback : manualSeed.trim();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("No seed available");
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return value.hashCode();
        }
    }

    public SeedMapperEspStyle getEspStyle(SeedMapperEspTarget target) {
        return espStyles.computeIfAbsent(target, ignored -> SeedMapperEspStyle.useCommandColorDefaults());
    }

    public SeedMapperEspStyleSnapshot getEspStyleSnapshot(SeedMapperEspTarget target, int fallbackColor) {
        return getEspStyle(target).snapshot(fallbackColor);
    }

    public String getCurrentServerKey() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip;
        }
        if (minecraft.hasSingleplayerServer()) {
            return "singleplayer";
        }
        return "unknown";
    }

    public Map<String, String> getSavedSeedsSnapshot() {
        return new HashMap<>(savedSeeds);
    }

    public void putSavedSeed(String key, String seed) {
        putMappedValue(savedSeeds, key, seed);
    }

    public String getSavedSeed(String key) {
        return key == null ? null : savedSeeds.get(key);
    }

    public void loadSavedSeedForCurrentServer() {
        String saved = getSavedSeed(getCurrentServerKey());
        if (saved != null && !saved.isBlank()) {
            manualSeed = saved.trim();
        } else {
            manualSeed = "";
        }
    }

    public Map<String, String> getDatapackSavedUrlsSnapshot() {
        return new HashMap<>(datapackSavedUrls);
    }

    public Map<String, String> getDatapackSavedCachePathsSnapshot() {
        return new HashMap<>(datapackSavedCachePaths);
    }

    public void putDatapackSavedUrl(String key, String url) {
        putMappedValue(datapackSavedUrls, key, url);
    }

    public void putDatapackSavedCachePath(String key, String path) {
        putMappedValue(datapackSavedCachePaths, key, path);
    }

    public List<Integer> getDatapackRandomColors() {
        return datapackRandomColors;
    }

    public Set<String> getDisabledDatapackStructures(String worldKey) {
        return new HashSet<>(datapackStructureDisabled.getOrDefault(worldKey, Set.of()));
    }

    public boolean isDatapackStructureEnabled(String worldKey, String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return true;
        }
        return !datapackStructureDisabled.getOrDefault(worldKey, Set.of()).contains(structureId);
    }

    public void setDatapackStructureEnabled(String worldKey, String structureId, boolean enabled) {
        if (worldKey == null || worldKey.isBlank() || structureId == null || structureId.isBlank()) {
            return;
        }
        Set<String> disabled = datapackStructureDisabled.computeIfAbsent(worldKey, ignored -> new HashSet<>());
        if (enabled) {
            disabled.remove(structureId);
            if (disabled.isEmpty()) {
                datapackStructureDisabled.remove(worldKey);
            }
        } else {
            disabled.add(structureId);
        }
    }

    public boolean isFeatureEnabled(SeedMapperFeature feature) {
        return enabledFeatures.contains(feature);
    }

    public void toggleFeature(SeedMapperFeature feature) {
        if (enabledFeatures.contains(feature)) {
            enabledFeatures.remove(feature);
        } else {
            enabledFeatures.add(feature);
        }
    }

    public Set<SeedMapperFeature> getEnabledFeaturesSnapshot() {
        return EnumSet.copyOf(enabledFeatures);
    }

    public void setEnabledFeatures(Set<SeedMapperFeature> features) {
        enabledFeatures.clear();
        if (features != null && !features.isEmpty()) {
            enabledFeatures.addAll(features);
        }
    }

    public void setOnlyFeatureEnabled(SeedMapperFeature feature) {
        enabledFeatures.clear();
        if (feature != null) {
            enabledFeatures.add(feature);
        }
    }

    public boolean isCompleted(String worldKey, SeedMapperFeature feature, int x, int z) {
        return completedFeatureEntries.contains(buildCompletionKey(worldKey, feature, x, z));
    }

    public void setCompleted(String worldKey, SeedMapperFeature feature, int x, int z, boolean completed) {
        String key = buildCompletionKey(worldKey, feature, x, z);
        if (completed) {
            completedFeatureEntries.add(key);
        } else {
            completedFeatureEntries.remove(key);
        }
    }

    private String buildCompletionKey(String worldKey, SeedMapperFeature feature, int x, int z) {
        return (worldKey == null ? "unknown" : worldKey) + "|" + feature.id() + "|" + x + "|" + z;
    }

    public void addDatapackLocatedMarker(String structureId, int dimension, int x, int z) {
        String sid = structureId == null ? "unknown:structure" : structureId.trim().toLowerCase(Locale.ROOT);
        datapackLocatedEntries.add(sid + "|" + dimension + "|" + x + "|" + z);
    }

    public Set<DatapackMarker> getDatapackLocatedMarkers(int dimension, int minX, int maxX, int minZ, int maxZ) {
        Set<DatapackMarker> result = new HashSet<>();
        for (String entry : datapackLocatedEntries) {
            String[] parts = entry.split("\\|");
            if (parts.length != 4) {
                continue;
            }
            try {
                int dim = Integer.parseInt(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                if (dim != dimension) {
                    continue;
                }
                if (x < minX || x > maxX || z < minZ || z > maxZ) {
                    continue;
                }
                result.add(new DatapackMarker(parts[0], dim, x, z));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public record DatapackMarker(String structureId, int dimension, int x, int z) {
    }

    public int getDatapackMarkerHash() {
        int importedHash = datapackEnabled ? SeedMapperImportedDatapackManager.getImportedDatapack(datapackCachePath).hash() : 0;
        return 31 * importedHash + datapackLocatedEntries.hashCode() + datapackStructureDisabled.hashCode();
    }

    @Override
    public String getKeyText(EnumOptionsMinimap option) {
        return MapSettingsManager.ERROR_STRING;
    }

    @Override
    public boolean getBooleanValue(EnumOptionsMinimap option) {
        return false;
    }

    @Override
    public void toggleBooleanValue(EnumOptionsMinimap option) {
    }

    @Override
    public String getListValue(EnumOptionsMinimap option) {
        return MapSettingsManager.ERROR_STRING;
    }

    @Override
    public void cycleListValue(EnumOptionsMinimap option) {
    }

    @Override
    public float getFloatValue(EnumOptionsMinimap option) {
        return 0;
    }

    @Override
    public void setFloatValue(EnumOptionsMinimap option, float value) {
    }

    private static void putMappedValue(Map<String, String> map, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null || value.isBlank()) {
            map.remove(key);
        } else {
            map.put(key, value.trim());
        }
    }

    private static void loadIntList(String raw, List<Integer> target) {
        target.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            try {
                target.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static String saveIntList(List<Integer> values) {
        return values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static void loadMap(String raw, Map<String, String> target) {
        target.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String entry : raw.split(";;")) {
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = unescape(entry.substring(0, equals));
            String value = unescape(entry.substring(equals + 1));
            if (!key.isBlank() && !value.isBlank()) {
                target.put(key, value);
            }
        }
    }

    private static String saveMap(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> escape(entry.getKey()) + "=" + escape(entry.getValue()))
                .reduce((left, right) -> left + ";;" + right)
                .orElse("");
    }

    private static void loadWorldSetMap(String raw, Map<String, Set<String>> target) {
        target.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String entry : raw.split(";;")) {
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = unescape(entry.substring(0, equals));
            String encodedValues = entry.substring(equals + 1);
            Set<String> values = new HashSet<>();
            if (!encodedValues.isBlank()) {
                for (String part : encodedValues.split(",")) {
                    String decoded = unescape(part);
                    if (!decoded.isBlank()) {
                        values.add(decoded);
                    }
                }
            }
            if (!key.isBlank() && !values.isEmpty()) {
                target.put(key, values);
            }
        }
    }

    private static String saveWorldSetMap(Map<String, Set<String>> values) {
        return values.entrySet().stream()
                .map(entry -> escape(entry.getKey()) + "=" + entry.getValue().stream().map(SeedMapperSettingsManager::escape).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + ";;" + right)
                .orElse("");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("=", "\\=").replace(";", "\\;").replace(",", "\\,");
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaping) {
                builder.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
