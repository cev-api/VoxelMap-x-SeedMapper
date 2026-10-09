package com.mamiyaotaru.voxelmap.persistent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/** One coalesced background request per visible layer, retaining the last complete result. */
final class WorldMapViewCache<K, V> {
    private final LinkedHashMap<String, Entry<K, V>> entries = new LinkedHashMap<>(8, 0.75F, true);
    private final ToLongFunction<V> bytes;
    private long budget;
    private final java.util.concurrent.ExecutorService executor;
    private long hits, builds;
    private long frame;
    private java.util.function.Function<String, String> progressLabel;
    private record HistoryKey<K>(String layer, K key) { }
    private final LinkedHashMap<HistoryKey<K>, V> history = new LinkedHashMap<>(32, .75F, true);
    WorldMapViewCache<K, V> trackProgress(java.util.function.Function<String, String> labels) { progressLabel = labels; return this; }
    private void cancel(Entry<K, V> entry) {
        if (entry.pending != null) entry.pending.cancel(true);
        if (entry.progress != null) entry.progress.cancel();
        entry.pending = null; entry.progress = null;
    }
    private void trimHistory() {
        long total = 0;
        for (V value : history.values()) total += bytes.applyAsLong(value);
        for (var item : entries.entrySet()) {
            Entry<K, V> entry = item.getValue();
            if (entry.value != null && !history.containsKey(new HistoryKey<>(item.getKey(), entry.key))) total += bytes.applyAsLong(entry.value);
        }
        var iterator = history.entrySet().iterator();
        while (iterator.hasNext() && (total > budget || history.size() > 128)) {
            var item = iterator.next();
            Entry<K, V> entry = entries.get(item.getKey().layer());
            // Removing a history reference must not evict the image currently displayed.
            if (entry == null || !java.util.Objects.equals(entry.key, item.getKey().key())) total -= bytes.applyAsLong(item.getValue());
            iterator.remove();
        }
    }

    private static final class Entry<K, V> {
        K key;
        K pendingKey;
        V value;
        Future<V> pending;
        WorldMapProgress.Task progress;
        long usedFrame;
    }

    WorldMapViewCache(long budget, ToLongFunction<V> bytes) { this(budget, bytes, ThreadManager.viewExecutorService); }
    WorldMapViewCache(long budget, ToLongFunction<V> bytes, java.util.concurrent.ExecutorService executor) {
        this.budget = budget; this.bytes = bytes; this.executor = executor;
    }
    private void purge() { if (executor instanceof java.util.concurrent.ThreadPoolExecutor pool) pool.purge(); }

    V get(String layer, K key, boolean ready, Supplier<V> builder) {
        return get(layer, key, ready, builder, java.util.Objects::equals);
    }

    V get(String layer, K key, boolean ready, Supplier<V> builder, java.util.function.BiPredicate<K, K> sameView) {
        Entry<K, V> entry = entries.computeIfAbsent(layer, ignored -> new Entry<>());
        entry.usedFrame = frame;
        if (entry.pending != null && entry.pending.isDone()) {
            try {
                if (!entry.pending.isCancelled()) {
                    V built = entry.pending.get();
                    if (built != null) history.put(new HistoryKey<>(layer, entry.pendingKey), built);
                    if (sameView.test(entry.pendingKey, key)) { entry.value = built; entry.key = entry.pendingKey; }
                }
            } catch (Exception exception) {
                com.mamiyaotaru.voxelmap.VoxelConstants.getLogger().warn("World map layer rebuild failed", exception);
            }
            entry.pending = null;
        }
        V warm = history.get(new HistoryKey<>(layer, key));
        if (warm != null && !key.equals(entry.key)) {
            cancel(entry); entry.value = warm; entry.key = key;
        }
        if (key.equals(entry.key)) hits++;
        else if (ready && (entry.pending == null || !sameView.test(key, entry.pendingKey))) {
            cancel(entry);
            entry.pendingKey = key;
            WorldMapProgress.Task task = progressLabel == null ? null : WorldMapProgress.begin(progressLabel.apply(layer));
            entry.progress = task;
            entry.pending = executor.submit(() -> WorldMapProgress.run(task, builder));
            builds++;
        }
        long total = 0;
        for (Entry<K, V> e : entries.values()) if (e.value != null) total += bytes.applyAsLong(e.value);
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && (entries.size() > 1024 || total > budget)) {
            var item = iterator.next();
            if (item.getKey().equals(layer)) continue;
            Entry<K, V> old = item.getValue();
            // A byte budget evicts obsolete layers, never a currently displayed full-detail layer.
            if (frame != 0 && old.usedFrame >= frame - 1) continue;
            cancel(old);
            if (old.value != null) total -= bytes.applyAsLong(old.value);
            iterator.remove();
        }
        trimHistory();
        purge();
        return entry.value;
    }

    void cancelPending(String layer) { Entry<K, V> entry = entries.get(layer); if (entry != null) cancel(entry); }
    K displayedKey(String layer) { Entry<K, V> entry = entries.get(layer); return entry == null ? null : entry.key; }
    boolean contains(String layer, K key) {
        Entry<K, V> entry = entries.get(layer);
        return (entry != null && key.equals(entry.key)) || history.containsKey(new HistoryKey<>(layer, key));
    }

    boolean matches(String layer, K key) { Entry<K, V> entry = entries.get(layer); return entry != null && key.equals(entry.key); }

    void setBudget(long bytes) { budget = Math.max(1, bytes); }
    void beginFrame() { frame++; }

    void clear() {
        for (Entry<K, V> e : entries.values()) cancel(e);
        entries.clear(); history.clear();
        purge();
    }

    long hits() { return hits; }
    long builds() { return builds; }
}
