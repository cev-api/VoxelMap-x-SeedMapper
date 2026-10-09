package com.mamiyaotaru.voxelmap.persistent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Render-thread polling with one outstanding lookup and a bounded result cache. */
public final class AsyncLatestValue<K, V> {
    private final ExecutorService worker;
    private final Map<K, V> cache = new LinkedHashMap<>(16, .75F, true);
    private final int limit;
    private Future<V> pending;
    private K pendingKey;

    public AsyncLatestValue(ExecutorService worker, int limit) {
        this.worker = worker;
        this.limit = Math.max(1, limit);
    }

    /** Returns null while loading; never waits for an unfinished task. Call from one thread. */
    public V get(K key, Supplier<V> lookup) {
        if (pending != null && pending.isDone()) {
            try {
                V result = pending.get();
                if (result != null) cache.put(pendingKey, result);
                while (cache.size() > limit) cache.remove(cache.keySet().iterator().next());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException ignored) {
                // A failed lookup leaves a cache miss and can be retried.
            }
            pending = null;
        }
        V result = cache.get(key);
        if (result == null && pending == null) {
            pendingKey = key;
            pending = worker.submit(lookup::get);
        }
        return result;
    }
}
