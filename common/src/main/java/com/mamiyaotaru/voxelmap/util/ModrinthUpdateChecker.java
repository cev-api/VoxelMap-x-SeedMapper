/*
 * MIT License
 *
 * Copyright (c) 2025 Clickism
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.mamiyaotaru.voxelmap.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class to check for newer versions of a project hosted on Modrinth.
 * <p>
 * Compared to the old implementation, this one also aggregates changelogs for all
 * versions newer than the currently installed version, because the Modrinth version
 * list response includes a "changelog" field per version object. [page:2]
 */
public class ModrinthUpdateChecker {

    /**
     * Modrinth endpoint to list versions of a project. [page:2]
     */
    private static final String API_URL = "https://api.modrinth.com/v2/project/{id}/version";

    private final String projectId;
    private final String loader;

    @Nullable
    private final String minecraftVersion;

    private static final int green = 0xA8E6CF;
    private static final int red = 0xFF9AA2;
    private static final Properties BUILD_PROPERTIES = loadBuildProperties();

    /**
     * Data we need for update UI:
     * - version: normalized version string used for comparisons and URL building
     * - changelog: raw changelog text from Modrinth (may contain multiple lines) [page:2]
     */
    public record VersionInfo(String version, @Nullable String changelog) {
    }

    /**
     * Result of the update check:
     * - latestVersion: latest compatible version (normalized)
     * - updates: all versions that are newer than the installed version
     */
    public record UpdateResult(String latestVersion, List<VersionInfo> updates) {}

    public ModrinthUpdateChecker(String projectId, String loader) {
        this(projectId, loader, null);
    }

    /**
     * Create a new update checker for the given project.
     * Checks the latest compatible version for the given loader and a specific Minecraft version.
     *
     * @param projectId        the Modrinth project ID or slug
     * @param loader           the loader name used by Modrinth
     * @param minecraftVersion the current Minecraft version (or null for any)
     */
    public ModrinthUpdateChecker(String projectId, String loader, @Nullable String minecraftVersion) {
        this.projectId = projectId;
        this.loader = loader;
        this.minecraftVersion = minecraftVersion;
    }

