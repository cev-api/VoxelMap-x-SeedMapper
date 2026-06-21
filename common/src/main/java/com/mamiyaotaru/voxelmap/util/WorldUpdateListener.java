package com.mamiyaotaru.voxelmap.util;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.interfaces.IChangeObserver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorldUpdateListener {
    private final List<IChangeObserver> chunkProcessors = new ArrayList<>();
    private final Set<Long> notifiedChunksThisTick = new HashSet<>();
    private int notificationTick = -1;

    public void addListener(IChangeObserver chunkProcessor) {
        chunkProcessors.add(chunkProcessor);
    }

    public void notifyObservers(int chunkX, int sectionY, int chunkZ) {
        int tick = VoxelConstants.getElapsedTicks();
        long chunkKey = (Integer.toUnsignedLong(chunkX) << 32) | Integer.toUnsignedLong(chunkZ);
        synchronized (notifiedChunksThisTick) {
            if (notificationTick != tick) {
                notifiedChunksThisTick.clear();
                notificationTick = tick;
            }
            if (!notifiedChunksThisTick.add(chunkKey)) {
                return;
            }
        }

        try {
            for (IChangeObserver chunkProcessor : this.chunkProcessors) {
                chunkProcessor.handleChangeInWorld(chunkX, sectionY, chunkZ);
            }
        } catch (RuntimeException exception) {
            VoxelConstants.getLogger().error("Exception", exception);
        }
    }
}
