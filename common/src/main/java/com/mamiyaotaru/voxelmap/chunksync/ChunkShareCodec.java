package com.mamiyaotaru.voxelmap.chunksync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * reads/writes the portable chunk share text format
 */
public final class ChunkShareCodec {
    public static final String HEADER = "# voxelmap-chunks v1";
    public static final String EXPLORED = "explored";

    private static final char EXPLORED_CODE = 'e';
    private static final String[] CATEGORY_NAMES = {"new", "old", "block_exploit", "being_updated", "old_generation"};
    private static final char[] CATEGORY_CODES = {'N', 'O', 'B', 'U', 'G'};

    /** {@code explored} and {@code newold} hold coordinates packed as {@code (x<<32)^z}. */
    public record Payload(String player, long[] explored, Map<String, long[]> newold) {
    }

    private ChunkShareCodec() {
    }

    public static String write(Payload p) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        if (p.player() != null && !p.player().isBlank()) {
            sb.append("player ").append(p.player().trim()).append('\n');
        }
        appendCoords(sb, EXPLORED_CODE, p.explored());
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            long[] coords = p.newold() == null ? null : p.newold().get(CATEGORY_NAMES[i]);
            appendCoords(sb, CATEGORY_CODES[i], coords);
        }
        return sb.toString();
    }

    private static void appendCoords(StringBuilder sb, char code, long[] coords) {
        if (coords == null) {
            return;
        }
        for (long c : coords) {
            sb.append(code).append(' ').append((int) (c >> 32)).append(',').append((int) c).append('\n');
        }
    }

    public static Payload parse(List<String> lines) {
        String player = null;
        List<Long> explored = new ArrayList<>();
        Map<String, List<Long>> newold = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            if (line.startsWith("player ")) {
                player = line.substring("player ".length()).trim();
                continue;
            }
            int space = line.indexOf(' ');
            if (space != 1) {
                continue;
            }
            List<Long> bucket = bucketFor(line.charAt(0), explored, newold);
            if (bucket == null) {
                continue;
            }
            long packed;
            String coord = line.substring(2);
            int comma = coord.indexOf(',');
            if (comma <= 0) {
                continue;
            }
            try {
                int x = Integer.parseInt(coord.substring(0, comma).trim());
                int z = Integer.parseInt(coord.substring(comma + 1).trim());
                packed = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            } catch (NumberFormatException ignored) {
                continue;
            }
            bucket.add(packed);
        }
        Map<String, long[]> newoldArrays = new LinkedHashMap<>();
        newold.forEach((name, list) -> newoldArrays.put(name, toArray(list)));
        return new Payload(player, toArray(explored), newoldArrays);
    }

    private static List<Long> bucketFor(char code, List<Long> explored, Map<String, List<Long>> newold) {
        if (code == EXPLORED_CODE) {
            return explored;
        }
        for (int i = 0; i < CATEGORY_CODES.length; i++) {
            if (CATEGORY_CODES[i] == code) {
                return newold.computeIfAbsent(CATEGORY_NAMES[i], k -> new ArrayList<>());
            }
        }
        return null;
    }

    private static long[] toArray(List<Long> list) {
        long[] array = new long[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
