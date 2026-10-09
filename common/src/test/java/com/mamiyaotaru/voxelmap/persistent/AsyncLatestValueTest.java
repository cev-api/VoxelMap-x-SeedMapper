package com.mamiyaotaru.voxelmap.persistent;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AsyncLatestValueTest {
    @Test void pollingNeverWaitsForNativeLockOrQueuesEveryCursorPosition() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        var render = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try {
            var values = new AsyncLatestValue<Integer, String>(worker, 2);
            assertNull(render.submit(() -> values.get(1, () -> {
                calls.incrementAndGet(); entered.countDown();
                try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                return "one";
            })).get(1, TimeUnit.SECONDS));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            for (int i = 2; i < 100; i++) {
                final int key = i;
                assertNull(render.submit(() -> values.get(key, () -> { calls.incrementAndGet(); return "extra"; }))
                        .get(1, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get());
            release.countDown();
            worker.submit(() -> {}).get(1, TimeUnit.SECONDS);
            assertEquals("one", render.submit(() -> values.get(1, () -> "unexpected")).get(1, TimeUnit.SECONDS));
        } finally { release.countDown(); worker.shutdownNow(); render.shutdownNow(); }
    }

    @Test void completedOldLookupDoesNotBecomeTheNewPositionsValue() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try {
            var values = new AsyncLatestValue<Integer, String>(worker, 1);
            assertNull(values.get(1, () -> "one"));
            worker.submit(() -> {}).get(1, TimeUnit.SECONDS);
            assertNull(values.get(2, () -> "two"));
            worker.submit(() -> {}).get(1, TimeUnit.SECONDS);
            assertEquals("two", values.get(2, () -> "unexpected"));
            assertNull(values.get(1, () -> "one again"));
        } finally { worker.shutdownNow(); }
    }
}
