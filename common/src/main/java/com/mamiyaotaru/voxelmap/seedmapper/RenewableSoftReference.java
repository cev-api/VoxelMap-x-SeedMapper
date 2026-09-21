package com.mamiyaotaru.voxelmap.seedmapper;

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

/** A lazily recreated cache holder for large, discardable SeedMapper data. */
final class RenewableSoftReference<T> {
    private final Supplier<T> supplier;
    private volatile SoftReference<T> reference;

    RenewableSoftReference(Supplier<T> supplier) {
        this.supplier = supplier;
        this.reference = new SoftReference<>(supplier.get());
    }

    T get() {
        T value = reference.get();
        if (value != null) {
            return value;
        }
        synchronized (this) {
            value = reference.get();
            if (value == null) {
                value = supplier.get();
                reference = new SoftReference<>(value);
            }
            return value;
        }
    }
}
