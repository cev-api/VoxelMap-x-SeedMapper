package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.chunksync.ChunkSharePlayerSettings;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommandHandler;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiChunkSyncLayers extends GuiScreenMinimap {
    private GuiChunkSyncPlayerList list;

    public GuiChunkSyncLayers(Screen parent) {
        super();
        this.lastScreen = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int listWidth = Math.min(width - 40, 520);
        int listX = (width - listWidth) / 2;
        list = new GuiChunkSyncPlayerList(this, listX, 40, listWidth, Math.max(60, height - 80));
        addRenderableWidget(list);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20).build());
    }

    void refreshPlayerLayerList() {
        if (list != null) {
            list.rebuildEntries();
        }
    }

    void removePlayerLayer(String slug) {
        ChunkSyncCommandHandler.runFromGui("remove " + slug);
        refreshPlayerLayerList();
    }

    void setPlayerLayerColor(String slug, String colorText) {
        ChunkSharePlayerSettings.setColor(slug, parseColor(colorText, ChunkSharePlayerSettings.get(slug).rgb()));
        refreshPlayerLayerList();
    }

    void openPlayerLayerColorPicker(String slug) {
        minecraft.gui.setScreen(new GuiColorEditScreen(this, Component.translatable("options.voxelmap.sharing.layerColor"),
                () -> "#" + String.format("%06X", ChunkSharePlayerSettings.get(slug).rgb() & 0x00FFFFFF),
                value -> setPlayerLayerColor(slug, value)));
    }

    private static int parseColor(String colorText, int fallbackRgb) {
        String normalized = colorText == null ? "" : colorText.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (!normalized.matches("#[0-9a-fA-F]{6}")) {
            return fallbackRgb & 0x00FFFFFF;
        }
        try {
            return Integer.parseInt(normalized.substring(1), 16) & 0x00FFFFFF;
        } catch (NumberFormatException ignored) {
            return fallbackRgb & 0x00FFFFFF;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(getFont(), Component.translatable("options.voxelmap.sharing.layers"), width / 2, 16, 0xFFFFFFFF);
        if (list != null && list.children().isEmpty()) {
            graphics.centeredText(getFont(), Component.translatable("options.voxelmap.sharing.noLayers"), width / 2, height / 2, 0xFFA0A0A0);
        }
    }
}
