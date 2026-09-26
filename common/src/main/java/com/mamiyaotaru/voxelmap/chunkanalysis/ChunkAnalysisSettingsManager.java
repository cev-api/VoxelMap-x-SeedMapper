package com.mamiyaotaru.voxelmap.chunkanalysis;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.interfaces.ISubSettingsManager;
import net.minecraft.util.Mth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

public final class ChunkAnalysisSettingsManager implements ISubSettingsManager {
    public boolean ghostBlocks = true;
    public boolean espFill = false;
    public boolean highConfidenceOnly = true;
    public boolean structureOnly = true;
    /** off, voids, audit, or both; continuous scans begin when the player enters a new chunk. */
    public String continuousMode = "off";
    public int continuousRadius = ChunkAnalysisService.MAX_CONTINUOUS_RADIUS;
    public boolean flashDetections = false;
    public boolean chatFeedback = true;
    public int scanRadius = ChunkAnalysisService.DEFAULT_RADIUS;
    public int renderDistance = 192;
    public int renderLimit = 12000;
    public int autoClearMinutes = 3;
    public double ghostOpacity = 0.38D;
    public double fillOpacity = 0.13D;
    public String missingExpectedColor = "#FF3333";
    public String excavationColor = "#FF174D";
    public String unexpectedColor = "#3388FF";
    public String changedColor = "#FFD43B";
    public String inventoriesColor = "#FF8800";
    public String redstoneColor = "#FF33CC";
    public String workstationsColor = "#33FF88";

    @Override
    public void loadAll(File settingsFile) {
        try (BufferedReader in = new BufferedReader(new FileReader(settingsFile))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] value = line.split(":", 2);
                if (value.length != 2) continue;
                switch (value[0]) {
                    case "ChunkAnalysis Ghost Blocks" -> ghostBlocks = Boolean.parseBoolean(value[1]);
                    case "ChunkAnalysis ESP Fill" -> espFill = Boolean.parseBoolean(value[1]);
                    case "ChunkAnalysis High Confidence Only" -> highConfidenceOnly = Boolean.parseBoolean(value[1]);
                    case "ChunkAnalysis Structure Only" -> structureOnly = Boolean.parseBoolean(value[1]);
                    case "ChunkAnalysis Continuous Mode" -> continuousMode = normalizeContinuousMode(value[1]);
                    case "ChunkAnalysis Continuous Radius" -> continuousRadius = Mth.clamp(Integer.parseInt(value[1]), 0, ChunkAnalysisService.MAX_CONTINUOUS_RADIUS);
                    case "ChunkAnalysis Flash Detections" -> flashDetections = Boolean.parseBoolean(value[1]);
                    case "ChunkAnalysis Chat Feedback" -> chatFeedback = Boolean.parseBoolean(value[1]);
                    case "ChunkAnalysis Scan Radius" -> scanRadius = Mth.clamp(Integer.parseInt(value[1]), 0, ChunkAnalysisService.MAX_RADIUS);
                    case "ChunkAnalysis Render Distance" -> renderDistance = Mth.clamp(Integer.parseInt(value[1]), 32, 512);
                    case "ChunkAnalysis Render Limit" -> renderLimit = Mth.clamp(Integer.parseInt(value[1]), 1000, 100000);
                    case "ChunkAnalysis Auto Clear Minutes" -> autoClearMinutes = Mth.clamp(Integer.parseInt(value[1]), 1, 5);
                    case "ChunkAnalysis Ghost Opacity" -> ghostOpacity = Mth.clamp(Double.parseDouble(value[1]), 0.05D, 0.8D);
                    case "ChunkAnalysis Fill Opacity" -> fillOpacity = Mth.clamp(Double.parseDouble(value[1]), 0.02D, 0.5D);
                    case "ChunkAnalysis Missing Expected Color" -> missingExpectedColor = normalizeColor(value[1], missingExpectedColor);
                    case "ChunkAnalysis Excavation Color" -> excavationColor = normalizeColor(value[1], excavationColor);
                    case "ChunkAnalysis Unexpected Color" -> unexpectedColor = normalizeColor(value[1], unexpectedColor);
                    case "ChunkAnalysis Changed Color" -> changedColor = normalizeColor(value[1], changedColor);
                    case "ChunkAnalysis Interesting Color" -> inventoriesColor = normalizeColor(value[1], inventoriesColor);
                    case "ChunkAnalysis Inventories Color" -> inventoriesColor = normalizeColor(value[1], inventoriesColor);
                    case "ChunkAnalysis Redstone Color" -> redstoneColor = normalizeColor(value[1], redstoneColor);
                    case "ChunkAnalysis Workstations Color" -> workstationsColor = normalizeColor(value[1], workstationsColor);
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    @Override
    public void saveAll(PrintWriter out) {
        out.println("ChunkAnalysis Ghost Blocks:" + ghostBlocks);
        out.println("ChunkAnalysis ESP Fill:" + espFill);
        out.println("ChunkAnalysis High Confidence Only:" + highConfidenceOnly);
        out.println("ChunkAnalysis Structure Only:" + structureOnly);
        out.println("ChunkAnalysis Continuous Mode:" + continuousMode);
        out.println("ChunkAnalysis Continuous Radius:" + continuousRadius);
        out.println("ChunkAnalysis Flash Detections:" + flashDetections);
        out.println("ChunkAnalysis Chat Feedback:" + chatFeedback);
        out.println("ChunkAnalysis Scan Radius:" + scanRadius);
        out.println("ChunkAnalysis Render Distance:" + renderDistance);
        out.println("ChunkAnalysis Render Limit:" + renderLimit);
        out.println("ChunkAnalysis Auto Clear Minutes:" + autoClearMinutes);
        out.println("ChunkAnalysis Ghost Opacity:" + ghostOpacity);
        out.println("ChunkAnalysis Fill Opacity:" + fillOpacity);
        out.println("ChunkAnalysis Missing Expected Color:" + missingExpectedColor);
        out.println("ChunkAnalysis Excavation Color:" + excavationColor);
        out.println("ChunkAnalysis Unexpected Color:" + unexpectedColor);
        out.println("ChunkAnalysis Changed Color:" + changedColor);
        out.println("ChunkAnalysis Inventories Color:" + inventoriesColor);
        out.println("ChunkAnalysis Redstone Color:" + redstoneColor);
        out.println("ChunkAnalysis Workstations Color:" + workstationsColor);
    }

    @Override public String getKeyText(EnumOptionsMinimap option) { return MapSettingsManager.ERROR_STRING; }
    @Override public boolean getBooleanValue(EnumOptionsMinimap option) { return false; }
    @Override public void toggleBooleanValue(EnumOptionsMinimap option) { }
    @Override public String getListValue(EnumOptionsMinimap option) { return MapSettingsManager.ERROR_STRING; }
    @Override public void cycleListValue(EnumOptionsMinimap option) { }
    @Override public float getFloatValue(EnumOptionsMinimap option) { return 0.0F; }
    @Override public void setFloatValue(EnumOptionsMinimap option, float value) { }

    private static String normalizeContinuousMode(String value) {
        if (value == null) return "off";
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "voids", "void" -> "voids";
            case "audit", "interesting" -> "audit";
            case "both" -> "both";
            default -> "off";
        };
    }

    private static String normalizeColor(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        return normalized.matches("[0-9a-fA-F]{6}") ? "#" + normalized.toUpperCase(java.util.Locale.ROOT) : fallback;
    }
}
