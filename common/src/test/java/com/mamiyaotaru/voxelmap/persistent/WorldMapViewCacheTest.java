package com.mamiyaotaru.voxelmap.persistent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapViewCacheTest {
    @Test void completedViewsAreReusedAndRetainedWhileReplacementLoads() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cache = new WorldMapViewCache<Integer, String>(1024, String::length, executor);
            AtomicInteger calls = new AtomicInteger();
            cache.get("self", 1, true, () -> { calls.incrementAndGet(); return "first"; });
            assertEquals("first", await(cache, "self", 1));
            for (int i = 0; i < 1000; i++) assertEquals("first", cache.get("self", 1, true, () -> { calls.incrementAndGet(); return "wrong"; }));
            assertEquals(1, calls.get()); assertEquals(1, cache.builds());
            assertEquals("first", cache.get("self", 2, false, () -> "second"));
            assertFalse(cache.matches("self", 2));
            cache.get("self", 2, true, () -> "second");
            assertEquals("second", await(cache, "self", 2));
        }
    }
    @Test void supersededRunningWorkCannotPublishOverNewView() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cache = new WorldMapViewCache<Integer, String>(1024, String::length, executor);
            cache.get("self", 1, true, () -> {
                entered.countDown();
                try { finish.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return "stale";
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            cache.get("self", 2, true, () -> "current");
            assertEquals("current", await(cache, "self", 2));
            finish.countDown();
            assertEquals("current", cache.get("self", 2, true, () -> "wrong"));
            cache.clear(); assertFalse(cache.matches("self", 2));
        } finally { finish.countDown(); }
    }
    @Test void byteBudgetDoesNotEvictActiveFullDetailLayersEveryFrame() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cache = new WorldMapViewCache<Integer, String>(1, String::length, executor);
            cache.beginFrame();
            cache.get("self", 1, true, () -> "full self"); await(cache, "self", 1);
            cache.get("shared", 1, true, () -> "full shared"); await(cache, "shared", 1);
            long builds = cache.builds();
            for (int i = 0; i < 100; i++) {
                cache.beginFrame();
                assertEquals("full self", cache.get("self", 1, true, () -> "wrong"));
                assertEquals("full shared", cache.get("shared", 1, true, () -> "wrong"));
            }
            assertEquals(builds, cache.builds());
        }
    }
    @Test void liveUpdatesFinishTheCurrentViewInsteadOfContinuouslyRestartingColdBuilds() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cache = new WorldMapViewCache<Integer, String>(1024, String::length, executor);
            java.util.function.BiPredicate<Integer, Integer> sameViewport = (a, b) -> a / 100 == b / 100;
            cache.get("self", 101, true, () -> {
                entered.countDown();
                try { finish.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException e) { return "cancelled"; }
                return "complete first snapshot";
            }, sameViewport);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            for (int version = 102; version <= 199; version++) cache.get("self", version, true, () -> "wrong", sameViewport);
            assertEquals(1, cache.builds()); finish.countDown();
            String value = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (value == null && System.nanoTime() < deadline) {
                value = cache.get("self", 199, false, () -> "wrong", sameViewport); Thread.sleep(1);
            }
            assertEquals("complete first snapshot", value);
            assertFalse(cache.matches("self", 199)); // Next refresh still knows it must incorporate newer data.
            cache.clear();
        } finally { finish.countDown(); }
    }
    @Test void alternatingCompletedZoomsReuseSnapshotsEvenBeforeSourceLoaderIsReady() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cache = new WorldMapViewCache<Integer, String>(1024, String::length, executor);
            cache.get("self", 1, true, () -> "wide"); await(cache, "self", 1);
            cache.get("self", 2, true, () -> "near"); await(cache, "self", 2);
            long builds = cache.builds();
            for (int i = 0; i < 100; i++) {
                assertTrue(cache.contains("self", 1));
                assertEquals("wide", cache.get("self", 1, false, () -> fail("Warm zoom must not rebuild")));
                assertEquals("near", cache.get("self", 2, true, () -> fail("Warm zoom must not rebuild")));
            }
            assertEquals(builds, cache.builds());
            // A source/style/version change gets its own key, never a false warm hit.
            assertFalse(cache.contains("self", 3));
            cache.get("self", 3, true, () -> "updated");
            assertEquals("updated", await(cache, "self", 3));
            cache.clear(); assertFalse(cache.contains("self", 1));
        }
    }
    @Test void historyEvictsLeastRecentlyUsedViewsAtMemoryBudget() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cache = new WorldMapViewCache<Integer, String>(8, String::length, executor);
            cache.get("self", 1, true, () -> "aaaa"); await(cache, "self", 1);
            cache.get("self", 2, true, () -> "bbbb"); await(cache, "self", 2);
            assertEquals("aaaa", cache.get("self", 1, true, () -> fail("Expected warm view")));
            cache.get("self", 3, true, () -> "cccc"); await(cache, "self", 3);
            assertFalse(cache.contains("self", 2));
            assertTrue(cache.contains("self", 1));
            assertEquals("cccc", cache.get("self", 2, false, () -> "wrong"));
            cache.setBudget(1);
            assertEquals("cccc", cache.get("self", 3, true, () -> "wrong"));
            assertFalse(cache.contains("self", 1));
            cache.clear();
        }
    }
    private static String await(WorldMapViewCache<Integer, String> cache, String layer, int key) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!cache.matches(layer, key) && System.nanoTime() < deadline) {
            cache.get(layer, key, true, () -> "unexpected duplicate"); Thread.sleep(1);
        }
        assertTrue(cache.matches(layer, key));
        return cache.get(layer, key, true, () -> "unexpected duplicate");
    }
}
