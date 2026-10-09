package com.mamiyaotaru.voxelmap.util;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collection;

/** A viewport snapshot whose storage depends on occupied tiles, never on the distance between them. */
public final class SparseCellGrid {
    public static final int SIDE = 128;
    private record TileKey(long value) {
        @Override public int hashCode() {
            // Long.hashCode(x<<32|z) collapses diagonal trails into one bucket.
            long hash = value ^ (value >>> 33);
            hash *= 0xff51afd7ed558ccdL;
            hash ^= hash >>> 33;
            return (int) (hash ^ (hash >>> 32));
        }
    }
    private final Map<TileKey, BitSet> tiles = new LinkedHashMap<>();

    public static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    public Collection<Long> tileKeys() { return tiles.keySet().stream().map(TileKey::value).toList(); }
    public void mark(int x, int z) {
        tiles.computeIfAbsent(new TileKey(key(Math.floorDiv(x, SIDE), Math.floorDiv(z, SIDE))), ignored -> new BitSet(SIDE * SIDE))
                .set(Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE));
    }
    public boolean get(int x, int z) {
        BitSet tile = tiles.get(new TileKey(key(Math.floorDiv(x, SIDE), Math.floorDiv(z, SIDE))));
        return tile != null && tile.get(Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE));
    }
    public CellGrid tile(long key, int halo) {
        int x0 = (int) (key >> 32) * SIDE, z0 = (int) key * SIDE;
        CellGrid result = new CellGrid(x0 - halo, z0 - halo, SIDE + halo * 2, SIDE + halo * 2);
        int minTx = Math.floorDiv(result.minX, SIDE), maxTx = Math.floorDiv(result.minX + result.width - 1, SIDE);
        int minTz = Math.floorDiv(result.minZ, SIDE), maxTz = Math.floorDiv(result.minZ + result.height - 1, SIDE);
        for (int tz = minTz; tz <= maxTz; tz++) for (int tx = minTx; tx <= maxTx; tx++) {
            BitSet bits = tiles.get(new TileKey(key(tx, tz)));
            if (bits == null) continue;
            int bx = tx * SIDE, bz = tz * SIDE;
            int firstX = Math.max(0, result.minX - bx), lastX = Math.min(SIDE, result.minX + result.width - bx);
            int firstZ = Math.max(0, result.minZ - bz), lastZ = Math.min(SIDE, result.minZ + result.height - bz);
            for (int z = firstZ; z < lastZ; z++) {
                int end = z * SIDE + lastX;
                for (int bit = bits.nextSetBit(z * SIDE + firstX); bit >= 0 && bit < end; bit = bits.nextSetBit(bit + 1))
                    result.mark(bx + bit % SIDE, bz + z);
            }
        }
        return result;
    }
    public void subtract(SparseCellGrid other) {
        for (var entry : tiles.entrySet()) {
            BitSet mask = other.tiles.get(entry.getKey());
            if (mask != null) entry.getValue().andNot(mask);
        }
        tiles.values().removeIf(BitSet::isEmpty);
    }
    public long count() { long count = 0; for (BitSet tile : tiles.values()) count += tile.cardinality(); return count; }
}