    /**
     * Fetches versions from Modrinth and returns an aggregated update result.
     * The Modrinth response is an array of version objects containing fields like
     * version_number, loaders, game_versions, and changelog. [page:2]
     */
    public void checkUpdates(String installedModVersion, Consumer<UpdateResult> consumer) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL.replace("{id}", projectId))).GET().build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAcceptAsync(response -> {
                if (response.statusCode() != 200) {
                    consumer.accept(null);
                    return;
                }

                JsonArray versionsArray = JsonParser.parseString(response.body()).getAsJsonArray();
                UpdateResult result = buildUpdateResult(installedModVersion, versionsArray);
                consumer.accept(result);
            });
        } catch (Exception exception) {
            VoxelConstants.getLogger().log(Level.ERROR, exception);
            consumer.accept(null);
        }
    }

    /**
     * Builds a list of all compatible versions from Modrinth, then:
     * - finds the latest version
     * - filters the versions that are newer than the installed version
     * <p>
     * Uses version_number and changelog from the Modrinth version objects. [page:2]
     */
    @Nullable
    protected UpdateResult buildUpdateResult(String installedModVersion, JsonArray versions) {
        String installedRaw = getRawVersion(installedModVersion);

        List<VersionInfo> compatible = versions.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(version -> isVersionCompatible(version, true, true))
                .map(obj -> new VersionInfo(
                        getRawVersion(obj.get("version_number").getAsString()),
                        (obj.has("changelog") && !obj.get("changelog").isJsonNull()) ? obj.get("changelog").getAsString() : null
                )).collect(Collectors.toList());

        if (compatible.isEmpty()) {
            compatible = versions.asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .filter(version -> isVersionCompatible(version, false, true))
                    .map(obj -> new VersionInfo(
                            getRawVersion(obj.get("version_number").getAsString()),
                            (obj.has("changelog") && !obj.get("changelog").isJsonNull()) ? obj.get("changelog").getAsString() : null
                    )).collect(Collectors.toList());
        }

        if (compatible.isEmpty()) {
            compatible = versions.asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .map(obj -> new VersionInfo(
                            getRawVersion(obj.get("version_number").getAsString()),
                            (obj.has("changelog") && !obj.get("changelog").isJsonNull()) ? obj.get("changelog").getAsString() : null
                    )).collect(Collectors.toList());
        }

        if (compatible.isEmpty()) return null;

        String latest = compatible.stream().map(VersionInfo::version).max(ModrinthUpdateChecker::compareVersions).orElse(null);

        if (latest == null) return null;

        List<VersionInfo> updates = compatible.stream()
                .filter(v -> compareVersions(v.version(), installedRaw) > 0)
                .sorted((a, b) -> compareVersions(a.version(), b.version()))
                .collect(Collectors.toList());

        return new UpdateResult(latest, updates);
    }

    /**
     * Modrinth version objects include "game_versions" and "loaders". [page:2]
     */
    protected boolean isVersionCompatible(JsonObject version) {
        return isVersionCompatible(version, true, true);
    }

    protected boolean isVersionCompatible(JsonObject version, boolean requireLoaderMatch, boolean requireMcMatch) {
        JsonArray gameVersions = version.get("game_versions").getAsJsonArray();
        JsonArray loaders = version.get("loaders").getAsJsonArray();

        boolean mcOk = !requireMcMatch || (minecraftVersion == null) || gameVersions.contains(new JsonPrimitive(minecraftVersion));
        boolean loaderOk = !requireLoaderMatch || loaders.contains(new JsonPrimitive(loader));
        return mcOk && loaderOk;
    }

    /**
     * Normalizes a version string to a comparable value.
     */
    public static String getRawVersion(String version) {
        if (version == null || version.isEmpty()) return "";
        version = version.replaceAll("^\\D+", "");
        String[] split = version.split("\\+");
        return split[0];
    }

    /**
     * Builds a multiline hover component like:
     * <p>
     * 1.1:
     * Change1
     * Change2
     * 1.2:
     * Change1
     * <p>
     * It respects multi-line changelog strings by splitting on line separators.
     */
    public static Component buildAggregatedChangelogHover(List<VersionInfo> updates) {
        if (updates == null || updates.isEmpty()) {
            return Component.translatable("voxelmap.update.noChangelogAvailable");
        }

        VersionInfo latest = updates.stream()
                .max((a, b) -> compareVersions(a.version(), b.version()))
                .orElse(updates.get(updates.size() - 1));

        Component out = Component.translatable("voxelmap.update.changes").setStyle(Style.EMPTY.withColor(red)).append("\n");
        out = out.copy().append(Component.literal(latest.version() + ":").setStyle(Style.EMPTY.withColor(red)));

        String changelog = (latest.changelog() == null) ? "" : latest.changelog();
        String[] lines = changelog.split("\\R", -1);
        if (lines.length == 0 || (lines.length == 1 && lines[0].isBlank())) {
            return out.copy().append(Component.literal("\n ").append(Component.translatable("voxelmap.update.noChangelogProvided")).setStyle(Style.EMPTY.withColor(green)));
        }
        for (String line : lines) {
            out = out.copy().append(Component.literal("\n " + line).setStyle(Style.EMPTY.withColor(green)));
        }

        return out;
    }

    public static void checkUpdates() {
        if (!VoxelConstants.getVoxelMapInstance().getMapOptions().updateNotifier) {
            return;
        }
        String modVersion = getBuildProperty("forkVersion",
                VoxelConstants.getModVersion() == null || VoxelConstants.getModVersion().isBlank()
                        ? "0.01"
                        : VoxelConstants.getModVersion());
        String projectId = getBuildProperty("modrinthId", "cVrDroCh");

        if (modVersion == null) return;

        String mcVersion = SharedConstants.getCurrentVersion().name();

        new ModrinthUpdateChecker(projectId, VoxelConstants.getModApiBridge().getModLoader(), mcVersion).checkUpdates(modVersion, result -> {
            if (result == null || result.latestVersion() == null) {
                VoxelConstants.getLogger().warn("Update check failed or returned no compatible versions.");
                VoxelConstants.getMinecraft().execute(() ->
                        VoxelConstants.getMinecraft().gui.hud.getChat().addClientSystemMessage(
                                Component.literal("[VoxelMap] Update check failed.")));
                return;
            }
            String installedRaw = getRawVersion(modVersion);
            if (compareVersions(installedRaw, result.latestVersion()) >= 0) {
                VoxelConstants.getLogger().info("Voxelmap is up to date.");
                return;
            }

            String url = "https://modrinth.com/mod/" + projectId + "/version/" + result.latestVersion();
            Component prefix = Component.translatable("voxelmap.update.prefix", result.latestVersion()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(green)));
            Component suffix = Component.translatable("voxelmap.update.suffix").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(green)));
            Component hover = buildAggregatedChangelogHover(result.updates());
            Style linkStyle = Style.EMPTY.withColor(TextColor.fromRgb(red)).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(url))).withHoverEvent(new HoverEvent.ShowText(hover));

            Component link = Component.translatable("voxelmap.update.link").setStyle(linkStyle);
            Component msg = prefix.copy().append(link).append(suffix);
            VoxelConstants.getMinecraft().execute(() -> VoxelConstants.getMinecraft().gui.hud.getChat().addClientSystemMessage(msg));
        });
    }

    public static int compareVersions(String left, String right) {
        String[] a = splitVersion(left);
        String[] b = splitVersion(right);
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            String pa = i < a.length ? a[i] : "0";
            String pb = i < b.length ? b[i] : "0";
            int cmp = comparePart(pa, pb);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static String[] splitVersion(String version) {
        if (version == null || version.isBlank()) {
            return new String[0];
        }
        return version.trim().split("[^0-9A-Za-z]+");
    }

    private static int comparePart(String a, String b) {
        boolean aNum = a.chars().allMatch(Character::isDigit);
        boolean bNum = b.chars().allMatch(Character::isDigit);
        if (aNum && bNum) {
            String aa = a.replaceFirst("^0+(?!$)", "");
            String bb = b.replaceFirst("^0+(?!$)", "");
            if (aa.length() != bb.length()) {
                return Integer.compare(aa.length(), bb.length());
            }
            return aa.compareTo(bb);
        }
        return a.compareToIgnoreCase(b);
    }

    private static Properties loadBuildProperties() {
        Properties properties = new Properties();
        try (var stream = ModrinthUpdateChecker.class.getClassLoader().getResourceAsStream("voxelmap-build.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (Exception exception) {
            VoxelConstants.getLogger().warn("Unable to load voxelmap-build.properties", exception);
        }
        return properties;
    }

    public static String getBuildProperty(String key, String fallback) {
        String value = BUILD_PROPERTIES.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            return fallback;
        }
        return value.trim();
    }
}
