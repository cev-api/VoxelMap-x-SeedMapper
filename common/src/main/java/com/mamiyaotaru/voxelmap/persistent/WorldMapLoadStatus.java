package com.mamiyaotaru.voxelmap.persistent;

/** Migration/storage work joins the same counted loading bars as viewport builders. */
public final class WorldMapLoadStatus {
    private WorldMapLoadStatus() { }
    public static Task begin(String operation, int total) {
        Task task = new Task(operation, Math.max(0, total));
        task.update(0);
        return task;
    }
    public static final class Task implements AutoCloseable {
        private final String operation;
        private final int total;
        private final WorldMapProgress.Task progress = WorldMapProgress.begin("Storage");
        private int completed;
        private boolean closed;
        private Task(String operation, int total) { this.operation = operation; this.total = total; }
        public synchronized void update(int completed) {
            if (closed) return;
            this.completed = Math.max(0, completed);
            progress.update(operation, this.completed, total);
        }
        public synchronized void step() { update(completed + 1); }
        @Override public synchronized void close() {
            if (closed) return;
            closed = true; progress.finish();
        }
    }
}
