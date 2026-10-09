package com.mamiyaotaru.voxelmap.persistent.explored;

import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.ChunkBounds;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExploredStoreViewportTest {
    @TempDir Path directory;

    @Test void rectangularQueriesMatchReferenceAtEveryIntermediateLod() {
        ExploredDiskStore store = new ExploredDiskStore(directory);
        Random random = new Random(991);
        Set<Long> chunks = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            int x = random.nextInt(1024) - 512, z = random.nextInt(1024) - 512;
            store.setChunk(x, z); chunks.add(((long) x << 32) | (z & 0xffffffffL));
        }
        ChunkBounds view = new ChunkBounds(-333, 277, -91, 143);
        for (int size : new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024}) {
            CellGrid actual = store.cellsInBounds(view, size);
            CellGrid expected = new CellGrid(actual.minX, actual.minZ, actual.width, actual.height);
            int coverage = size;
            for (long packed : chunks) {
                int x = (int) (packed >> 32), z = (int) packed;
                int bx = Math.floorDiv(x, coverage) * coverage, bz = Math.floorDiv(z, coverage) * coverage;
                if (bx <= view.maxX() && bx + coverage - 1 >= view.minX() && bz <= view.maxZ() && bz + coverage - 1 >= view.minZ())
                    expected.mark(Math.floorDiv(x, size), Math.floorDiv(z, size));
            }
            assertArrayEquals(expected.cells, actual.cells, "cell size " + size);
        }
    }

    @Test void noOpAndOffscreenUpdatesLeaveVisibleVersionUnchanged() {
        ExploredDiskStore store = new ExploredDiskStore(directory);
        ChunkBounds bounds = new ChunkBounds(0, 63, 0, 63);
        store.setChunk(12, 24);
        long version = store.versionInBounds(bounds, 0);
        store.setChunk(12, 24);
        assertEquals(version, store.versionInBounds(bounds, 0));
        store.setChunk(1000, 1000);
        assertEquals(version, store.versionInBounds(bounds, 0));
        store.setChunk(10000, 10000);
        assertEquals(version, store.versionInBounds(bounds, 0));
        store.setChunk(13, 24);
        assertTrue(store.versionInBounds(bounds, 0) > version);
    }

    @Test void flushMergesUnloadedHistoryAndRetriesFailedWrites() throws Exception {
        ExploredDiskStore first = new ExploredDiskStore(directory);
        first.setChunk(-3, -5); first.flush();
        ExploredDiskStore writer = new ExploredDiskStore(directory);
        writer.setChunk(-4, -6); writer.flush();
        assertFalse(writer.hasDirty());
        ExploredDiskStore reader = new ExploredDiskStore(directory);
        reader.loadContainer(0, -1, -1);
        assertTrue(reader.isChunkExplored(-3, -5)); assertTrue(reader.isChunkExplored(-4, -6));
        Path blocked = directory.resolve("blocked"); Files.writeString(blocked, "file");
        ExploredDiskStore retry = new ExploredDiskStore(blocked);
        retry.setChunk(99, 100); retry.flush(); assertTrue(retry.hasDirty());
        Files.delete(blocked); retry.flush(); assertFalse(retry.hasDirty());
        ExploredDiskStore saved = new ExploredDiskStore(blocked); saved.loadContainer(0, 0, 0);
        assertTrue(saved.isChunkExplored(99, 100));
    }

    @Test void residencyEvictsCleanDataButRetainsDirtyDataAndReloadsIt() {
        ExploredDiskStore writer = new ExploredDiskStore(directory);
        writer.setChunk(1, 1); writer.flush();
        ExploredDiskStore store = new ExploredDiskStore(directory);
        store.loadContainer(0, 0, 0); store.setChunk(2, 2);
        for (int x = 1; x < 600; x++) store.loadContainer(0, x, 0);
        assertTrue(store.isContainerLoaded(0, 0, 0)); assertTrue(store.isChunkExplored(2, 2));
        store.flush();
        for (int x = 600; x < 1200; x++) store.loadContainer(0, x, 0);
        assertFalse(store.isContainerLoaded(0, 0, 0));
        store.loadContainer(0, 0, 0);
        assertTrue(store.isChunkExplored(1, 1)); assertTrue(store.isChunkExplored(2, 2));
    }

    @Test void concurrentExplorationAndFlushCannotLoseNewBits() throws Exception {
        ExploredDiskStore store = new ExploredDiskStore(directory);
        store.setChunk(0, 0);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer = workers.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int i = 0; i < 8000; i++) store.setChunk(i % 1024, i / 1024);
            });
            var saver = workers.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int i = 0; i < 30; i++) store.flush();
            });
            start.countDown(); writer.get(5, java.util.concurrent.TimeUnit.SECONDS); saver.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        store.flush();
        ExploredDiskStore reloaded = new ExploredDiskStore(directory); reloaded.loadContainer(0, 0, 0);
        for (int i = 0; i < 8000; i++) assertTrue(reloaded.isChunkExplored(i % 1024, i / 1024), "chunk " + i);
    }

    @Test void hashRemovalPreservesEverySurvivingProbeChain() {
        ExploredTileMap map = new ExploredTileMap();
        for (long key = 0; key < 5000; key++) map.computeIfAbsent(key, ignored -> new ExploredTile());
        for (long key = 0; key < 5000; key += 3) assertTrue(map.remove(key));
        for (long key = 0; key < 5000; key++) if (key % 3 == 0) assertNull(map.get(key)); else assertNotNull(map.get(key));
        assertEquals(3333, map.size());
    }

    @Test void summariesInvalidateWhenSourceBitsChange() {
        ExploredTile tile = new ExploredTile(); tile.set(0, 0);
        ExploredTile first = tile.reduced(2); assertTrue(first.get(0, 0));
        tile.set(31, 31); assertTrue(tile.reduced(2).get(7, 7)); assertFalse(first.get(7, 7));
    }
}
