package com.mamiyaotaru.voxelmap.persistent;

import java.util.concurrent.atomic.AtomicInteger;

/** Shared, lightweight status for background world-map storage work. */
public final class WorldMapLoadStatus {
    private static final AtomicInteger ACTIVE_TASKS = new AtomicInteger();
    private static volatile String message;

    private WorldMapLoadStatus() {
    }

    public static Task begin(String operation, int total) {
        ACTIVE_TASKS.incrementAndGet();
        Task task = new Task(operation, Math.max(0, total));
        task.update(0);
        return task;
    }

    public static String current() {
        return ACTIVE_TASKS.get() > 0 ? message : null;
    }

    public static final class Task implements AutoCloseable {
        private final String operation;
        private final int total;
        private int completed;
        private boolean closed;

        private Task(String operation, int total) {
            this.operation = operation;
            this.total = total;
        }

        public synchronized void update(int completed) {
            if (closed) return;
            this.completed = Math.max(0, completed);
            message = total > 0
                    ? "Loading world map data: " + operation + " (" + this.completed + "/" + total + ")"
                    : "Loading world map data: " + operation + "...";
        }

        public synchronized void step() {
            update(completed + 1);
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            if (ACTIVE_TASKS.decrementAndGet() <= 0) {
                ACTIVE_TASKS.set(0);
                message = null;
            }
        }
    }
}
