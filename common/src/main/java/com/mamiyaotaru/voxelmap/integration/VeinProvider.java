package com.mamiyaotaru.voxelmap.integration;

import java.util.Collection;
import net.minecraft.core.BlockPos;

public interface VeinProvider {
    Collection<BlockPos> veinsInChunk(int chunkX, int chunkZ);
}
