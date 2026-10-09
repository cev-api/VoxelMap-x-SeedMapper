package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.persistent.explored.ExploredCellQuery;
import com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore;
import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.ChunkBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** Retains 128-cell meshes across pans; a one-cell halo preserves connections at tile boundaries. */
final class WorldMapTrailTiles {
    private static final int SIDE = 128;
    private static final long BUDGET = 32L << 20;
    private final LinkedHashMap<Key, ExploredLineMesher.Result> cache = new LinkedHashMap<>(64, .75F, true);
    private long bytes;
    private long generation;
    private record Key(long store, long version, int size, int x0, int z0, int x1, int z1, int hx0, int hz0, int hx1, int hz1) { }

    /** Exact/fixed-resolution path: visits occupied tiles only, including neighbours in each halo. */
    ExploredLineMesher.Result buildSparse(com.mamiyaotaru.voxelmap.util.SparseCellGrid cells, int size) {
        List<ExploredLineMesher.Result> parts = new ArrayList<>();
        var keys = cells.tileKeys();
        int completed = 0;
        WorldMapProgress.report("Meshing tiles", 0, keys.size());
        for (long key : keys) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int x = (int) (key >> 32) * SIDE, z = (int) key * SIDE;
            parts.add(clip(ExploredLineMesher.build(cells.tile(key, 1), size, true, 0, 0), x, z, x + SIDE - 1, z + SIDE - 1, size));
            WorldMapProgress.report("Meshing tiles", ++completed, keys.size());
        }
        return combine(parts);
    }

    private static ExploredLineMesher.Result combine(List<ExploredLineMesher.Result> parts) {
        int segments = 0, nodes = 0;
        for (var part : parts) { segments += part.segmentCount(); nodes += part.nodeCount(); }
        float[] lines = new float[segments * 4], centers = new float[nodes * 2];
        boolean[] linked = new boolean[nodes];
        int so = 0, no = 0;
        for (var part : parts) {
            System.arraycopy(part.segments(), 0, lines, so * 4, part.segmentCount() * 4);
            System.arraycopy(part.nodeCoords(), 0, centers, no * 2, part.nodeCount() * 2);
            System.arraycopy(part.nodeLinked(), 0, linked, no, part.nodeCount());
            so += part.segmentCount(); no += part.nodeCount();
        }
        return new ExploredLineMesher.Result(lines, segments, centers, linked, nodes);
    }

    ExploredLineMesher.Result build(ExploredCellQuery query, CellGrid cells, int size, long snapshotVersion) {
        List<ExploredLineMesher.Result> parts = new ArrayList<>();
        int segments = 0, nodes = 0;
        long token;
        synchronized (this) { token = generation; }
        for (int tz = Math.floorDiv(cells.minZ, SIDE); tz <= Math.floorDiv(cells.minZ + cells.height - 1, SIDE); tz++) {
            for (int tx = Math.floorDiv(cells.minX, SIDE); tx <= Math.floorDiv(cells.minX + cells.width - 1, SIDE); tx++) {
                int x0 = Math.max(cells.minX, tx * SIDE), z0 = Math.max(cells.minZ, tz * SIDE);
                int x1 = Math.min(cells.minX + cells.width - 1, tx * SIDE + SIDE - 1);
                int z1 = Math.min(cells.minZ + cells.height - 1, tz * SIDE + SIDE - 1);
                int hx0 = Math.max(cells.minX, x0 - 1), hz0 = Math.max(cells.minZ, z0 - 1);
                int hx1 = Math.min(cells.minX + cells.width - 1, x1 + 1), hz1 = Math.min(cells.minZ + cells.height - 1, z1 + 1);
                ChunkBounds source = new ChunkBounds(hx0 * size, (hx1 + 1) * size - 1, hz0 * size, (hz1 + 1) * size - 1);
                long version = query.store().versionInBounds(source, ExploredDiskStore.selectLevelForCellSize(query.cellSize()));
                Key key = new Key(query.store().identity(), version, size, x0, z0, x1, z1, hx0, hz0, hx1, hz1);
                ExploredLineMesher.Result result;
                synchronized (this) { result = cache.get(key); }
                if (result == null) {
                    CellGrid local = new CellGrid(hx0, hz0, hx1 - hx0 + 1, hz1 - hz0 + 1);
                    for (int z = hz0; z <= hz1; z++) for (int x = hx0; x <= hx1; x++) if (cells.get(x, z)) local.mark(x, z);
                    result = clip(ExploredLineMesher.build(local, size, true, 0, 0), x0, z0, x1, z1, size);
                    synchronized (this) {
                        if (generation == token && query.store().versionInBounds(query.bounds(), ExploredDiskStore.selectLevelForCellSize(query.cellSize())) == snapshotVersion && !cache.containsKey(key)) {
                            cache.put(key, result); bytes += bytes(result);
                            var iterator = cache.entrySet().iterator();
                            while (iterator.hasNext() && (bytes > BUDGET || cache.size() > 512)) {
                                bytes -= bytes(iterator.next().getValue()); iterator.remove();
                            }
                        }
                    }
                }
                parts.add(result); segments += result.segmentCount(); nodes += result.nodeCount();
            }
        }
        float[] lines = new float[segments * 4], centers = new float[nodes * 2];
        boolean[] linked = new boolean[nodes];
        int so = 0, no = 0;
        for (var part : parts) {
            System.arraycopy(part.segments(), 0, lines, so * 4, part.segmentCount() * 4);
            System.arraycopy(part.nodeCoords(), 0, centers, no * 2, part.nodeCount() * 2);
            System.arraycopy(part.nodeLinked(), 0, linked, no, part.nodeCount());
            so += part.segmentCount(); no += part.nodeCount();
        }
        return new ExploredLineMesher.Result(lines, segments, centers, linked, nodes);
    }

    private static ExploredLineMesher.Result clip(ExploredLineMesher.Result mesh, int x0, int z0, int x1, int z1, int size) {
        float minX = (float) x0 * size * 16, maxX = (float) (x1 + 1) * size * 16;
        float minZ = (float) z0 * size * 16, maxZ = (float) (z1 + 1) * size * 16;
        float[] lines = new float[mesh.segmentCount() * 4], nodes = new float[mesh.nodeCount() * 2];
        boolean[] linked = new boolean[mesh.nodeCount()];
        int sc = 0, nc = 0;
        for (int i = 0; i < mesh.segmentCount(); i++) {
            float ax = mesh.segments()[i * 4], az = mesh.segments()[i * 4 + 1];
            float bx = mesh.segments()[i * 4 + 2], bz = mesh.segments()[i * 4 + 3];
            float dx = bx - ax, dz = bz - az, start = 0, end = 1;
            boolean outside = false;
            for (int edge = 0; edge < 4; edge++) {
                float p = switch (edge) { case 0 -> -dx; case 1 -> dx; case 2 -> -dz; default -> dz; };
                float q = switch (edge) { case 0 -> ax - minX; case 1 -> maxX - ax; case 2 -> az - minZ; default -> maxZ - az; };
                if (p == 0) { if (q < 0) { outside = true; break; } }
                else { float t = q / p; if (p < 0) start = Math.max(start, t); else end = Math.min(end, t); }
            }
            if (outside || start >= end) continue;
            lines[sc * 4] = ax + start * dx; lines[sc * 4 + 1] = az + start * dz;
            lines[sc * 4 + 2] = ax + end * dx; lines[sc * 4 + 3] = az + end * dz; sc++;
        }
        for (int i = 0; i < mesh.nodeCount(); i++) {
            float x = mesh.nodeCoords()[i * 2], z = mesh.nodeCoords()[i * 2 + 1];
            if (x < minX || x >= maxX || z < minZ || z >= maxZ) continue;
            nodes[nc * 2] = x; nodes[nc * 2 + 1] = z; linked[nc++] = mesh.nodeLinked()[i];
        }
        return new ExploredLineMesher.Result(Arrays.copyOf(lines, sc * 4), sc, Arrays.copyOf(nodes, nc * 2), Arrays.copyOf(linked, nc), nc);
    }

    private static long bytes(ExploredLineMesher.Result r) { return (long) r.segments().length * 4 + (long) r.nodeCoords().length * 4 + r.nodeLinked().length; }
    synchronized void clear() { generation++; cache.clear(); bytes = 0; }
}
