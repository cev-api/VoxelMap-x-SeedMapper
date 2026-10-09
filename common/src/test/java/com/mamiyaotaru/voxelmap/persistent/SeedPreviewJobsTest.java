package com.mamiyaotaru.voxelmap.persistent;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SeedPreviewJobsTest {
    @Test void cancellingCoordinatorInterruptsNativeBandsAndAllowsNewWork() throws Exception {
        var coordinator = Executors.newSingleThreadExecutor();
        var bands = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        try {
            Future<?> band = bands.submit(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException ex) { interrupted.countDown(); Thread.currentThread().interrupt(); }
                SeedPreviewJobs.checkCancelled();
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            var awaiting = new CountDownLatch(1);
            Future<?> preview = coordinator.submit(() -> {
                awaiting.countDown(); SeedPreviewJobs.await(List.of(band));
            });
            assertTrue(awaiting.await(1, TimeUnit.SECONDS));
            preview.cancel(true);
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertTrue(band.isCancelled());
            assertEquals("ready", coordinator.submit(() -> "ready").get(1, TimeUnit.SECONDS));
            assertEquals("ready", bands.submit(() -> "ready").get(1, TimeUnit.SECONDS));
        } finally { coordinator.shutdownNow(); bands.shutdownNow(); }
    }

    @Test void aFailedBandCancelsRemainingBands() {
        var failed = CompletableFuture.failedFuture(new IllegalStateException("native failure"));
        var pending = new CompletableFuture<Void>();
        assertThrows(RuntimeException.class, () -> SeedPreviewJobs.await(List.of(failed, pending)));
        assertTrue(pending.isCancelled());
    }

    @Test void completedBandsRemainAvailable() {
        var completed = CompletableFuture.completedFuture(42);
        SeedPreviewJobs.await(List.of(completed));
        assertFalse(completed.isCancelled());
        assertEquals(42, completed.join());
    }
}
