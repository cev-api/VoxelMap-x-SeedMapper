package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperImportedDatapackManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperDatapackManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GuiSeedMapperDatapackStructures extends GuiScreenMinimap {
    private final SeedMapperSettingsManager settings;
    private final List<String> structureIds = new ArrayList<>();
    private Button enableAllButton;
    private Button disableAllButton;

    private int scrollOffset;

    public GuiSeedMapperDatapackStructures(Screen parent) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
    }

    @Override
    public void init() {
        int buttonY = this.height - 48;
        enableAllButton = addRenderableWidget(new Button.Builder(Component.literal("Enable All"), button -> enableAll())
                .bounds(this.width / 2 - 170, buttonY, 90, 20).build());
        disableAllButton = addRenderableWidget(new Button.Builder(Component.literal("Disable All"), button -> disableAll())
                .bounds(this.width / 2 - 60, buttonY, 90, 20).build());
        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 + 50, buttonY, 90, 20).build());
        reloadEntries();
    }

    private void reloadEntries() {
        structureIds.clear();
        structureIds.addAll(SeedMapperDatapackManager.readImportedStructureIds(settings.datapackCachePath));
        structureIds.sort(Comparator.comparing(s -> s, String.CASE_INSENSITIVE_ORDER));
        clampScroll();
        refreshButtons();
    }

    private void refreshButtons() {
        boolean hasEntries = !structureIds.isEmpty();
        if (enableAllButton != null) {
            enableAllButton.active = hasEntries;
        }
        if (disableAllButton != null) {
            disableAllButton.active = hasEntries;
        }
    }

    private void enableAll() {
        String worldKey = currentWorldKey();
        for (String id : structureIds) {
            settings.setDatapackStructureEnabled(worldKey, id, true);
        }
        MapSettingsManager.instance.saveAll();
        refreshButtons();
    }

    private void disableAll() {
        String worldKey = currentWorldKey();
        for (String id : structureIds) {
            settings.setDatapackStructureEnabled(worldKey, id, false);
        }
        MapSettingsManager.instance.saveAll();
        refreshButtons();
    }

    private void toggleStructure(String structureId) {
        String worldKey = currentWorldKey();
        boolean enabled = settings.isDatapackStructureEnabled(worldKey, structureId);
        settings.setDatapackStructureEnabled(worldKey, structureId, !enabled);
        MapSettingsManager.instance.saveAll();
        refreshButtons();
    }

    private int listX() {
        return this.width / 2 - 380;
    }

    private int listY() {
        return 48;
    }

    private int listWidth() {
        return 760;
    }

    private int listHeight() {
        return this.height - 120;
    }

    private int rowHeight() {
        return 22;
    }

    private int visibleRows() {
        return Math.max(1, listHeight() / rowHeight());
    }

    private void clampScroll() {
        int maxOffset = Math.max(0, structureIds.size() - visibleRows());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (super.mouseClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }

        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        if (mouseButtonEvent.button() == 0 && mouseX >= listX() && mouseX < listX() + listWidth() && mouseY >= listY() && mouseY < listY() + listHeight()) {
            int clickedRow = (int) ((mouseY - listY()) / rowHeight());
            int entryIndex = scrollOffset + clickedRow;
            if (entryIndex >= 0 && entryIndex < structureIds.size()) {
                toggleStructure(structureIds.get(entryIndex));
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        if (mouseX >= listX() && mouseX < listX() + listWidth() && mouseY >= listY() && mouseY < listY() + listHeight()) {
            if (amount < 0) {
                scrollOffset++;
            } else if (amount > 0) {
                scrollOffset--;
            }
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.getFont(), Component.literal("Datapack Structures"), this.width / 2, 20, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int x0 = listX();
        int y0 = listY();
        int x1 = x0 + listWidth();
        int y1 = y0 + listHeight();
        graphics.fill(x0, y0, x1, y1, 0xD0000000);
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y0, 0xFF808080);
        graphics.fill(x0 - 1, y1, x1 + 1, y1 + 1, 0xFF2F2F2F);
        graphics.fill(x0 - 1, y0, x0, y1, 0xFF808080);
        graphics.fill(x1, y0, x1 + 1, y1, 0xFF2F2F2F);

        int maxRows = visibleRows();
        for (int i = 0; i < maxRows; i++) {
            int entryIndex = scrollOffset + i;
            if (entryIndex >= structureIds.size()) {
                break;
            }

            int rowTop = y0 + i * rowHeight();
            int rowBottom = rowTop + rowHeight();
            String id = structureIds.get(entryIndex);
            boolean enabled = settings.isDatapackStructureEnabled(currentWorldKey(), id);
            if (!enabled) {
                graphics.fill(x0 + 2, rowTop + 1, x1 - 2, rowBottom - 1, 0xFF171717);
            }

            int previewSize = 10;
            int previewX = x0 + 8;
            int previewY = rowTop + 6;
            if (SeedMapperImportedDatapackManager.usesPotionIcon()) {
                Identifier potion = SeedMapperImportedDatapackManager.iconForStructureId(id);
                Identifier overlay = SeedMapperImportedDatapackManager.iconOverlayForStructureId(id);
                graphics.blit(RenderPipelines.GUI_TEXTURED, potion, previewX, previewY - 1, 0.0F, 0.0F, previewSize, previewSize, previewSize, previewSize, 0xFFFFFFFF);
                graphics.blit(RenderPipelines.GUI_TEXTURED, overlay, previewX, previewY - 1, 0.0F, 0.0F, previewSize, previewSize, previewSize, previewSize, SeedMapperImportedDatapackManager.colorForStructureId(id));
            } else {
                int color = SeedMapperImportedDatapackManager.colorForStructureId(id);
                graphics.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFF000000);
                graphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, color);
            }

            graphics.text(this.getFont(), id, x0 + 24, rowTop + 7, enabled ? 0xFFFFFFFF : 0xFF808080, false);
            Identifier toggleIcon = enabled ? VoxelConstants.getCheckMarkerTexture() : VoxelConstants.getCrossMarkerTexture();
            graphics.blit(RenderPipelines.GUI_TEXTURED, toggleIcon, x1 - 22, rowTop + 2, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
        }

        if (structureIds.isEmpty()) {
            graphics.centeredText(this.getFont(), Component.literal("No datapack structures were imported."), this.width / 2, y0 + 20, 0xFFA0A0A0);
        }
    }

    private String currentWorldKey() {
        String world = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentWorldName();
        String sub = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false);
        var level = com.mamiyaotaru.voxelmap.util.GameVariableAccessShim.getWorld();
        String dim = level == null ? "unknown" : level.dimension().identifier().toString();
        return (world == null ? "unknown" : world) + "|" + (sub == null ? "" : sub) + "|" + dim;
    }
}
