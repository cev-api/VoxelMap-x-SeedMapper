package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import java.util.concurrent.CopyOnWriteArraySet;

public class SettingsAndLightingChangeNotifier {
    private final java.util.concurrent.atomic.AtomicLong version = new java.util.concurrent.atomic.AtomicLong();
    public long version() { return version.get(); }

    private final CopyOnWriteArraySet<CachedRegion> listeners = new CopyOnWriteArraySet<>();

    public final void addObserver(CachedRegion listener) {
        listeners.add(listener);
    }

    public final void removeObserver(CachedRegion listener) {
        listeners.remove(listener);
    }

    public void notifyOfChanges() {
        version.incrementAndGet();
        for (CachedRegion listener : listeners) {
            listener.notifyOfActionableChange(this);
        }

    }
}
