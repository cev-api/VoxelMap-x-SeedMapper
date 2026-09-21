package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The names accepted by SeedMapper's custom structure-salt option.
 *
 * <p>The native provider receives numeric cubiomes structure ids, while the
 * user-facing seed syntax is intentionally kept readable.  Keeping this
 * table in one place also means aliases used by the command parser and the
 * native callback cannot drift apart.</p>
 */
public final class SeedMapperStructureConfig {
    private static final Map<String, Integer> STRUCTURES = createStructureMap();

    private SeedMapperStructureConfig() {
    }

    public static Integer resolveId(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = normalize(rawName);
        Integer id = STRUCTURES.get(name);
        if (id != null) {
            return id;
        }
        if (name.startsWith("minecraft_")) {
            return STRUCTURES.get(name.substring("minecraft_".length()));
        }
        return null;
    }

    public static String normalize(String rawName) {
        String name = rawName.trim().toLowerCase(Locale.ROOT);
        name = name.replace(':', '_').replace('-', '_').replace(' ', '_');
        while (name.contains("__")) {
            name = name.replace("__", "_");
        }
        return name;
    }

    public static Map<String, Integer> knownStructures() {
        return STRUCTURES;
    }

    public static Map<Integer, Integer> resolveSalts(Map<String, Integer> namedSalts) {
        if (namedSalts == null || namedSalts.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : namedSalts.entrySet()) {
            Integer structureId = resolveId(entry.getKey());
            if (structureId != null && entry.getValue() != null) {
                resolved.put(structureId, entry.getValue());
            }
        }
        return Map.copyOf(resolved);
    }

    public static int hashSalts(Map<String, Integer> namedSalts) {
        if (namedSalts == null || namedSalts.isEmpty()) {
            return 0;
        }
        int hash = 1;
        for (Map.Entry<String, Integer> entry : namedSalts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            hash = 31 * hash + normalize(entry.getKey()).hashCode();
            hash = 31 * hash + (entry.getValue() == null ? 0 : entry.getValue());
        }
        return hash;
    }

    private static Map<String, Integer> createStructureMap() {
        Map<String, Integer> structures = new LinkedHashMap<>();
        put(structures, "desert_pyramid", Cubiomes.Desert_Pyramid());
        put(structures, "jungle_pyramid", Cubiomes.Jungle_Pyramid());
        put(structures, "jungle_temple", Cubiomes.Jungle_Pyramid());
        put(structures, "swamp_hut", Cubiomes.Swamp_Hut());
        put(structures, "stronghold", Cubiomes.Stronghold());
        put(structures, "igloo", Cubiomes.Igloo());
        put(structures, "village", Cubiomes.Village());
        put(structures, "ocean_ruin", Cubiomes.Ocean_Ruin());
        put(structures, "shipwreck", Cubiomes.Shipwreck());
        put(structures, "monument", Cubiomes.Monument());
        put(structures, "mansion", Cubiomes.Mansion());
        put(structures, "pillager_outpost", Cubiomes.Outpost());
        put(structures, "outpost", Cubiomes.Outpost());
        put(structures, "ruined_portal", Cubiomes.Ruined_Portal());
        put(structures, "ruined_portal_nether", Cubiomes.Ruined_Portal_N());
        put(structures, "ruined_portal_n", Cubiomes.Ruined_Portal_N());
        put(structures, "ancient_city", Cubiomes.Ancient_City());
        put(structures, "buried_treasure", Cubiomes.Treasure());
        put(structures, "treasure", Cubiomes.Treasure());
        put(structures, "mineshaft", Cubiomes.Mineshaft());
        put(structures, "desert_well", Cubiomes.Desert_Well());
        put(structures, "geode", Cubiomes.Geode());
        put(structures, "fortress", Cubiomes.Fortress());
        put(structures, "bastion_remnant", Cubiomes.Bastion());
        put(structures, "bastion", Cubiomes.Bastion());
        put(structures, "nether_fossil", Cubiomes.Nether_Fossil());
        put(structures, "end_city", Cubiomes.End_City());
        put(structures, "end_gateway", Cubiomes.End_Gateway());
        put(structures, "end_island", Cubiomes.End_Island());
        put(structures, "trail_ruins", Cubiomes.Trail_Ruins());
        put(structures, "trial_chambers", Cubiomes.Trial_Chambers());
        put(structures, "abandoned_camp", Cubiomes.Abandoned_Camp());
        return Map.copyOf(structures);
    }

    private static void put(Map<String, Integer> target, String name, int structureId) {
        target.put(name, structureId);
    }
}
