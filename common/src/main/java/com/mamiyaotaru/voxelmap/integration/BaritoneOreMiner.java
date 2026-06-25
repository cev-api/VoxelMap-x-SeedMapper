package com.mamiyaotaru.voxelmap.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BaritoneOreMiner {

    private static final BaritoneOreMiner INSTANCE = new BaritoneOreMiner();

    private static final int WINDOW = 48;
    private static final int REACH_CHUNKS = 4;
    private static final int MAX_CHUNKS_PER_EXPANSION = 3;
    private static final long EXPAND_INTERVAL_MS = 600L;
    private static final long STUCK_TIMEOUT_MS = 20000L;
    private static final long IDLE_REISSUE_MS = 2000L;

    private final LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
    private final Set<Long> scannedChunks = new HashSet<>();
    private VeinProvider provider;
    private boolean active;
    private long lastProgressMs;
    private long lastReissueMs;
    private long lastExpandMs;

    private BaritoneOreMiner() {
    }

    public static BaritoneOreMiner getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized int remaining() {
        return targets.size();
    }

    public synchronized int start(int centerWorldX, int centerWorldZ, int initialChunkRange, VeinProvider provider) {
        targets.clear();
        scannedChunks.clear();
        this.provider = provider;
        int centerChunkX = centerWorldX >> 4;
        int centerChunkZ = centerWorldZ >> 4;
        int range = Math.max(0, initialChunkRange);
        for (int cx = centerChunkX - range; cx <= centerChunkX + range; cx++) {
            for (int cz = centerChunkZ - range; cz <= centerChunkZ + range; cz++) {
                scanAndAdd(cx, cz);
            }
        }
        active = provider != null;
        long now = System.currentTimeMillis();
        lastProgressMs = now;
        lastReissueMs = now;
        lastExpandMs = now;
        if (active && !targets.isEmpty()) {
            reissue();
        }
        return targets.size();
    }

    public synchronized void stop() {
        boolean wasActive = active;
        active = false;
        targets.clear();
        scannedChunks.clear();
        provider = null;
        if (wasActive) {
            BaritoneHelper.cancel();
        }
    }

    public synchronized void tick() {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Level level = mc.level;
        long now = System.currentTimeMillis();

        boolean changed = false;
        Iterator<BlockPos> it = targets.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (level.isLoaded(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    it.remove();
                    changed = true;
                }
            }
        }

        BlockPos playerPos = mc.player.blockPosition();
        BlockPos expandCenter = targets.isEmpty() ? playerPos : nearestTo(playerPos);

        if (now - lastExpandMs > EXPAND_INTERVAL_MS) {
            lastExpandMs = now;
            if (expandAround(expandCenter) > 0) {
                changed = true;
            }
        }

        if (changed) {
            lastProgressMs = now;
        }

        if (targets.isEmpty()) {
            if (reachFullyScanned(playerPos)) {
                finish(mc, "Baritone: ore veins within reach mined out — stopping.");
            }
            return;
        }

        if (changed) {
            reissue();
            return;
        }

        if (now - lastProgressMs > STUCK_TIMEOUT_MS) {
            BlockPos nearest = nearestTo(playerPos);
            if (nearest != null) {
                targets.remove(nearest);
            }
            lastProgressMs = now;
            if (!targets.isEmpty()) {
                reissue();
            }
            return;
        }

        if (!BaritoneHelper.isPathing() && now - lastReissueMs > IDLE_REISSUE_MS) {
            reissue();
        }
    }

    private int expandAround(BlockPos center) {
        if (provider == null) {
            return 0;
        }
        int pcx = center.getX() >> 4;
        int pcz = center.getZ() >> 4;
        List<long[]> candidates = new ArrayList<>();
        for (int cx = pcx - REACH_CHUNKS; cx <= pcx + REACH_CHUNKS; cx++) {
            for (int cz = pcz - REACH_CHUNKS; cz <= pcz + REACH_CHUNKS; cz++) {
                if (!scannedChunks.contains(chunkKey(cx, cz))) {
                    int dx = cx - pcx;
                    int dz = cz - pcz;
                    candidates.add(new long[] {cx, cz, (long) dx * dx + (long) dz * dz});
                }
            }
        }
        candidates.sort((a, b) -> Long.compare(a[2], b[2]));
        int added = 0;
        int scanned = 0;
        for (long[] c : candidates) {
            if (scanned >= MAX_CHUNKS_PER_EXPANSION) {
                break;
            }
            added += scanAndAdd((int) c[0], (int) c[1]);
            scanned++;
        }
        return added;
    }

    private int scanAndAdd(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (!scannedChunks.add(key)) {
            return 0;
        }
        if (provider == null) {
            return 0;
        }
        Collection<BlockPos> veins = provider.veinsInChunk(chunkX, chunkZ);
        if (veins == null) {
            return 0;
        }
        int added = 0;
        for (BlockPos pos : veins) {
            if (targets.add(pos.immutable())) {
                added++;
            }
        }
        return added;
    }

    private boolean reachFullyScanned(BlockPos playerPos) {
        int pcx = playerPos.getX() >> 4;
        int pcz = playerPos.getZ() >> 4;
        for (int cx = pcx - REACH_CHUNKS; cx <= pcx + REACH_CHUNKS; cx++) {
            for (int cz = pcz - REACH_CHUNKS; cz <= pcz + REACH_CHUNKS; cz++) {
                if (!scannedChunks.contains(chunkKey(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void finish(Minecraft mc, String message) {
        active = false;
        targets.clear();
        scannedChunks.clear();
        provider = null;
        BaritoneHelper.cancel();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }

    private void reissue() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        List<BlockPos> window = nearestN(mc.player.blockPosition(), WINDOW);
        if (window.isEmpty()) {
            return;
        }
        BaritoneHelper.setGoalComposite(window);
        lastReissueMs = System.currentTimeMillis();
    }

    private BlockPos nearestTo(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : targets) {
            double d = pos.distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    private List<BlockPos> nearestN(BlockPos from, int n) {
        List<BlockPos> all = new ArrayList<>(targets);
        all.sort((a, b) -> Double.compare(a.distSqr(from), b.distSqr(from)));
        if (all.size() > n) {
            return all.subList(0, n);
        }
        return all;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
