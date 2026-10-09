package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

/** Opt-in live CPU diagnostics; enable with -Dvoxelmap.worldmap.profile=true. */
public final class WorldMapProfiler {
    private static final boolean ENABLED = Boolean.getBoolean("voxelmap.worldmap.profile");
    private static final double[] frames = new double[4096];
    private static int samples;
    private static long started, phaseStarted, reportAt;
    private static long terrain, overlays, waypoints, tiles, quads, uploads;
    private WorldMapProfiler() { }
    static void begin() { if (ENABLED) { started = System.nanoTime(); } }
    static void phaseBegin() { if (ENABLED) phaseStarted = System.nanoTime(); }
    static void terrainEnd() { if (ENABLED) terrain += System.nanoTime() - phaseStarted; }
    static void overlaysEnd() { if (ENABLED) overlays += System.nanoTime() - phaseStarted; }
    static void waypointsEnd() { if (ENABLED) waypoints += System.nanoTime() - phaseStarted; }
    public static void tile() { if (ENABLED) tiles++; }
    public static void quads(int count) { if (ENABLED) quads += count; }
    static void uploaded(int bytes) { if (ENABLED) uploads += bytes; }
    static void end(long trailHits, long trailBuilds, long areaHits, long areaBuilds) {
        if (!ENABLED) return;
        frames[samples++ % frames.length] = (System.nanoTime() - started) / 1e6;
        long now = System.currentTimeMillis();
        if (reportAt == 0) reportAt = now;
        if (now - reportAt < 5000) return;
        double[] sorted = Arrays.copyOf(frames, Math.min(samples, frames.length)); Arrays.sort(sorted);
        String line = String.format(Locale.ROOT,
                "%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                Instant.now(), samples, percentile(sorted, .5), percentile(sorted, .95), percentile(sorted, .99),
                terrain / 1e6 / samples, overlays / 1e6 / samples, waypoints / 1e6 / samples,
                tiles, quads, uploads, ThreadManager.executorService.getQueue().size(),
                ThreadManager.overlayExecutorService.getQueue().size(), ThreadManager.viewExecutorService.getQueue().size(),
                trailHits, trailBuilds, areaHits, areaBuilds);
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/voxelmap-worldmap-profile.csv");
        ThreadManager.viewExecutorService.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) Files.writeString(file,
                        "utc,frames,cpu_median_ms,cpu_p95_ms,cpu_p99_ms,terrain_avg_ms,overlays_avg_ms,waypoints_avg_ms,terrain_tiles,overlay_quads,uploaded_bytes,terrain_queue,loader_queue,mesh_queue,trail_hits,trail_builds,area_hits,area_builds\n",
                        StandardOpenOption.CREATE_NEW);
                Files.writeString(file, line, StandardOpenOption.APPEND);
            } catch (Exception exception) { VoxelConstants.getLogger().warn("Could not write world map CPU profile", exception); }
        });
        VoxelConstants.getLogger().info("World map CPU profile: median={}ms p95={}ms p99={}ms; {}",
                percentile(sorted, .5), percentile(sorted, .95), percentile(sorted, .99), file);
        samples = 0; terrain = overlays = waypoints = tiles = quads = uploads = 0; reportAt = now;
    }
    private static double percentile(double[] sorted, double percentile) { return sorted[(int) Math.min(sorted.length - 1, Math.floor(percentile * sorted.length))]; }
}
