package com.mamiyaotaru.voxelmap.persistent.explored;

/**
 * 4-level mip pyramid of {@link ExploredTile}s, level 0 stores per-chunk bits, each higher level's
 * bit means "the corresponding lower-level tile is non-empty", setting a chunk
 * sets its level-0 bit and propagates "non-empty" upward only as far as needed
 *
 */
public final class ExploredPyramid {
    public static final int LEVELS = 4; // 0..3
    private static final int TILE_SHIFT = 5; // 32 chunks per tile side at level 0
    private static final int TILE_MASK = (1 << TILE_SHIFT) - 1; // 31

    private final ExploredTileMap[] levels;

    public ExploredPyramid() {
        levels = new ExploredTileMap[LEVELS];
        for (int l = 0; l < LEVELS; l++) {
            levels[l] = new ExploredTileMap();
        }
    }

    static int tileX(int level, int chunkX) {
        return chunkX >> (TILE_SHIFT * (level + 1));
    }

    static int tileZ(int level, int chunkZ) {
        return chunkZ >> (TILE_SHIFT * (level + 1));
    }

    static int bitX(int level, int chunkX) {
        return (chunkX >> (TILE_SHIFT * level)) & TILE_MASK;
    }

    static int bitZ(int level, int chunkZ) {
        return (chunkZ >> (TILE_SHIFT * level)) & TILE_MASK;
    }

    static long key(int tileX, int tileZ) {
        return ((long) tileX << 32) ^ (tileZ & 0xFFFFFFFFL);
    }

    public boolean isChunkExplored(int chunkX, int chunkZ) {
        ExploredTile t = levels[0].get(key(tileX(0, chunkX), tileZ(0, chunkZ)));
        return t != null && t.get(bitX(0, chunkX), bitZ(0, chunkZ));
    }

    public interface TileChangeListener {
        void onTileChanged(int level, int tileX, int tileZ);
    }

    private static final TileChangeListener NO_OP = (level, tileX, tileZ) -> { };

    public void setChunk(int chunkX, int chunkZ) {
        setChunk(chunkX, chunkZ, NO_OP);
    }

    public void setChunk(int chunkX, int chunkZ, TileChangeListener listener) {
        int tx = tileX(0, chunkX);
        int tz = tileZ(0, chunkZ);
        ExploredTile tile = levels[0].computeIfAbsent(key(tx, tz), k -> new ExploredTile());
        boolean tileWasEmpty = tile.isEmpty();
        boolean changed = tile.set(bitX(0, chunkX), bitZ(0, chunkZ));
        if (changed) {
            listener.onTileChanged(0, tx, tz);
            if (tileWasEmpty) {
                propagate(1, tx, tz, listener);
            }
        }
    }

    public void mergeTile(int level, int tileX, int tileZ, ExploredTile tile) {
        levels[level].computeIfAbsent(key(tileX, tileZ), k -> new ExploredTile()).orFrom(tile);
    }

    private void propagate(int level, int childTileX, int childTileZ, TileChangeListener listener) {
        while (level < LEVELS) {
            int parentTileX = childTileX >> TILE_SHIFT;
            int parentTileZ = childTileZ >> TILE_SHIFT;
            int bx = childTileX & TILE_MASK;
            int bz = childTileZ & TILE_MASK;
            ExploredTile parent = levels[level].computeIfAbsent(key(parentTileX, parentTileZ), k -> new ExploredTile());
            boolean parentWasEmpty = parent.isEmpty();
            boolean changed = parent.set(bx, bz);
            if (changed) {
                listener.onTileChanged(level, parentTileX, parentTileZ);
            }
            if (!changed || !parentWasEmpty) {
                return; // bit already set, or parent was already non-empty -> ancestors already marked
            }
            childTileX = parentTileX;
            childTileZ = parentTileZ;
            level++;
        }
    }

    public ExploredTile tileAt(int level, int tileX, int tileZ) {
        return levels[level].get(key(tileX, tileZ));
    }

    public int tileCount(int level) {
        return levels[level].size();
    }

    public void forEachTile(int level, ExploredTileMap.EntryVisitor visitor) {
        levels[level].forEach(visitor);
    }
}
