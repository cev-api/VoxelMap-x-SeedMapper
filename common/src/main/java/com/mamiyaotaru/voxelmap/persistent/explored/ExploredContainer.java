package com.mamiyaotaru.voxelmap.persistent.explored;

/**
 * holds up to 1024 {@link ExploredTile}s for a pyramid level which is addressed by Morton slot
 * serialized: header (8 ints) + presence bitmap (1024 bits) + present tiles packed
 * contiguously in the Morton-slot order so disk size is therefore {@code 160 + presentTiles*128} bytes,
 * scaling with explored tiles
 */
public final class ExploredContainer {
    public static final int MAGIC = 0x45585033; // "EXP3"
    public static final int FORMAT_VERSION = 3;
    public static final int TILES_PER_SIDE = 32;
    public static final int SLOTS = TILES_PER_SIDE * TILES_PER_SIDE; // 1024
    public static final int REGION_SHIFT = 5;     // 32 chunks per tile side
    public static final int CONTAINER_SHIFT = 5;  // 32 tiles per container side

    private static final int PRESENCE_BYTES = SLOTS / 8; // 128
    private static final int HEADER_BYTES = 8 * 4;

    private final int level;
    private final int containerX;
    private final int containerZ;
    private final ExploredTile[] slots = new ExploredTile[SLOTS]; // indexed by Morton code

    public ExploredContainer(int level, int containerX, int containerZ) {
        this.level = level;
        this.containerX = containerX;
        this.containerZ = containerZ;
    }

    public int level() {
        return level;
    }

    public int containerX() {
        return containerX;
    }

    public int containerZ() {
        return containerZ;
    }

    /** localTileX/localTileZ are 0..31 within the container, this returns null if absent */
    public ExploredTile getTile(int localTileX, int localTileZ) {
        return slots[MortonCode.encode(localTileX, localTileZ)];
    }

    public ExploredTile getOrCreateTile(int localTileX, int localTileZ) {
        int slot = MortonCode.encode(localTileX, localTileZ);
        ExploredTile t = slots[slot];
        if (t == null) {
            t = new ExploredTile();
            slots[slot] = t;
        }
        return t;
    }

    public void putTile(int localTileX, int localTileZ, ExploredTile tile) {
        slots[MortonCode.encode(localTileX, localTileZ)] = tile;
    }

    public boolean isEmpty() {
        for (ExploredTile t : slots) {
            if (t != null && !t.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public byte[] encode() {
        byte[] presence = new byte[PRESENCE_BYTES];
        int presentCount = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            ExploredTile t = slots[slot];
            if (t != null && !t.isEmpty()) {
                presence[slot >> 3] |= (byte) (1 << (slot & 7));
                presentCount++;
            }
        }

        byte[] out = new byte[HEADER_BYTES + PRESENCE_BYTES + presentCount * ExploredTile.BYTE_SIZE];
        int p = 0;
        p = writeInt(out, p, MAGIC);
        p = writeInt(out, p, FORMAT_VERSION);
        p = writeInt(out, p, level);
        p = writeInt(out, p, containerX);
        p = writeInt(out, p, containerZ);
        p = writeInt(out, p, REGION_SHIFT);
        p = writeInt(out, p, CONTAINER_SHIFT);
        p = writeInt(out, p, ExploredTile.BYTE_SIZE);
        System.arraycopy(presence, 0, out, p, PRESENCE_BYTES);
        p += PRESENCE_BYTES;
        for (int slot = 0; slot < SLOTS; slot++) {
            ExploredTile t = slots[slot];
            if (t != null && !t.isEmpty()) {
                byte[] tb = t.toBytes();
                System.arraycopy(tb, 0, out, p, ExploredTile.BYTE_SIZE);
                p += ExploredTile.BYTE_SIZE;
            }
        }
        return out;
    }

    public static ExploredContainer decode(byte[] data) {
        if (data.length < HEADER_BYTES + PRESENCE_BYTES) {
            throw new IllegalArgumentException("Explored container too short: " + data.length + " bytes");
        }
        int p = 0;
        int magic = readInt(data, p);
        p += 4;
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Not an explored container (bad magic 0x" + Integer.toHexString(magic) + ")");
        }
        int version = readInt(data, p);
        p += 4;
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported explored container version: " + version);
        }
        int level = readInt(data, p);
        p += 4;
        int cx = readInt(data, p);
        p += 4;
        int cz = readInt(data, p);
        p += 4;
        p += 4; // regionShift
        p += 4; // containerShift
        int tileByteSize = readInt(data, p);
        p += 4;
        if (tileByteSize <= 0) {
            throw new IllegalArgumentException("Bad explored container tile size: " + tileByteSize);
        }

        int presenceStart = p;
        p += PRESENCE_BYTES;

        int presentCount = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if ((data[presenceStart + (slot >> 3)] & (1 << (slot & 7))) != 0) {
                presentCount++;
            }
        }
        long expectedLength = (long) HEADER_BYTES + PRESENCE_BYTES + (long) presentCount * tileByteSize;
        if (data.length < expectedLength) {
            throw new IllegalArgumentException("Explored container truncated: expected " + expectedLength + " bytes, got " + data.length);
        }

        ExploredContainer c = new ExploredContainer(level, cx, cz);
        for (int slot = 0; slot < SLOTS; slot++) {
            if ((data[presenceStart + (slot >> 3)] & (1 << (slot & 7))) != 0) {
                byte[] tb = new byte[tileByteSize];
                System.arraycopy(data, p, tb, 0, tileByteSize);
                p += tileByteSize;
                c.slots[slot] = ExploredTile.fromBytes(tb);
            }
        }
        return c;
    }

    private static int writeInt(byte[] b, int p, int v) {
        b[p] = (byte) (v >>> 24);
        b[p + 1] = (byte) (v >>> 16);
        b[p + 2] = (byte) (v >>> 8);
        b[p + 3] = (byte) v;
        return p + 4;
    }

    private static int readInt(byte[] b, int p) {
        return ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
    }
}
