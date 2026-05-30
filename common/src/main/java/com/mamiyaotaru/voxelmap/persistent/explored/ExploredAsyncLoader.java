package com.mamiyaotaru.voxelmap.persistent.explored;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;

public final class ExploredAsyncLoader {
    private record LoadRequest(int level, int containerX, int containerZ) {
    }

    private final ExploredDiskStore store;
    private final Executor executor;

    private final Object monitor = new Object();
    private final Queue<LoadRequest> queue = new ArrayDeque<>();
    private final Set<LoadRequest> queued = new HashSet<>();
    private boolean flushRequested = false;
    private boolean workerRunning = false;

    public ExploredAsyncLoader(ExploredDiskStore store, Executor executor) {
        this.store = store;
        this.executor = executor;
    }

    /** enqueues a container load unless it is already loaded or already queued */
    public void requestLoad(int level, int containerX, int containerZ) {
        if (store.isContainerLoaded(level, containerX, containerZ)) {
            return;
        }
        LoadRequest request = new LoadRequest(level, containerX, containerZ);
        synchronized (monitor) {
            if (!queued.add(request)) {
                return;
            }
            queue.add(request);
            ensureWorkerLocked();
        }
    }

    public void requestFlush() {
        synchronized (monitor) {
            flushRequested = true;
            ensureWorkerLocked();
        }
    }

    public int pendingCount() {
        synchronized (monitor) {
            return queued.size();
        }
    }

    private void ensureWorkerLocked() {
        if (!workerRunning && (!queue.isEmpty() || flushRequested)) {
            workerRunning = true;
            executor.execute(this::runWorker);
        }
    }

    private void runWorker() {
        try {
            while (true) {
                LoadRequest request;
                boolean doFlush;
                synchronized (monitor) {
                    doFlush = flushRequested;
                    flushRequested = false;
                    request = queue.poll();
                    if (request == null && !doFlush) {
                        workerRunning = false;
                        return;
                    }
                }
                if (request != null) {
                    try {
                        store.loadContainer(request.level(), request.containerX(), request.containerZ());
                    } finally {
                        synchronized (monitor) {
                            queued.remove(request);
                        }
                    }
                }
                if (doFlush) {
                    store.flush();
                }
            }
        } catch (RuntimeException | Error e) {
            // reset the running flag and re-submit a worker if work remains,
            // so future loads still get processed
            synchronized (monitor) {
                workerRunning = false;
                if (!queue.isEmpty() || flushRequested) {
                    workerRunning = true;
                    executor.execute(this::runWorker);
                }
            }
            throw e;
        }
    }
}
