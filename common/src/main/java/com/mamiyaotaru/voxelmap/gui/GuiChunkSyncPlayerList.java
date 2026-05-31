package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSharePlayerSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

class GuiChunkSyncPlayerList extends AbstractSelectionList<GuiChunkSyncPlayerList.PlayerLayerRow> {
    private final GuiRadarChunkOverlays parent;
    private final int rowLeft;
    private final int rowWidth;
    private List<ChunkSharePlayerSettings.PlayerLayer> layers = List.of();
    private String activeColorSlug;

    GuiChunkSyncPlayerList(GuiRadarChunkOverlays parent, int left, int top, int width, int height) {
        super(VoxelConstants.getMinecraft(), width, height, top, 22);
        this.parent = parent;
        this.rowLeft = left;
        this.rowWidth = width - 8;
        setX(left);
        rebuildEntries();
    }

    void rebuildEntries() {
        this.layers = new ArrayList<>(ChunkSharePlayerSettings.list());
        clearEntries();
        for (ChunkSharePlayerSettings.PlayerLayer layer : layers) {
            addEntry(new PlayerLayerRow(layer));
        }
    }

    @Override
    public int getRowWidth() {
        return rowWidth;
    }

    @Override
    protected int scrollBarX() {
        return rowLeft + rowWidth + 4;
    }

    String getActiveColorSlug() {
        return activeColorSlug;
    }

    void setActiveColorSlug(String activeColorSlug) {
        this.activeColorSlug = activeColorSlug;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
    }

    class PlayerLayerRow extends AbstractSelectionList.Entry<PlayerLayerRow> {
        private final ChunkSharePlayerSettings.PlayerLayer layer;
        private final Button visibleButton;
        private final EditBox colorInput;
        private final Button colorPickerButton;
        private final Button removeButton;

        PlayerLayerRow(ChunkSharePlayerSettings.PlayerLayer layer) {
            this.layer = layer;
            this.visibleButton = new Button.Builder(Component.literal(layer.enabled() ? "ON" : "OFF"), button -> {
                ChunkSharePlayerSettings.toggleEnabled(layer.slug());
                parent.refreshPlayerLayerList();
            }).bounds(0, 0, 54, 18).build();
            this.colorInput = new EditBox(parent.getFont(), 0, 0, 92, 18, Component.literal("Layer color"));
            this.colorInput.setMaxLength(7);
            this.colorInput.setValue(String.format("#%06X", layer.rgb()));
            this.colorPickerButton = new Button.Builder(Component.literal("..."), button -> parent.openPlayerLayerColorPicker(layer.slug()))
                    .bounds(0, 0, 28, 18).build();
            this.removeButton = new Button.Builder(Component.literal("Remove"), button -> {
                parent.removePlayerLayer(layer.slug());
            }).bounds(0, 0, 76, 18).build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int y = getY() + 2;
            graphics.text(parent.getFont(), layer.slug(), rowLeft + 6, y + 5, 0xFFFFFFFF, false);
            visibleButton.setPosition(rowLeft + rowWidth - 270, y);
            colorInput.setX(rowLeft + rowWidth - 210);
            colorInput.setY(y);
            colorPickerButton.setPosition(rowLeft + rowWidth - 112, y);
            removeButton.setPosition(rowLeft + rowWidth - 80, y);
            visibleButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
            colorInput.extractRenderState(graphics, mouseX, mouseY, tickDelta);
            colorPickerButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
            removeButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return visibleButton.mouseClicked(event, doubleClick)
                    || colorInput.mouseClicked(event, doubleClick)
                    || colorPickerButton.mouseClicked(event, doubleClick)
                    || removeButton.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            return visibleButton.mouseReleased(event)
                    || colorInput.mouseReleased(event)
                    || colorPickerButton.mouseReleased(event)
                    || removeButton.mouseReleased(event);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && colorInput.isFocused()) {
                parent.setPlayerLayerColor(layer.slug(), colorInput.getValue());
                return true;
            }
            return colorInput.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return colorInput.charTyped(event);
        }
    }
}
