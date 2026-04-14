package com.mamiyaotaru.voxelmap.seedmapper;

import net.minecraft.util.Mth;

import java.io.PrintWriter;
import java.util.Locale;

public class SeedMapperEspStyle {
    public String outlineColor = "#00CFFF";
    public double outlineAlpha = 0.95D;
    public boolean useCommandColor = true;
    public boolean fillEnabled = false;
    public String fillColor = "#00CFFF";
    public double fillAlpha = 0.2D;
    public boolean rainbow = false;
    public double rainbowSpeed = 1.0D;

    public SeedMapperEspStyle copy() {
        SeedMapperEspStyle copy = new SeedMapperEspStyle();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(SeedMapperEspStyle other) {
        if (other == null) {
            return;
        }
        this.outlineColor = other.outlineColor;
        this.outlineAlpha = other.outlineAlpha;
        this.useCommandColor = other.useCommandColor;
        this.fillEnabled = other.fillEnabled;
        this.fillColor = other.fillColor;
        this.fillAlpha = other.fillAlpha;
        this.rainbow = other.rainbow;
        this.rainbowSpeed = other.rainbowSpeed;
    }

    public SeedMapperEspStyleSnapshot snapshot(int fallbackColor) {
        int baseColor = parseColor(this.outlineColor, fallbackColor);
        int fillRgb = parseColor(this.fillColor, baseColor);
        return new SeedMapperEspStyleSnapshot(
                baseColor | 0xFF000000,
                (float) Mth.clamp(this.outlineAlpha, 0.0D, 1.0D),
                this.fillEnabled,
                fillRgb | 0xFF000000,
                (float) Mth.clamp(this.fillAlpha, 0.0D, 1.0D),
                this.rainbow,
                (float) Mth.clamp(this.rainbowSpeed, 0.05D, 5.0D)
        );
    }

    public void loadLine(String key, String value) {
        String suffix = key.toLowerCase(Locale.ROOT);
        switch (suffix) {
            case "outline color" -> this.outlineColor = normalizeColor(value, this.outlineColor);
            case "outline alpha" -> this.outlineAlpha = clampDouble(value, this.outlineAlpha, 0.0D, 1.0D);
            case "use command color" -> this.useCommandColor = Boolean.parseBoolean(value);
            case "fill enabled" -> this.fillEnabled = Boolean.parseBoolean(value);
            case "fill color" -> this.fillColor = normalizeColor(value, this.fillColor);
            case "fill alpha" -> this.fillAlpha = clampDouble(value, this.fillAlpha, 0.0D, 1.0D);
            case "rainbow" -> this.rainbow = Boolean.parseBoolean(value);
            case "rainbow speed" -> this.rainbowSpeed = clampDouble(value, this.rainbowSpeed, 0.05D, 5.0D);
        }
    }

    public void save(PrintWriter out, String prefix) {
        out.println(prefix + " Outline Color:" + this.outlineColor);
        out.println(prefix + " Outline Alpha:" + this.outlineAlpha);
        out.println(prefix + " Use Command Color:" + this.useCommandColor);
        out.println(prefix + " Fill Enabled:" + this.fillEnabled);
        out.println(prefix + " Fill Color:" + this.fillColor);
        out.println(prefix + " Fill Alpha:" + this.fillAlpha);
        out.println(prefix + " Rainbow:" + this.rainbow);
        out.println(prefix + " Rainbow Speed:" + this.rainbowSpeed);
    }

    public static SeedMapperEspStyle defaults() {
        return new SeedMapperEspStyle();
    }

    public static SeedMapperEspStyle useCommandColorDefaults() {
        SeedMapperEspStyle style = new SeedMapperEspStyle();
        style.useCommandColor = true;
        return style;
    }

    public static int parseColor(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String normalized = text.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized, 16) & 0x00FFFFFF;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static String normalizeColor(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (!normalized.matches("#[0-9a-fA-F]{6}")) {
            return fallback;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static double clampDouble(String value, double fallback, double min, double max) {
        try {
            return Mth.clamp(Double.parseDouble(value), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
