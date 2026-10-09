package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

public final class ThreadManager {
    private static final long SAVE_FLUSH_TIMEOUT_SECONDS = 5L;
    static final int concurrentThreads = Math.min(Math.max(Runtime.getRuntime().availableProcessors() / 2, 1), 4);
    static final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    public static final ThreadPoolExecutor executorService = new ThreadPoolExecutor(concurrentThreads, concurrentThreads, 60L, TimeUnit.SECONDS, queue);
    // Visible overlay loading and meshing never wait behind terrain migration/compression.
    public static final ThreadPoolExecutor overlayExecutorService = new ThreadPoolExecutor(2, 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    public static final ThreadPoolExecutor viewExecutorService = new ThreadPoolExecutor(2, 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    public static ThreadPoolExecutor saveExecutorService = createSaveExecutor();
    private static volatile boolean saveShutdownInProgress;
    private static final AtomicInteger skippedSaveTasks = new AtomicInteger();

    private ThreadManager() {}

    public static void emptyQueue() {
        // View changes must not cancel unrelated region updates, compression, or migrations.
        executorService.purge();
        viewExecutorService.purge();
    }

    public static void flushSaveQueue() {
        ThreadPoolExecutor executor = saveExecutorService;
        saveShutdownInProgress = true;
        int queuedAtStart = executor.getQueue().size();
        int activeAtStart = executor.getActiveCount();
        VoxelConstants.getLogger().info("Flushing map save queue (queued: {}, active: {})", queuedAtStart, activeAtStart);
        try {
            executor.shutdown();
            if (!executor.awaitTermination(SAVE_FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                int queuedBeforeCancel = executor.getQueue().size();
                int cancelled = executor.shutdownNow().size();
                VoxelConstants.getLogger().warn("Map save flush timed out after {}s; cancelling remaining saves (queued before cancel: {}, cancelled: {})",
                        SAVE_FLUSH_TIMEOUT_SECONDS, queuedBeforeCancel, cancelled);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            VoxelConstants.getLogger().warn("Interrupted while flushing map save queue; continuing shutdown.");
        } catch (RuntimeException e) {
            VoxelConstants.getLogger().warn("Unexpected error while flushing map save queue; continuing shutdown.", e);
        }
        int skipped = skippedSaveTasks.getAndSet(0);
        VoxelConstants.getLogger().info("Map save flush finished (terminated: {}, skipped submissions: {})", executor.isTerminated(), skipped);
        saveExecutorService = createSaveExecutor();
        saveShutdownInProgress = false;
    }

    public static boolean submitSaveTask(Runnable task, String description) {
        if (task == null) {
            return false;
        }
        ThreadPoolExecutor executor = saveExecutorService;
        if (saveShutdownInProgress || executor.isShutdown() || executor.isTerminated()) {
            int skipped = skippedSaveTasks.incrementAndGet();
            if (skipped <= 5 || VoxelConstants.DEBUG) {
                VoxelConstants.getLogger().debug("Skipping save task during shutdown: {} (skipped count: {})", description, skipped);
            }
            return false;
        }

        Runnable guarded = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                VoxelConstants.getLogger().error("Save task failed: {}", description, e);
            } catch (Error e) {
                VoxelConstants.getLogger().error("Severe save task error: {}", description, e);
            }
        };

        try {
            executor.execute(guarded);
            return true;
        } catch (RejectedExecutionException e) {
            int skipped = skippedSaveTasks.incrementAndGet();
            if (skipped <= 5 || VoxelConstants.DEBUG) {
                VoxelConstants.getLogger().debug("Rejected save task during shutdown: {} (skipped count: {})", description, skipped);
            }
        } catch (RuntimeException e) {
            VoxelConstants.getLogger().warn("Failed to submit save task: {}", description, e);
        }
        return false;
    }

    public static boolean isSaveShutdownInProgress() {
        return saveShutdownInProgress;
    }

    private static ThreadPoolExecutor createSaveExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(concurrentThreads, concurrentThreads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.setThreadFactory(new NamedThreadFactory("Voxelmap WorldMap Saver Thread", false));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static void shutdownCalculationQueue() {
        emptyQueue();
        executorService.shutdown();
        overlayExecutorService.shutdown();
        viewExecutorService.shutdown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        for (ThreadPoolExecutor executor : new ThreadPoolExecutor[]{executorService, overlayExecutorService, viewExecutorService}) {
            try {
                if (!executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) executor.shutdownNow();
            } catch (InterruptedException exception) {
                executorService.shutdownNow(); overlayExecutorService.shutdownNow(); viewExecutorService.shutdownNow();
                Thread.currentThread().interrupt(); return;
            }
        }
    }

    static {
        executorService.setThreadFactory(new NamedThreadFactory("Voxelmap WorldMap Calculation Thread", true));
        executorService.allowCoreThreadTimeOut(true);
        overlayExecutorService.setThreadFactory(new NamedThreadFactory("Voxelmap Overlay Loader", true));
        viewExecutorService.setThreadFactory(new NamedThreadFactory("Voxelmap View Mesher", true));
        overlayExecutorService.allowCoreThreadTimeOut(true);
        viewExecutorService.allowCoreThreadTimeOut(true);
        saveExecutorService.setThreadFactory(new NamedThreadFactory("Voxelmap WorldMap Saver Thread", false));
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String name;
        private final AtomicInteger threadCount = new AtomicInteger(1);
        private final boolean daemon;

        private NamedThreadFactory(String name, boolean daemon) {
            this.name = name;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r, this.name + " " + this.threadCount.getAndIncrement());
            thread.setDaemon(this.daemon);
            return thread;
        }
    }
}
