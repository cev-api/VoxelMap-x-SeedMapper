package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class GuiSeedMapperSavedStringMap extends GuiScreenMinimap {
    public enum Mode {
        URLS,
        CACHE_PATHS
    }

    private final SeedMapperSettingsManager settings;
    private final Mode mode;
    private final List<Map.Entry<String, String>> entries = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    private Button addButton;
    private Button editButton;
    private Button removeButton;

    public GuiSeedMapperSavedStringMap(Screen parent, Mode mode) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        this.mode = mode;
    }

    @Override
    public void init() {
        int buttonY = this.height - 48;
        addButton = addRenderableWidget(new Button.Builder(Component.literal("Add"), button ->
                this.minecraft.setScreen(new GuiSeedMapperEditSavedStringMapEntry(this, mode, null, null)))
                .bounds(this.width / 2 - 170, buttonY, 90, 20).build());
        editButton = addRenderableWidget(new Button.Builder(Component.literal("Edit"), button -> editSelected())
                .bounds(this.width / 2 - 60, buttonY, 90, 20).build());
        removeButton = addRenderableWidget(new Button.Builder(Component.literal("Remove"), button -> removeSelected())
                .bounds(this.width / 2 + 50, buttonY, 90, 20).build());
        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 + 160, buttonY, 90, 20).build());
        reloadEntries();
    }

    public void reloadEntries() {
        entries.clear();
        entries.addAll(snapshot().entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        if (entries.isEmpty()) {
            selectedIndex = -1;
            scrollOffset = 0;
        } else if (selectedIndex < 0) {
            selectedIndex = 0;
        } else if (selectedIndex >= entries.size()) {
            selectedIndex = entries.size() - 1;
        }
        clampScroll();
        refreshButtons();
    }

    private Map<String, String> snapshot() {
        return switch (mode) {
            case URLS -> settings.getDatapackSavedUrlsSnapshot();
            case CACHE_PATHS -> settings.getDatapackSavedCachePathsSnapshot();
        };
    }

    private void refreshButtons() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < entries.size();
        if (editButton != null) {
            editButton.active = hasSelection;
        }
        if (removeButton != null) {
            removeButton.active = hasSelection;
        }
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
        int maxOffset = Math.max(0, entries.size() - visibleRows());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
        if (selectedIndex >= 0) {
            if (selectedIndex < scrollOffset) {
                scrollOffset = selectedIndex;
            } else if (selectedIndex >= scrollOffset + visibleRows()) {
                scrollOffset = selectedIndex - visibleRows() + 1;
            }
        }
    }

    private void editSelected() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return;
        }
        Map.Entry<String, String> entry = entries.get(selectedIndex);
        this.minecraft.setScreen(new GuiSeedMapperEditSavedStringMapEntry(this, mode, entry.getKey(), entry.getValue()));
    }

    private void removeSelected() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return;
        }
        Map.Entry<String, String> entry = entries.get(selectedIndex);
        putValue(entry.getKey(), "");
        MapSettingsManager.instance.saveAll();
        reloadEntries();
    }

    private void putValue(String key, String value) {
        switch (mode) {
            case URLS -> settings.putDatapackSavedUrl(key, value);
            case CACHE_PATHS -> settings.putDatapackSavedCachePath(key, value);
        }
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
            if (entryIndex >= 0 && entryIndex < entries.size()) {
                selectedIndex = entryIndex;
                clampScroll();
                refreshButtons();
                if (doubleClick) {
                    editSelected();
                }
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
        graphics.centeredText(this.getFont(), Component.literal(mode == Mode.URLS ? "Saved URLs" : "Saved Cache Paths"), this.width / 2, 20, 0xFFFFFFFF);
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
            if (entryIndex >= entries.size()) {
                break;
            }
            int rowTop = y0 + i * rowHeight();
            int rowBottom = rowTop + rowHeight();
            if (entryIndex == selectedIndex) {
                graphics.fill(x0 + 2, rowTop + 1, x1 - 2, rowBottom - 1, 0xFF1B1B1B);
            }
            String line = entries.get(entryIndex).getKey() + " -> " + entries.get(entryIndex).getValue();
            graphics.text(this.getFont(), line, x0 + 8, rowTop + 7, 0xFFFFFFFF, false);
        }
    }
}
