package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.persistent.ServerSeedStore;
import com.mamiyaotaru.voxelmap.persistent.VoxelMapDataStore;
import com.mamiyaotaru.voxelmap.util.TextUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One searchable catalog for server/world seeds and SeedMapper overrides. */
public final class GuiServerSeeds extends GuiScreenMinimap {
    private record Entry(String server, String world, String seed, Path file) {
        String label() { return server + (world.equals("all") ? "" : " / " + TextUtils.descrubName(world)) + " [" + (file == null ? "SeedMapper" : "World Map") + "]"; }
    }
    private final List<Entry> entries = new ArrayList<>(), visible = new ArrayList<>();
    private EditBox search, seedInput;
    private Button copy, save, delete;
    private int seedFilter;
    private int selected = -1, offset;
    private String status = "";
    public GuiServerSeeds(Screen parent) { lastScreen = parent; }
    @Override public void init() {
        search = addRenderableWidget(new EditBox(font, 14, 32, Math.max(100, width - 152), 20, Component.literal("Search servers")));
        addRenderableWidget(new Button.Builder(filterLabel(), button -> {
            seedFilter = (seedFilter + 1) % 3;
            button.setMessage(filterLabel()); filter();
        }).bounds(width - 132, 32, 118, 20).build());
        search.setHint(Component.literal("Search servers…")); search.setMaxLength(256);
        search.setResponder(value -> filter());
        seedInput = addRenderableWidget(new EditBox(font, 14, height - 60, Math.max(100, width - 28), 20, Component.literal("Seed")));
        seedInput.setMaxLength(256);
        int buttonWidth = Math.min(90, (width - 30) / 4 - 4), x = (width - (buttonWidth + 4) * 4) / 2;
        copy = addRenderableWidget(new Button.Builder(Component.literal("Copy Seed"), button -> {
            Entry entry = selection(); if (entry != null) { minecraft.keyboardHandler.setClipboard(entry.seed()); status = "Seed copied"; }
        }).bounds(x, height - 30, buttonWidth, 20).build());
        save = addRenderableWidget(new Button.Builder(Component.literal("Save Edit"), button -> update(seedInput.getValue().trim())).bounds(x + buttonWidth + 4, height - 30, buttonWidth, 20).build());
        delete = addRenderableWidget(new Button.Builder(Component.literal("Delete Seed"), button -> update("")).bounds(x + (buttonWidth + 4) * 2, height - 30, buttonWidth, 20).build());
        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose()).bounds(x + (buttonWidth + 4) * 3, height - 30, buttonWidth, 20).build());
        reload();
    }
    private Component filterLabel() {
        return Component.literal(switch (seedFilter) {
            case 1 -> "With seeds";
            case 2 -> "Without seeds";
            default -> "All servers";
        });
    }
    private Entry selection() { return selected >= 0 && selected < visible.size() ? visible.get(selected) : null; }
    private int rows() { return Math.max(1, (height - 150) / 20); }
    private void reload() {
        Entry previous = selection();
        entries.clear();
        try (var files = Files.list(VoxelMapDataStore.getGlobalRoot().toPath())) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".points") && Files.isRegularFile(path)).sorted().toList()) {
                String server = TextUtils.descrubName(file.getFileName().toString().replaceFirst("\\.points$", ""));
                try {
                    var seeds = ServerSeedStore.read(file);
                    if (seeds.isEmpty()) entries.add(new Entry(server, "all", "", file));
                    else seeds.forEach((world, seed) -> entries.add(new Entry(server, world, seed, file)));
                } catch (java.io.IOException failure) { status = "Could not read " + server; }
            }
        } catch (java.io.IOException failure) { status = "Could not read server data"; }
        java.util.Set<Path> knownFiles = new java.util.HashSet<>();
        for (Entry entry : entries) knownFiles.add(entry.file());
        Path root = VoxelMapDataStore.getGlobalRoot().toPath();
        var servers = new net.minecraft.client.multiplayer.ServerList(minecraft);
        servers.load();
        for (int i = 0; i < servers.size(); i++) {
            String server = com.mamiyaotaru.voxelmap.persistent.VoxelMapDataConfig.getInstance().resolveCanonical(servers.get(i).ip);
            if (server.endsWith(":25565")) server = server.substring(0, server.length() - 6);
            Path file = root.resolve(TextUtils.scrubNameFile(server) + ".points");
            if (knownFiles.add(file)) entries.add(new Entry(server, "all", "", file));
        }
        Path cache = root.resolve("cache");
        if (Files.isDirectory(cache)) try (var directories = Files.list(cache)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                String name = directory.getFileName().toString();
                Path file = root.resolve(name + ".points");
                if (knownFiles.add(file)) entries.add(new Entry(TextUtils.descrubName(name), "all", "", file));
            }
        } catch (java.io.IOException failure) { status = "Could not read cached servers"; }
        VoxelConstants.getVoxelMapInstance().getSeedMapperOptions().getSavedSeedsSnapshot().forEach((server, seed) -> entries.add(new Entry(server, "all", seed, null)));
        entries.sort(Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER)); filter();
        if (previous != null) for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            if (entry.server().equals(previous.server()) && entry.world().equals(previous.world()) && java.util.Objects.equals(entry.file(), previous.file())) {
                selected = i; offset = Math.max(0, Math.min(i, visible.size() - rows())); select(); break;
            }
        }
    }
    private void filter() {
        if (search == null || seedInput == null) return;
        visible.clear(); String query = search.getValue().toLowerCase(java.util.Locale.ROOT);
        for (Entry entry : entries) if ((seedFilter == 0 || (seedFilter == 1) == !entry.seed().isBlank())
                && entry.label().toLowerCase(java.util.Locale.ROOT).contains(query)) visible.add(entry);
        selected = visible.isEmpty() ? -1 : 0; offset = 0; select();
    }
    private void select() {
        Entry entry = selection(); seedInput.setValue(entry == null ? "" : entry.seed());
        save.active = entry != null; delete.active = entry != null && !entry.seed().isBlank(); copy.active = delete.active;
    }
    private void update(String value) {
        Entry entry = selection(); if (entry == null) return;
        try {
            var voxelMap = VoxelConstants.getVoxelMapInstance();
            if (entry.file() == null) {
                var settings = voxelMap.getSeedMapperOptions(); settings.putSavedSeed(entry.server(), value);
                if (entry.server().equals(settings.getCurrentServerKey())) settings.loadSavedSeedForCurrentServer();
                MapSettingsManager.instance.saveAll();
            } else {
                ServerSeedStore.update(entry.file(), entry.world(), value);
                if (entry.file().toAbsolutePath().normalize().equals(voxelMap.getDataStore().getPointsFile().toPath().toAbsolutePath().normalize()))
                    voxelMap.getWaypointManager().updateStoredWorldSeed(entry.world(), value);
            }
            voxelMap.getMap().forceFullRender(true);
            status = value.isBlank() ? "Seed deleted" : "Seed saved";
            reload();
        } catch (java.io.IOException | IllegalArgumentException failure) { status = "Could not save: " + failure.getMessage(); }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && event.x() >= 14 && event.x() < width - 14 && event.y() >= 62 && event.y() < 62 + rows() * 20) {
            int index = offset + (int) ((event.y() - 62) / 20);
            if (index < visible.size()) { selected = index; select(); if (doubleClick) setFocused(seedInput); }
            return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (y >= 62 && y < 62 + rows() * 20) {
            offset = Math.max(0, Math.min(Math.max(0, visible.size() - rows()), offset + (amount < 0 ? 1 : -1))); return true;
        }
        return super.mouseScrolled(x, y, horizontal, amount);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(font, Component.literal("Servers & Seeds"), width / 2, 12, 0xffffffff);
        graphics.fill(14, 62, width - 14, 62 + rows() * 20, 0xb0000000);
        for (int row = 0; row < rows() && offset + row < visible.size(); row++) {
            int index = offset + row, y = 62 + row * 20; Entry entry = visible.get(index);
            if (index == selected) graphics.fill(15, y, width - 15, y + 20, 0xff303030);
            String text = entry.label() + " → " + (entry.seed().isBlank() ? "No saved seed" : entry.seed());
            graphics.text(font, font.plainSubstrByWidth(text, width - 44), 20, y + 6, 0xffffffff);
        }
        graphics.text(font, font.plainSubstrByWidth(status, width - 28), 14, height - 77, 0xffdddddd);
        if (visible.size() > rows()) {
            int listHeight = rows() * 20, thumb = Math.max(12, listHeight * rows() / visible.size());
            int y = 62 + (listHeight - thumb) * offset / (visible.size() - rows());
            graphics.fill(width - 18, 62, width - 15, 62 + listHeight, 0xff202020);
            graphics.fill(width - 18, y, width - 15, y + thumb, 0xff888888);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
