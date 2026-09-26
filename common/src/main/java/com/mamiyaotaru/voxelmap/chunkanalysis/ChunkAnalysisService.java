package com.mamiyaotaru.voxelmap.chunkanalysis;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.util.AppChatMessages;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperMarkerOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkAnalysisService {
    public static final int DEFAULT_RADIUS = 4; // 9x9 chunks
    public static final int MAX_RADIUS = 8;
    public static final int MAX_CONTINUOUS_RADIUS = MAX_RADIUS;
    private static final int MAX_RENDER_DIFFERENCES_STORED = 100_000;
    // Void detection keeps several hash maps over these candidates. Keep the quick scan bounded
    // so a badly mismatched cave baseline cannot exhaust the client heap.
    private static final int MAX_VOID_CANDIDATES = 50_000;
    // Spend a larger slice of each client tick on comparison so large scans finish much sooner.
    // World generation remains off-thread; this only affects the loaded-chunk comparison pass.
    private static final long COMPARISON_TICK_BUDGET_NANOS = 24_000_000L;
    private static final long CONTINUOUS_COMPARISON_TICK_BUDGET_NANOS = 40_000_000L;
    private static final ChunkAnalysisService INSTANCE = new ChunkAnalysisService();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "VoxelMap ChunkAnalysis");
            thread.setDaemon(true);
            return thread;
        }
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong scanEpoch = new AtomicLong();
    private volatile CompletableFuture<ChunkAnalysisWorldgenContext> context;
    private volatile ChunkAnalysisSnapshot snapshot = ChunkAnalysisSnapshot.empty();
    private volatile ComparisonJob comparisonJob;
    private volatile String status = "idle";
    private volatile long overlayExpiresAtMillis;
    private ClientLevel continuousLevel;
    private int continuousChunkX = Integer.MIN_VALUE;
    private int continuousChunkZ = Integer.MIN_VALUE;
    private String activeContinuousMode;
    private long lastContinuousPruneTick;
    private ChunkAnalysisWorldgenContext.GeneratedArea continuousGeneratedArea;
    private int continuousGeneratedCenterX = Integer.MIN_VALUE;
    private int continuousGeneratedCenterZ = Integer.MIN_VALUE;
    private int continuousGeneratedRadius = -1;
    private long continuousGeneratedSeed;
    private ResourceKey<Level> continuousGeneratedDimension;
    private ScanMode continuousGeneratedMode;

    private ChunkAnalysisService() { }

    public static ChunkAnalysisService get() { return INSTANCE; }
    public ChunkAnalysisSnapshot snapshot() { return snapshot; }
    public boolean isRunning() { return running.get(); }
    public String status() { return status; }

    public boolean scan(int radius) {
        return scan(radius, ScanMode.FULL, false);
    }

    public boolean scanVoids(int radius) {
        return scan(radius, ScanMode.VOIDS_ONLY, false);
    }

    public boolean scanAudit(int radius) {
        return scan(radius, ScanMode.INTERESTING_ONLY, false);
    }

    public boolean scanUnexpected(int radius) {
        return scan(radius, ScanMode.ALL_UNEXPECTED, false);
    }

    private boolean scan(int radius, ScanMode mode, boolean continuousRequest) {
        return scan(radius, mode, continuousRequest, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private boolean scan(int radius, ScanMode mode, boolean continuousRequest, int previousChunkX, int previousChunkZ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            message("ChunkAnalysis requires an active world.");
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            message("ChunkAnalysis is already scanning.");
            return false;
        }

        int checkedRadius = Math.max(0, Math.min(MAX_RADIUS, radius));
        long runId = scanEpoch.incrementAndGet();
        overlayExpiresAtMillis = 0L;
        long seed;
        try {
            seed = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions()
                    .resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException exception) {
            running.set(false);
            message("ChunkAnalysis needs the world seed. Use /seedmap seed <seed> first.");
            return false;
        }

        ChunkPos center = minecraft.player.chunkPosition();
        ResourceKey<Level> dimension = level.dimension();
        if (continuousRequest && canReuseContinuousArea(center, checkedRadius, seed, dimension, mode)) {
            status = "comparing loaded chunks (0%)";
            List<ChunkPos> chunks = continuousChunkSelection(center, checkedRadius, previousChunkX, previousChunkZ);
            comparisonJob = new ComparisonJob(level, seed, dimension, center, checkedRadius,
                    continuousGeneratedArea, System.nanoTime(), mode, chunks);
            return true;
        }
        status = "loading vanilla worldgen";
        message("ChunkAnalysis: preparing " + (mode == ScanMode.VOIDS_ONLY ? "fast terrain/carver baseline" : "vanilla worldgen")
                + " for " + (checkedRadius * 2 + 1) + "x" + (checkedRadius * 2 + 1) + " chunks...");

        CompletableFuture<ChunkAnalysisWorldgenContext> loaded = context;
        if (loaded == null) {
            synchronized (this) {
                loaded = context;
                if (loaded == null) context = loaded = ChunkAnalysisWorldgenContext.load(executor);
            }
        }

        long started = System.nanoTime();
        // Give continuous mode one chunk of cached overlap. This keeps a large scan
        // radius useful without rebuilding the entire radius on every boundary crossing.
        int generationRadius = continuousRequest ? checkedRadius + 1 : checkedRadius;
        loaded.thenApplyAsync(worldgen -> {
            status = "generating memory chunks";
            // Only the void fast path omits feature generation. Audit and literal scans need
            // the same structure/feature baseline as the normal comparison.
            return worldgen.generate(seed, dimension, center, generationRadius, mode != ScanMode.VOIDS_ONLY);
        }, executor).thenAccept(area -> minecraft.execute(() -> {
            if (runId != scanEpoch.get() || minecraft.level != level) return;
            if (continuousRequest) {
                continuousGeneratedArea = area;
                continuousGeneratedCenterX = center.x();
                continuousGeneratedCenterZ = center.z();
                continuousGeneratedRadius = generationRadius;
                continuousGeneratedSeed = seed;
                continuousGeneratedDimension = dimension;
                continuousGeneratedMode = mode;
            }
            status = "comparing loaded chunks (0%)";
            comparisonJob = new ComparisonJob(level, seed, dimension, center, checkedRadius, area, started, mode, null);
        })).exceptionally(failure -> {
            if (runId != scanEpoch.get()) return null;
            VoxelConstants.getLogger().error("ChunkAnalysis scan failed", failure);
            status = "failed: " + rootMessage(failure);
            running.set(false);
            minecraft.execute(() -> message("ChunkAnalysis failed: " + rootMessage(failure)));
            return null;
        });
        return true;
    }

    public void clear() {
        snapshot = ChunkAnalysisSnapshot.empty();
        overlayExpiresAtMillis = 0L;
        if (!running.get()) status = "idle";
    }

    public void cancel() {
        scanEpoch.incrementAndGet();
        comparisonJob = null;
        running.set(false);
        status = "idle";
    }

    private boolean canReuseContinuousArea(ChunkPos center, int radius, long seed,
                                           ResourceKey<Level> dimension, ScanMode mode) {
        if (continuousGeneratedArea == null || continuousGeneratedMode != mode
                || continuousGeneratedSeed != seed || continuousGeneratedDimension != dimension) {
            return false;
        }
        return Math.abs(center.x() - continuousGeneratedCenterX) + radius <= continuousGeneratedRadius
                && Math.abs(center.z() - continuousGeneratedCenterZ) + radius <= continuousGeneratedRadius;
    }

    /** Recheck only the newly entered edge chunks when the cached continuous area still covers us. */
    private static List<ChunkPos> continuousChunkSelection(ChunkPos center, int radius, int previousX, int previousZ) {
        int width = radius * 2 + 1;
        if (previousX == Integer.MIN_VALUE || previousZ == Integer.MIN_VALUE) {
            return fullChunkSelection(center, radius);
        }
        List<ChunkPos> chunks = new ArrayList<>();
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                if (x < previousX - radius || x > previousX + radius
                        || z < previousZ - radius || z > previousZ + radius) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
        return chunks.isEmpty() ? fullChunkSelection(center, radius) : chunks;
    }

    private static List<ChunkPos> fullChunkSelection(ChunkPos center, int radius) {
        int width = radius * 2 + 1;
        List<ChunkPos> chunks = new ArrayList<>(width * width);
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    private void clearContinuousGeneratedArea() {
        continuousGeneratedArea = null;
        continuousGeneratedCenterX = Integer.MIN_VALUE;
        continuousGeneratedCenterZ = Integer.MIN_VALUE;
        continuousGeneratedRadius = -1;
        continuousGeneratedDimension = null;
        continuousGeneratedMode = null;
    }

    /** Called on the client tick; compares chunks until the small frame-time budget is exhausted. */
    public void tick() {
        long expiresAt = overlayExpiresAtMillis;
        if (expiresAt != 0L && System.currentTimeMillis() >= expiresAt) {
            clear();
        }
        ComparisonJob job = comparisonJob;
        if (job == null) {
            pruneContinuousOverlay();
            tickContinuous();
            return;
        }
        if (Minecraft.getInstance().level != job.level) {
            comparisonJob = null;
            running.set(false);
            status = "cancelled: world changed";
            return;
        }
        ChunkAnalysisSettingsManager tickSettings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
        boolean continuousComparison = tickSettings != null && !"off".equals(tickSettings.continuousMode);
        long comparisonBudget = continuousComparison ? CONTINUOUS_COMPARISON_TICK_BUDGET_NANOS : COMPARISON_TICK_BUDGET_NANOS;
        long deadline = System.nanoTime() + comparisonBudget;
        boolean complete;
        do {
            complete = job.compareNextChunk();
        } while (!complete && System.nanoTime() < deadline);
        if (complete) {
            ChunkAnalysisSnapshot completed;
            try {
                completed = job.finish();
            } catch (RuntimeException failure) {
                comparisonJob = null;
                running.set(false);
                status = "failed: " + rootMessage(failure);
                VoxelConstants.getLogger().error("ChunkAnalysis comparison failed", failure);
                message("ChunkAnalysis failed: " + rootMessage(failure));
                return;
            }
            ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
            boolean continuous = settings != null && !"off".equals(settings.continuousMode);
            snapshot = continuous ? mergeContinuousSnapshot(snapshot, completed, job.level) : completed;
            if (continuous) {
                overlayExpiresAtMillis = 0L;
            } else {
                int autoClearMinutes = settings == null ? 3 : settings.autoClearMinutes;
                overlayExpiresAtMillis = System.currentTimeMillis() + autoClearMinutes * 60_000L;
            }
            comparisonJob = null;
            status = "complete";
            running.set(false);
            message(summary(snapshot));
            tickContinuous();
        } else {
            status = "comparing loaded chunks (" + job.percent() + "%)";
        }
    }

    public static String summary(ChunkAnalysisSnapshot value) {
        long total = value.missingCount() + value.unexpectedCount() + value.changedCount();
        ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
        int visibleLimit = settings == null ? value.differences().size() : settings.renderLimit;
        String renderNote = total > Math.min(value.differences().size(), visibleLimit)
                ? "; overlay sampled to protect frame time"
                : "";
        String result = "ChunkAnalysis complete in " + value.generationMillis() + "ms: "
                + value.count(ChunkAnalysisDifference.Kind.MISSING_EXPECTED) + " missing (red), "
                + value.count(ChunkAnalysisDifference.Kind.UNEXPECTED) + " unexpected (blue), "
                + value.count(ChunkAnalysisDifference.Kind.CHANGED) + " changed (yellow)"
                + (value.excavationCount() == 0 ? "" : "; " + value.excavationCount() + " player-shaped voids ("
                + value.excavationBlockCount() + " blocks, deep red)")
                + (value.skippedChunks() == 0 ? "" : "; " + value.skippedChunks() + " unloaded chunks skipped")
                + (value.unreliableChunks() == 0 ? "" : "; " + value.unreliableChunks() + " baseline-incompatible chunks filtered")
                + renderNote + ".";
        if (value.mode() == ScanMode.INTERESTING_ONLY) {
            return "Structure audit complete: " + value.unexpectedCount() + " unexpected blocks: "
                    + value.auditContainers() + " containers, " + value.auditRedstone() + " redstone, "
                    + value.auditWorkstations() + " workstations."
                    + (value.skippedChunks() == 0 ? "" : " " + value.skippedChunks() + " unloaded chunks skipped.");
        }
        if (value.mode() == ScanMode.BOTH) {
            return "Combined scan complete: " + value.unexpectedCount() + " interesting blocks and "
                    + value.excavationBlockCount() + " excavation blocks."
                    + (value.skippedChunks() == 0 ? "" : " " + value.skippedChunks() + " unloaded chunks skipped.");
        }
        if (value.mode() == ScanMode.ALL_UNEXPECTED) {
            return "All unexpected blocks complete: " + value.unexpectedCount()
                    + " block-type differences found."
                    + (value.skippedChunks() == 0 ? "" : " " + value.skippedChunks() + " unloaded chunks skipped.");
        }
        return result;
    }

    public void shutdown() {
        cancel();
        CompletableFuture<ChunkAnalysisWorldgenContext> loaded = context;
        if (loaded != null && loaded.isDone() && !loaded.isCompletedExceptionally()) {
            try { loaded.join().close(); } catch (RuntimeException ignored) { }
        }
        executor.shutdownNow();
    }

    private void tickContinuous() {
        ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
        if (settings == null || "off".equals(settings.continuousMode)) {
            if (activeContinuousMode != null) {
                overlayExpiresAtMillis = System.currentTimeMillis() + (settings == null ? 3 : settings.autoClearMinutes) * 60_000L;
            }
            activeContinuousMode = null;
            continuousLevel = null;
            continuousChunkX = Integer.MIN_VALUE;
            continuousChunkZ = Integer.MIN_VALUE;
            clearContinuousGeneratedArea();
            return;
        }
        if (!settings.continuousMode.equals(activeContinuousMode)) {
            snapshot = ChunkAnalysisSnapshot.empty();
            overlayExpiresAtMillis = 0L;
            activeContinuousMode = settings.continuousMode;
            clearContinuousGeneratedArea();
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || running.get()) return;
        int chunkX = minecraft.player.chunkPosition().x();
        int chunkZ = minecraft.player.chunkPosition().z();
        if (level != continuousLevel) {
            snapshot = ChunkAnalysisSnapshot.empty();
            overlayExpiresAtMillis = 0L;
            continuousLevel = level;
            continuousChunkX = chunkX;
            continuousChunkZ = chunkZ;
            clearContinuousGeneratedArea();
            return;
        }
        if (chunkX == continuousChunkX && chunkZ == continuousChunkZ) return;
        int previousChunkX = continuousChunkX;
        int previousChunkZ = continuousChunkZ;
        continuousChunkX = chunkX;
        continuousChunkZ = chunkZ;
        if ("both".equals(settings.continuousMode)) {
            scan(settings.continuousRadius, ScanMode.BOTH, true, previousChunkX, previousChunkZ);
        } else if ("voids".equals(settings.continuousMode)) {
            scan(settings.continuousRadius, ScanMode.VOIDS_ONLY, true, previousChunkX, previousChunkZ);
        } else if ("audit".equals(settings.continuousMode)) {
            scan(settings.continuousRadius, ScanMode.INTERESTING_ONLY, true, previousChunkX, previousChunkZ);
        }
    }

    private void pruneContinuousOverlay() {
        ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
        if (settings == null || "off".equals(settings.continuousMode) || snapshot.differences().isEmpty()) return;
        long tick = VoxelConstants.getElapsedTicks();
        if (tick - lastContinuousPruneTick < 10L) return;
        lastContinuousPruneTick = tick;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        List<ChunkAnalysisDifference> visible = snapshot.differences().stream()
                .filter(difference -> level.getChunkSource().getChunkNow(
                        difference.pos().getX() >> 4, difference.pos().getZ() >> 4) != null)
                .toList();
        if (visible.size() == snapshot.differences().size()) return;
        snapshot = new ChunkAnalysisSnapshot(snapshot.seed(), snapshot.dimension(), snapshot.center(), snapshot.radius(),
                visible, snapshot.missingCount(), snapshot.unexpectedCount(), snapshot.changedCount(),
                snapshot.excavationBlockCount(), snapshot.excavationCount(), snapshot.comparedBlocks(),
                snapshot.skippedChunks(), snapshot.unreliableChunks(), snapshot.generationMillis(), snapshot.mode(),
                snapshot.auditContainers(), snapshot.auditRedstone(), snapshot.auditWorkstations());
    }

    private static ChunkAnalysisSnapshot mergeContinuousSnapshot(ChunkAnalysisSnapshot previous,
                                                                  ChunkAnalysisSnapshot completed, ClientLevel level) {
        java.util.LinkedHashMap<ContinuousDifferenceKey, ChunkAnalysisDifference> merged = new java.util.LinkedHashMap<>();
        previous.differences().forEach(difference -> {
            if (level.getChunkSource().getChunkNow(difference.pos().getX() >> 4, difference.pos().getZ() >> 4) != null) {
                merged.put(new ContinuousDifferenceKey(difference.pos().asLong(), difference.kind()), difference);
            }
        });
        completed.differences().forEach(difference ->
                merged.put(new ContinuousDifferenceKey(difference.pos().asLong(), difference.kind()), difference));
        return new ChunkAnalysisSnapshot(completed.seed(), completed.dimension(), completed.center(), completed.radius(),
                List.copyOf(merged.values()), completed.missingCount(), completed.unexpectedCount(), completed.changedCount(),
                completed.excavationBlockCount(), completed.excavationCount(), completed.comparedBlocks(),
                completed.skippedChunks(), completed.unreliableChunks(), completed.generationMillis(), completed.mode(),
                completed.auditContainers(), completed.auditRedstone(), completed.auditWorkstations());
    }

    private record ContinuousDifferenceKey(long pos, ChunkAnalysisDifference.Kind kind) { }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static void message(String text) {
        ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
        if (settings != null && !settings.chatFeedback) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.hud.getChat()
                .addClientSystemMessage(AppChatMessages.prefixed("ChunkAnalysis", text));
    }

    private static final class ComparisonJob {
        private final ClientLevel level;
        private final long seed;
        private final ResourceKey<Level> dimension;
        private final ChunkPos center;
        private final int radius;
        private final ChunkAnalysisWorldgenContext.GeneratedArea area;
        private final long started;
        private final ScanMode mode;
        private final boolean highConfidenceOnly;
        private final boolean voidScan;
        private final List<ChunkPos> chunks;
        private final int totalChunks;
        private final List<ChunkAnalysisDifference> differences = new ArrayList<>();
        private final List<ChunkAnalysisVoidDetector.Candidate> voidCandidates = new ArrayList<>();
        private int nextChunk;
        private int compared;
        private int skipped;
        private int unreliable;
        private long missing;
        private long unexpected;
        private long changed;
        private long auditContainers;
        private long auditRedstone;
        private long auditWorkstations;
        private long excavationBlocks;
        private int excavations;

        private ComparisonJob(ClientLevel level, long seed, ResourceKey<Level> dimension, ChunkPos center,
                              int radius, ChunkAnalysisWorldgenContext.GeneratedArea area, long started, ScanMode mode) {
            this(level, seed, dimension, center, radius, area, started, mode, null);
        }

        private ComparisonJob(ClientLevel level, long seed, ResourceKey<Level> dimension, ChunkPos center,
                              int radius, ChunkAnalysisWorldgenContext.GeneratedArea area, long started, ScanMode mode,
                              List<ChunkPos> selectedChunks) {
            this.level = level;
            this.seed = seed;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.area = area;
            this.started = started;
            this.mode = mode;
            ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
            this.highConfidenceOnly = settings == null || settings.highConfidenceOnly;
            this.voidScan = mode == ScanMode.VOIDS_ONLY || mode == ScanMode.BOTH;
            this.chunks = selectedChunks == null ? fullChunkSelection(center, radius) : selectedChunks;
            this.totalChunks = chunks.size();
        }

        private boolean compareNextChunk() {
            if (nextChunk >= totalChunks) return true;
            ChunkPos selectedChunk = chunks.get(nextChunk);
            int chunkX = selectedChunk.x();
            int chunkZ = selectedChunk.z();
            nextChunk++;
            LevelChunk actualChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (actualChunk == null) {
                skipped++;
                return nextChunk >= totalChunks;
            }
            ProtoChunk expectedChunk = area.get(new ChunkPos(chunkX, chunkZ));
            boolean targetedMode = mode == ScanMode.INTERESTING_ONLY || mode == ScanMode.ALL_UNEXPECTED;
            if (!targetedMode && highConfidenceOnly && !biomeBaselineMatches(expectedChunk, actualChunk)) {
                if (mode == ScanMode.BOTH) {
                    // Keep the targeted audit useful even when the terrain baseline is
                    // incompatible; only the void half needs this confidence filter.
                    compareInterestingChunk(actualChunk, expectedChunk);
                } else {
                    unreliable++;
                }
                return nextChunk >= totalChunks;
            }
            if (mode == ScanMode.INTERESTING_ONLY) {
                compareInterestingChunk(actualChunk, expectedChunk);
                return nextChunk >= totalChunks;
            }
            if (mode == ScanMode.ALL_UNEXPECTED) {
                compareAllUnexpectedChunk(actualChunk, expectedChunk);
                return nextChunk >= totalChunks;
            }
            if (mode == ScanMode.BOTH) {
                compareInterestingChunk(actualChunk, expectedChunk);
            }
            int chunkStorageStart = differences.size();
            int chunkStorageLimit = Math.max(1, MAX_RENDER_DIFFERENCES_STORED / totalChunks);
            int chunkDifferenceCount = 0;
            long chunkAcceptedMissing = 0;
            int rawMissingTerrain = 0;
            int rawUnexpectedTerrain = 0;
            List<ChunkAnalysisVoidDetector.Candidate> chunkVoidCandidates = voidScan ? new ArrayList<>() : List.of();
            int chunkVoidCandidateLimit = voidScan
                    ? Math.max(1, (MAX_VOID_CANDIDATES + totalChunks - 1) / totalChunks)
                    : 0;
            int chunkVoidCandidateCount = 0;
            RandomSource sampleRandom = RandomSource.create(ChunkPos.pack(chunkX, chunkZ) ^ seed);
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            LevelChunkSection[] actualSections = actualChunk.getSections();
            LevelChunkSection[] expectedSections = expectedChunk.getSections();
            for (int y = area.minY(); y < area.minY() + area.height(); y++) {
                if (voidScan && Math.floorMod(y - area.minY(), 16) == 0) {
                    int sectionIndex = Math.floorDiv(y - area.minY(), 16);
                    LevelChunkSection actualSection = sectionIndex < actualSections.length ? actualSections[sectionIndex] : null;
                    LevelChunkSection expectedSection = sectionIndex < expectedSections.length ? expectedSections[sectionIndex] : null;
                    if (actualSection == null || expectedSection == null
                            || !actualSection.getStates().maybeHas(BlockState::isAir)
                            || !expectedSection.getStates().maybeHas(state -> !state.isAir())) {
                        y += 15;
                        continue;
                    }
                }
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        pos.set(minX + localX, y, minZ + localZ);
                        BlockState expected = expectedChunk.getBlockState(pos);
                        BlockState actual = actualChunk.getBlockState(pos);
                        compared++;
                        // Neighbor updates can legitimately change properties such as stair shape,
                        // connections, age, or waterlogging after generation. The seed establishes
                        // the block type; exact live BlockState identity is not evidence of editing.
                        if (expected.getBlock() == actual.getBlock()) continue;
                        // Structure processors frequently carve or replace blocks without passing
                        // through the feature-write hook used by the memory level. The seed still
                        // tells us the structure piece footprint, so conservatively exclude it.
                        if (highConfidenceOnly && area.isInsideStructurePiece(pos)) continue;
                        boolean featureWritten = area.wasFeatureWritten(pos);
                        if (!featureWritten && !expected.isAir() && actual.isAir() && isNaturalTerrain(expected)) {
                            rawMissingTerrain++;
                            if (voidScan) {
                                ChunkAnalysisVoidDetector.Candidate candidate =
                                        new ChunkAnalysisVoidDetector.Candidate(pos.immutable(), expected, false);
                                int candidateIndex = chunkVoidCandidateCount++;
                                if (chunkVoidCandidates.size() < chunkVoidCandidateLimit) {
                                    chunkVoidCandidates.add(candidate);
                                } else {
                                    int replacement = sampleRandom.nextInt(candidateIndex + 1);
                                    if (replacement < chunkVoidCandidateLimit) chunkVoidCandidates.set(replacement, candidate);
                                }
                            }
                        } else if (!featureWritten && expected.isAir() && !actual.isAir() && isNaturalTerrain(actual)) {
                            rawUnexpectedTerrain++;
                        }
                        if (mode == ScanMode.VOIDS_ONLY || mode == ScanMode.BOTH) continue;
                        if (!isSeedComparable(expected, actual, expectedChunk, pos, dimension,
                                featureWritten, highConfidenceOnly)) continue;
                        chunkDifferenceCount++;
                        ChunkAnalysisDifference.Kind kind;
                        if (!expected.isAir() && actual.isAir()) {
                            kind = ChunkAnalysisDifference.Kind.MISSING_EXPECTED;
                            missing++;
                            chunkAcceptedMissing++;
                        } else if (expected.isAir() && !actual.isAir()) {
                            kind = ChunkAnalysisDifference.Kind.UNEXPECTED;
                            unexpected++;
                        } else {
                            kind = ChunkAnalysisDifference.Kind.CHANGED;
                            changed++;
                        }
                        BlockState displayState = kind == ChunkAnalysisDifference.Kind.UNEXPECTED ? null : expected;
                        ChunkAnalysisDifference difference = new ChunkAnalysisDifference(pos.immutable(), kind, displayState);
                        if (chunkDifferenceCount <= chunkStorageLimit) {
                            differences.add(difference);
                        } else {
                            int replacement = sampleRandom.nextInt(chunkDifferenceCount);
                            if (replacement < chunkStorageLimit) differences.set(chunkStorageStart + replacement, difference);
                        }
                    }
                }
            }
            if (highConfidenceOnly && incompatibleCaveBaseline(rawMissingTerrain, rawUnexpectedTerrain)) {
                unreliable++;
                missing -= chunkAcceptedMissing;
                differences.subList(chunkStorageStart, differences.size())
                        .removeIf(difference -> difference.kind() == ChunkAnalysisDifference.Kind.MISSING_EXPECTED);
                if (voidScan) {
                    chunkVoidCandidates.forEach(candidate -> voidCandidates.add(
                            new ChunkAnalysisVoidDetector.Candidate(candidate.pos(), candidate.expectedState(), true)));
                }
            } else if (voidScan) {
                voidCandidates.addAll(chunkVoidCandidates);
            }
            return nextChunk >= totalChunks;
        }

        private void compareInterestingChunk(LevelChunk actualChunk, ProtoChunk expectedChunk) {
            ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
            boolean structureOnly = settings == null || settings.structureOnly;
            int chunkStorageStart = differences.size();
            int chunkStorageLimit = Math.max(1, MAX_RENDER_DIFFERENCES_STORED / totalChunks);
            int chunkDifferenceCount = 0;
            int minX = actualChunk.getPos().getMinBlockX();
            int minZ = actualChunk.getPos().getMinBlockZ();
            RandomSource sampleRandom = RandomSource.create(ChunkPos.pack(actualChunk.getPos().x(), actualChunk.getPos().z()) ^ seed);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            LevelChunkSection[] sections = actualChunk.getSections();
            int minSectionY = actualChunk.getMinY() >> 4;
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()
                        || !section.getStates().maybeHas(ComparisonJob::isInterestingBlock)) continue;
                int baseY = (minSectionY + sectionIndex) << 4;
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            BlockState actual = section.getBlockState(localX, localY, localZ);
                            if (!isInterestingBlock(actual)) continue;
                            pos.set(minX + localX, baseY + localY, minZ + localZ);
                            SeedMapperMarkerOption.Category category = SeedMapperMarkerOption.categoryFor(actual);
                            // Structure processors can write outside the final piece box. Feature-write
                            // metadata is the safe fallback for those generated structure blocks; the
                            // meaning of GeneratedArea.isInsideStructurePiece remains unchanged.
                            if (structureOnly && !area.isInsideStructurePiece(pos) && !area.wasFeatureWritten(pos)) continue;
                            compared++;
                            BlockState expected = expectedChunk.getBlockState(pos);
                            if (expected.getBlock() == actual.getBlock()) continue;
                            unexpected++;
                            switch (category) {
                                case CONTAINERS -> auditContainers++;
                                case REDSTONE -> auditRedstone++;
                                case WORKSTATIONS -> auditWorkstations++;
                                default -> { }
                            }
                            chunkDifferenceCount++;
                            ChunkAnalysisDifference.InterestingCategory interestingCategory = switch (category) {
                                case CONTAINERS -> ChunkAnalysisDifference.InterestingCategory.INVENTORIES;
                                case REDSTONE -> ChunkAnalysisDifference.InterestingCategory.REDSTONE;
                                case WORKSTATIONS -> ChunkAnalysisDifference.InterestingCategory.WORKSTATIONS;
                                case SPAWNERS -> null;
                            };
                            ChunkAnalysisDifference difference = new ChunkAnalysisDifference(pos.immutable(),
                                    ChunkAnalysisDifference.Kind.UNEXPECTED_INTERESTING, actual, interestingCategory);
                            if (chunkDifferenceCount <= chunkStorageLimit) differences.add(difference);
                            else {
                                int replacement = sampleRandom.nextInt(chunkDifferenceCount);
                                if (replacement < chunkStorageLimit)
                                    differences.set(chunkStorageStart + sampleRandom.nextInt(chunkStorageLimit), difference);
                            }
                        }
                    }
                }
            }
        }

        private static boolean isInterestingBlock(BlockState state) {
            SeedMapperMarkerOption.Category category = SeedMapperMarkerOption.categoryFor(state);
            return category == SeedMapperMarkerOption.Category.CONTAINERS
                    || category == SeedMapperMarkerOption.Category.REDSTONE
                    || category == SeedMapperMarkerOption.Category.WORKSTATIONS;
        }

        private void compareAllUnexpectedChunk(LevelChunk actualChunk, ProtoChunk expectedChunk) {
            int minX = actualChunk.getPos().getMinBlockX();
            int minZ = actualChunk.getPos().getMinBlockZ();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = area.minY(); y < area.minY() + area.height(); y++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        pos.set(minX + localX, y, minZ + localZ);
                        BlockState expected = expectedChunk.getBlockState(pos);
                        BlockState actual = actualChunk.getBlockState(pos);
                        compared++;
                        if (expected.getBlock() == actual.getBlock()) continue;
                        unexpected++;
                        ChunkAnalysisDifference difference = new ChunkAnalysisDifference(pos.immutable(),
                                ChunkAnalysisDifference.Kind.UNEXPECTED, actual);
                        differences.add(difference);
                    }
                }
            }
        }

        private static boolean incompatibleCaveBaseline(int missingTerrain, int unexpectedTerrain) {
            if (missingTerrain < 24 || unexpectedTerrain < 24) return false;
            int smaller = Math.min(missingTerrain, unexpectedTerrain);
            int larger = Math.max(missingTerrain, unexpectedTerrain);
            return smaller * 4 >= larger;
        }

        private boolean biomeBaselineMatches(ProtoChunk expected, LevelChunk actual) {
            int minQuartY = QuartPos.fromBlock(area.minY());
            int maxQuartY = QuartPos.fromBlock(area.minY() + area.height() - 1);
            for (int quartY = minQuartY; quartY <= maxQuartY; quartY++) {
                for (int quartZ = 0; quartZ < 4; quartZ++) {
                    for (int quartX = 0; quartX < 4; quartX++) {
                        if (!expected.getNoiseBiome(quartX, quartY, quartZ).unwrapKey()
                                .equals(actual.getNoiseBiome(quartX, quartY, quartZ).unwrapKey())) return false;
                    }
                }
            }
            return true;
        }

        /**
         * Only compare states whose present-day value remains a defensible seed-derived baseline.
         * Fluids and random-ticking blocks evolve through ordinary simulation without player input.
         */
        private static boolean isSeedComparable(BlockState expected, BlockState actual, ProtoChunk expectedChunk,
                                                BlockPos pos, ResourceKey<Level> dimension,
                                                boolean featureWritten, boolean highConfidenceOnly) {
            if (!expected.getFluidState().isEmpty() || !actual.getFluidState().isEmpty()) return false;
            if (!highConfidenceOnly) return true;

            // Missing volume is the strongest signal. A grass block may naturally become dirt,
            // but it cannot naturally become air; do not discard surface excavation merely
            // because the expected block has random ticks.
            if (actual.isAir()) return !featureWritten && !isNaturalDecoration(expected)
                    && !isUnstableNaturalFeature(expected, dimension);

            if (expected.isAir()) {
                if (actual.isRandomlyTicking() || isNaturalDecoration(actual)) return false;
                if (!isNaturalWorldgenBlock(actual, dimension)) return true;
                if (featureWritten) return false;
                // A seed-sensitive cave edge or feature can leave a natural block where this
                // baseline produced air. Natural blocks are accepted only at the expected
                // surface, which retains deliberate dirt/scaffolding shapes without cave noise.
                int expectedSurface = expectedChunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                        pos.getX() & 15, pos.getZ() & 15);
                return isLooseSurfaceMaterial(actual) && pos.getY() >= expectedSurface - 1;
            }

            if (expected.isRandomlyTicking() || actual.isRandomlyTicking()
                    || isNaturalDecoration(expected) || isNaturalDecoration(actual)
                    || isUnstableNaturalFeature(expected, dimension)
                    || isUnstableNaturalFeature(actual, dimension)) return false;

            // A block which cannot naturally generate in this dimension is evidence regardless
            // of whether the expected position happened to be touched by a vanilla feature.
            if (!isNaturalWorldgenBlock(actual, dimension)) return true;
            return actual.is(BlockTags.LOGS) && isNaturalTerrain(expected) && !featureWritten;
        }

        private static boolean isNaturalWorldgenBlock(BlockState state, ResourceKey<Level> dimension) {
            if (isNaturalTerrain(state) || isNaturalDecoration(state)
                    || isUnstableNaturalFeature(state, dimension)) return true;
            Block block = state.getBlock();
            if (dimension == Level.OVERWORLD) {
                return state.is(BlockTags.LOGS)
                        || block == Blocks.AMETHYST_BLOCK
                        || block == Blocks.BUDDING_AMETHYST
                        || block == Blocks.AMETHYST_CLUSTER
                        || block == Blocks.LARGE_AMETHYST_BUD
                        || block == Blocks.MEDIUM_AMETHYST_BUD
                        || block == Blocks.SMALL_AMETHYST_BUD
                        || block == Blocks.DRIPSTONE_BLOCK
                        || block == Blocks.POINTED_DRIPSTONE;
            }
            if (dimension == Level.NETHER) {
                return state.is(BlockTags.LOGS)
                        || block == Blocks.GLOWSTONE
                        || block == Blocks.SHROOMLIGHT;
            }
            return false;
        }

        /**
         * Feature blocks whose exact footprint cannot safely be attributed to the seed alone.
         * Sculk patches are feature-stage output and can subsequently spread from catalysts, so
         * neither their presence nor absence is reliable evidence of player modification.
         */
        private static boolean isUnstableNaturalFeature(BlockState state, ResourceKey<Level> dimension) {
            if (dimension != Level.OVERWORLD) return false;
            Block block = state.getBlock();
            return block == Blocks.SCULK || block == Blocks.SCULK_VEIN
                    || block == Blocks.SCULK_CATALYST || block == Blocks.SCULK_SENSOR
                    || block == Blocks.SCULK_SHRIEKER;
        }

        private static boolean isLooseSurfaceMaterial(BlockState state) {
            Block block = state.getBlock();
            return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                    || block == Blocks.GRAVEL || block == Blocks.CLAY;
        }

        private static boolean isNaturalDecoration(BlockState state) {
            if (state.isAir()) return false;
            return state.canBeReplaced()
                    || state.is(BlockTags.FLOWERS)
                    || state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.CROPS)
                    || state.is(BlockTags.CAVE_VINES)
                    || state.is(BlockTags.CORALS)
                    || state.is(BlockTags.SNOW)
                    || state.getBlock() == Blocks.AMETHYST_CLUSTER
                    || state.getBlock() == Blocks.LARGE_AMETHYST_BUD
                    || state.getBlock() == Blocks.MEDIUM_AMETHYST_BUD
                    || state.getBlock() == Blocks.SMALL_AMETHYST_BUD;
        }

        private static boolean isNaturalTerrain(BlockState state) {
            if (state.isAir()) return false;
            if (state.is(BlockTags.BASE_STONE_OVERWORLD)
                    || state.is(BlockTags.BASE_STONE_NETHER)
                    || state.is(BlockTags.DIRT)
                    || state.is(BlockTags.SAND)
                    || state.is(BlockTags.TERRACOTTA)
                    || state.is(BlockTags.BADLANDS_TERRACOTTA)
                    || state.is(BlockTags.NYLIUM)
                    || state.is(BlockTags.MOSS_BLOCKS)
                    || state.is(BlockTags.ICE)) return true;

            Block block = state.getBlock();
            if (block == Blocks.GRAVEL || block == Blocks.CLAY || block == Blocks.TUFF
                    || block == Blocks.CALCITE || block == Blocks.END_STONE
                    || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL
                    || block == Blocks.BASALT || block == Blocks.SMOOTH_BASALT
                    || block == Blocks.BLACKSTONE || block == Blocks.MAGMA_BLOCK
                    || block == Blocks.ANCIENT_DEBRIS || block == Blocks.RAW_COPPER_BLOCK
                    || block == Blocks.RAW_IRON_BLOCK || block == Blocks.AMETHYST_BLOCK
                    || block == Blocks.BUDDING_AMETHYST) return true;

            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            return path.endsWith("_ore");
        }

        private int percent() { return nextChunk * 100 / totalChunks; }

        private ChunkAnalysisSnapshot finish() {
            if (voidScan) applyVoidDetection();
            if (mode == ScanMode.VOIDS_ONLY) missing = excavationBlocks;
            return new ChunkAnalysisSnapshot(seed, dimension, center, radius, List.copyOf(differences),
                    missing, unexpected, changed, excavationBlocks, excavations, compared, skipped, unreliable,
                    (System.nanoTime() - started) / 1_000_000L, mode, auditContainers, auditRedstone, auditWorkstations);
        }

        private void applyVoidDetection() {
            ChunkAnalysisVoidDetector.Result result = ChunkAnalysisVoidDetector.detect(voidCandidates);
            if (result.blocks().isEmpty()) return;
            java.util.Set<Long> positions = new java.util.HashSet<>(result.blocks().size() * 2);
            result.blocks().forEach(candidate -> positions.add(candidate.pos().asLong()));
            for (int index = 0; index < differences.size(); index++) {
                ChunkAnalysisDifference difference = differences.get(index);
                if (difference.kind() == ChunkAnalysisDifference.Kind.MISSING_EXPECTED
                        && positions.remove(difference.pos().asLong())) {
                    differences.set(index, new ChunkAnalysisDifference(difference.pos(),
                            ChunkAnalysisDifference.Kind.EXCAVATION, difference.displayState(), difference.interestingCategory()));
                }
            }
            for (ChunkAnalysisVoidDetector.Candidate candidate : result.blocks()) {
                if (differences.size() >= MAX_RENDER_DIFFERENCES_STORED) break;
                if (positions.remove(candidate.pos().asLong())) {
                    differences.add(new ChunkAnalysisDifference(candidate.pos(),
                            ChunkAnalysisDifference.Kind.EXCAVATION, candidate.expectedState(), null));
                }
            }
            excavationBlocks = result.blocks().size();
            excavations = result.components();
        }
    }

    public enum ScanMode {
        FULL,
        VOIDS_ONLY,
        INTERESTING_ONLY,
        ALL_UNEXPECTED,
        BOTH
    }
}
