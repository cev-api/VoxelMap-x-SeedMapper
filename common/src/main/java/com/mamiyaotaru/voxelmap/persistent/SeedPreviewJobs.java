package com.mamiyaotaru.voxelmap.persistent;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Propagate preview cancellation through the coordinator to every native sampling band. */
final class SeedPreviewJobs {
    private SeedPreviewJobs() { }

    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Seed preview superseded");
    }

    static void await(List<? extends Future<?>> futures) {
        try {
            for (Future<?> future : futures) future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Seed preview superseded");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof CancellationException cancelled) throw cancelled;
            throw new RuntimeException("SeedMap preview sampling failed", failed.getCause());
        } finally {
            for (Future<?> future : futures) if (!future.isDone()) future.cancel(true);
        }
    }
}
