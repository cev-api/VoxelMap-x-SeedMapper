package com.mamiyaotaru.voxelmap.persistent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Actual work counters for the current loading stage, independent of elapsed time. */
public final class WorldMapProgress {
    private static final AtomicLong IDS = new AtomicLong();
    private static final ConcurrentHashMap<Long, Task> TASKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Task> CURRENT = new ThreadLocal<>();
    private static final long FINISHED_DISPLAY_NANOS = 750_000_000L;
    public record Row(String layer, String stage, int percent) { }
    private record State(String stage, long done, long total, long finished) { }
    private WorldMapProgress() { }

    public static Task begin(String layer) {
        Task task = new Task(IDS.incrementAndGet(), layer);
        TASKS.put(task.id, task);
        return task;
    }
    public static Task currentTask() { return CURRENT.get(); }
    public static void report(String stage, long done, long total) {
        Task task = CURRENT.get();
        if (task != null) task.update(stage, done, total);
    }
    public static <V> V run(Task task, Supplier<V> work) {
        if (task == null) return work.get();
        Task previous = CURRENT.get(); CURRENT.set(task);
        task.update("Preparing", 0, 0);
        try { V value = work.get(); task.finish(); return value; }
        catch (RuntimeException | Error failure) { task.cancel(); throw failure; }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static List<Row> rows() {
        long now = System.nanoTime();
        var grouped = new java.util.TreeMap<String, List<State>>();
        TASKS.forEach((id, task) -> {
            State state = task.state;
            if (state.finished != 0 && now - state.finished > FINISHED_DISPLAY_NANOS) TASKS.remove(id, task);
            else grouped.computeIfAbsent(task.layer, ignored -> new ArrayList<>()).add(state);
        });
        List<Row> result = new ArrayList<>();
        grouped.forEach((layer, states) -> {
            boolean active = states.stream().anyMatch(state -> state.finished == 0);
            if (active) states.removeIf(state -> state.finished != 0);
            String stage = states.getFirst().stage;
            boolean sameStage = states.stream().allMatch(state -> state.stage.equals(stage));
            boolean measurable = sameStage && states.stream().allMatch(state -> state.total > 0);
            long done = 0, total = 0;
            for (State state : states) { done += state.done; total += state.total; }
            int percent = measurable ? (int) Math.min(100, 100.0 * done / total) : -1;
            result.add(new Row(layer, sameStage ? stage : "Preparing", percent));
        });
        return result;
    }
    public static final class Task {
        private final long id;
        private final String layer;
        private volatile State state = new State("Queued", 0, 1, 0);
        private boolean closed;
        private Task(long id, String layer) { this.id = id; this.layer = layer; }
        public synchronized void update(String stage, long done, long total) {
            if (!closed) state = new State(stage, Math.max(0, Math.min(done, Math.max(0, total))), Math.max(0, total), 0);
        }
        public synchronized void finish() {
            if (!closed) { closed = true; state = new State("Ready", 1, 1, System.nanoTime()); }
        }
        public synchronized void cancel() { closed = true; TASKS.remove(id, this); }
    }
}
