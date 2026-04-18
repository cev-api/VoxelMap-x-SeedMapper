package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiButtonText;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiValueSliderMinimap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

public class GuiRadarChunkOverlays extends GuiScreenMinimap {
    private enum ColorTarget {
        EXPLORED,
        NEW,
        OLD,
        BLOCK
    }

    private final RadarSettingsManager settings;
    private final MapSettingsManager mapSettings;
    private GuiButtonText exploredColorInput;
    private GuiButtonText newColorInput;
    private GuiButtonText oldColorInput;
    private GuiButtonText blockColorInput;
    private Button exploredColorPickerButton;
    private Button newColorPickerButton;
    private Button oldColorPickerButton;
    private Button blockColorPickerButton;
    private Button exploredToggle;
    private Button newerToggle;
    private Button liquidToggle;
    private Button blockToggle;
    private Button detectModeButton;
    private GuiValueSliderMinimap exploredOpacitySlider;
    private GuiValueSliderMinimap newOpacitySlider;
    private GuiValueSliderMinimap oldOpacitySlider;
    private GuiValueSliderMinimap blockOpacitySlider;
    private GuiColorPickerContainer colorPicker;
    private Button colorPickerModeButton;
    private Button colorPickerApplyButton;
    private Button colorPickerCancelButton;
    private Button clearExploredButton;
    private Button clearNewerNewChunksButton;
    private ColorTarget activeColorTarget;
    private boolean swallowNextMouseRelease;

    public GuiRadarChunkOverlays(Screen parent) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        this.mapSettings = VoxelConstants.getVoxelMapInstance().getMapOptions();
    }

    @Override
    public void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = this.height / 6;

        exploredToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.showExploredChunks = !settings.showExploredChunks;
            refreshLabels();
        }).bounds(left, y, 150, 20).build());
        newerToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.showNewerNewChunks = !settings.showNewerNewChunks;
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y += 24;
        liquidToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.newerNewChunksLiquidExploit = !settings.newerNewChunksLiquidExploit;
            refreshLabels();
        }).bounds(left, y, 150, 20).build());
        blockToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.newerNewChunksBlockUpdateExploit = !settings.newerNewChunksBlockUpdateExploit;
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y += 24;
        clearExploredButton = addRenderableWidget(new Button.Builder(Component.literal("Clear Explored Chunks"), button -> {
            VoxelConstants.getVoxelMapInstance().getExploredChunksManager().clearCurrentWorld();
        }).bounds(left, y, 150, 20).build());
        clearNewerNewChunksButton = addRenderableWidget(new Button.Builder(Component.literal("Clear New Chunks"), button -> {
            VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().clearCurrentWorldData();
        }).bounds(right, y, 150, 20).build());

        y += 24;
        detectModeButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.newerNewChunksDetectMode = settings.newerNewChunksDetectMode >= 2 ? 0 : settings.newerNewChunksDetectMode + 1;
            refreshLabels();
        }).bounds(left, y, 310, 20).build());

        y += 24;
        exploredColorInput = new GuiButtonText(font, left, y, 118, 20, Component.literal("Explored Color"), button -> setActiveEditor(exploredColorInput));
        exploredColorInput.setText(settings.exploredChunksColor);
        addRenderableWidget(exploredColorInput);
        exploredColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.EXPLORED))
                .bounds(left + 122, y, 28, 20).build());
        newColorInput = new GuiButtonText(font, right, y, 118, 20, Component.literal("New Chunk Color"), button -> setActiveEditor(newColorInput));
        newColorInput.setText(settings.newerNewChunksNewColor);
        addRenderableWidget(newColorInput);
        newColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.NEW))
                .bounds(right + 122, y, 28, 20).build());

        y += 24;
        oldColorInput = new GuiButtonText(font, left, y, 118, 20, Component.literal("Old Chunk Color"), button -> setActiveEditor(oldColorInput));
        oldColorInput.setText(settings.newerNewChunksOldColor);
        addRenderableWidget(oldColorInput);
        oldColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.OLD))
                .bounds(left + 122, y, 28, 20).build());
        blockColorInput = new GuiButtonText(font, right, y, 118, 20, Component.literal("Block Update Color"), button -> setActiveEditor(blockColorInput));
        blockColorInput.setText(settings.newerNewChunksBlockColor);
        addRenderableWidget(blockColorInput);
        blockColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.BLOCK))
                .bounds(right + 122, y, 28, 20).build());

        y += 24;
        exploredOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 150, 20, settings.exploredChunksOpacity, 0.0D, 100.0D, value -> {
            settings.exploredChunksOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Explored Opacity: " + (int) Math.round(value) + "%"));
        newOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(right, y, 150, 20, settings.newerNewChunksNewOpacity, 0.0D, 100.0D, value -> {
            settings.newerNewChunksNewOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "New Opacity: " + (int) Math.round(value) + "%"));

        y += 24;
        oldOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 150, 20, settings.newerNewChunksOldOpacity, 0.0D, 100.0D, value -> {
            settings.newerNewChunksOldOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Old Opacity: " + (int) Math.round(value) + "%"));
        blockOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(right, y, 150, 20, settings.newerNewChunksBlockOpacity, 0.0D, 100.0D, value -> {
            settings.newerNewChunksBlockOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Block Opacity: " + (int) Math.round(value) + "%"));

        y += 30;
        addRenderableWidget(new Button.Builder(Component.literal("Save"), button -> saveValues()).bounds(this.width / 2 - 75, y, 150, 20).build());
        y += 24;
        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose()).bounds(this.width / 2 - 75, y, 150, 20).build());

        boolean simpleMode = mapSettings.colorPickerMode == 0;
        colorPicker = new GuiColorPickerContainer(this.width / 2, this.height / 2, 200, 140, simpleMode, picker -> {});
        colorPickerModeButton = new Button.Builder(Component.literal(mapSettings.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleColorPickerMode)
                .bounds(0, 0, 66, 16)
                .build();
        colorPickerApplyButton = new Button.Builder(Component.translatable("gui.done"), button -> applyColorPickerSelection())
                .bounds(0, 0, 66, 18)
                .build();
        colorPickerCancelButton = new Button.Builder(Component.translatable("gui.cancel"), button -> cancelColorPickerSelection())
                .bounds(0, 0, 66, 18)
                .build();
        refreshLabels();
    }

    private void refreshLabels() {
        exploredToggle.setMessage(Component.literal("Explored Chunks: " + (settings.showExploredChunks ? "ON" : "OFF")));
        newerToggle.setMessage(Component.literal("New Chunk Detector: " + (settings.showNewerNewChunks ? "ON" : "OFF")));
        liquidToggle.setMessage(Component.literal("Liquid Exploit: " + (settings.newerNewChunksLiquidExploit ? "ON" : "OFF")));
        blockToggle.setMessage(Component.literal("Block Update Exploit: " + (settings.newerNewChunksBlockUpdateExploit ? "ON" : "OFF")));
        detectModeButton.setMessage(Component.literal("Detect Mode: " + switch (settings.newerNewChunksDetectMode) {
            case 1 -> "Ignore Block Exploit";
            case 2 -> "Block Exploit Mode";
            default -> "Normal";
        }));
        exploredOpacitySlider.setActualValue(settings.exploredChunksOpacity);
        newOpacitySlider.setActualValue(settings.newerNewChunksNewOpacity);
        oldOpacitySlider.setActualValue(settings.newerNewChunksOldOpacity);
        blockOpacitySlider.setActualValue(settings.newerNewChunksBlockOpacity);
        if (clearExploredButton != null) {
            clearExploredButton.active = settings.showExploredChunks;
        }
        if (clearNewerNewChunksButton != null) {
            clearNewerNewChunksButton.active = settings.showNewerNewChunks;
        }
    }

    private void saveValues() {
        settings.exploredChunksColor = sanitize(exploredColorInput.getText(), settings.exploredChunksColor);
        settings.newerNewChunksNewColor = sanitize(newColorInput.getText(), settings.newerNewChunksNewColor);
        settings.newerNewChunksOldColor = sanitize(oldColorInput.getText(), settings.newerNewChunksOldColor);
        settings.newerNewChunksBlockColor = sanitize(blockColorInput.getText(), settings.newerNewChunksBlockColor);
        MapSettingsManager.instance.saveAll();
        refreshLabels();
    }

    private String sanitize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        return normalized.matches("#[0-9a-fA-F]{6}") ? normalized.toUpperCase() : fallback;
    }

    private void setActiveEditor(GuiButtonText target) {
        if (exploredColorInput != null) exploredColorInput.setEditing(exploredColorInput == target);
        if (newColorInput != null) newColorInput.setEditing(newColorInput == target);
        if (oldColorInput != null) oldColorInput.setEditing(oldColorInput == target);
        if (blockColorInput != null) blockColorInput.setEditing(blockColorInput == target);
    }

    private GuiButtonText getActiveEditor() {
        if (exploredColorInput != null && exploredColorInput.isEditing()) return exploredColorInput;
        if (newColorInput != null && newColorInput.isEditing()) return newColorInput;
        if (oldColorInput != null && oldColorInput.isEditing()) return oldColorInput;
        if (blockColorInput != null && blockColorInput.isEditing()) return blockColorInput;
        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (isColorPickerOpen()) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelColorPickerSelection();
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applyColorPickerSelection();
                return true;
            }
            return true;
        }
        GuiButtonText active = getActiveEditor();
        if (active != null) {
            active.keyPressed(keyEvent);
            return true;
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            saveValues();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (isColorPickerOpen()) {
            return true;
        }
        GuiButtonText active = getActiveEditor();
        if (active != null) {
            return active.charTyped(characterEvent);
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (!isColorPickerOpen()) {
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        colorPicker.mouseClicked(mouseButtonEvent, doubleClick);
        colorPickerModeButton.mouseClicked(mouseButtonEvent, doubleClick);
        colorPickerApplyButton.mouseClicked(mouseButtonEvent, doubleClick);
        colorPickerCancelButton.mouseClicked(mouseButtonEvent, doubleClick);
        swallowNextMouseRelease = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        if (!isColorPickerOpen()) {
            return super.mouseReleased(mouseButtonEvent);
        }
        boolean swallowed = swallowNextMouseRelease;
        swallowNextMouseRelease = false;
        colorPicker.mouseReleased(mouseButtonEvent);
        colorPickerModeButton.mouseReleased(mouseButtonEvent);
        colorPickerApplyButton.mouseReleased(mouseButtonEvent);
        colorPickerCancelButton.mouseReleased(mouseButtonEvent);
        return swallowed || true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        if (!isColorPickerOpen()) {
            return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
        }
        colorPicker.mouseDragged(mouseButtonEvent, deltaX, deltaY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        if (!isColorPickerOpen()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
        }
        colorPicker.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.getFont(), Component.literal("Radar Chunk Overlay Options"), this.width / 2, 20, 0xFFFFFFFF);
        super.extractRenderState(graphics, isColorPickerOpen() ? 0 : mouseX, isColorPickerOpen() ? 0 : mouseY, delta);
        if (isColorPickerOpen()) {
            graphics.nextStratum();
            extractTransparentBackground(graphics);

            int popupX0 = colorPicker.getX() - (colorPicker.getWidth() / 2) - 30;
            int popupY0 = colorPicker.getY() - (colorPicker.getHeight() / 2) - 10;
            int popupW = colorPicker.getWidth() + 60;
            int popupH = colorPicker.getHeight() + 56;
            TooltipRenderUtil.extractTooltipBackground(graphics, popupX0, popupY0, popupW, popupH, null);

            colorPicker.extractRenderState(graphics, mouseX, mouseY, delta);
            int pickerColor = colorPicker.getColor() & 0x00FFFFFF;
            String colorText = "#" + String.format("%06X", pickerColor);
            int textX = (this.width - colorPicker.getWidth()) / 2;
            int textY = (this.height + colorPicker.getHeight()) / 2 + 8;
            int textWidth = this.getFont().width(colorText);
            graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, ARGB.opaque(pickerColor));
            graphics.fill(textX - 1, textY, textX + textWidth + 1, textY + 8, ARGB.black(0.2F));
            graphics.text(this.getFont(), colorText, textX, textY, 0xFFFFFFFF, false);

            int buttonsY = textY + 16;
            int centerX = this.width / 2;
            colorPickerModeButton.setPosition(centerX - 106, buttonsY);
            colorPickerApplyButton.setPosition(centerX - 32, buttonsY - 1);
            colorPickerCancelButton.setPosition(centerX + 42, buttonsY - 1);
            colorPickerModeButton.extractRenderState(graphics, mouseX, mouseY, delta);
            colorPickerApplyButton.extractRenderState(graphics, mouseX, mouseY, delta);
            colorPickerCancelButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    private void openColorPicker(ColorTarget target) {
        activeColorTarget = target;
        String current = switch (target) {
            case EXPLORED -> settings.exploredChunksColor;
            case NEW -> settings.newerNewChunksNewColor;
            case OLD -> settings.newerNewChunksOldColor;
            case BLOCK -> settings.newerNewChunksBlockColor;
        };
        colorPicker.setColor(parseColor(current, 0x00CFFF));
        swallowNextMouseRelease = true;
    }

    private boolean isColorPickerOpen() {
        return activeColorTarget != null;
    }

    private void cycleColorPickerMode(Button button) {
        mapSettings.colorPickerMode = mapSettings.colorPickerMode == 0 ? 1 : 0;
        colorPicker.updateMode(mapSettings.colorPickerMode == 0);
        colorPickerModeButton.setMessage(Component.literal(mapSettings.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)));
        MapSettingsManager.instance.saveAll();
    }

    private void applyColorPickerSelection() {
        String hex = "#" + String.format("%06X", colorPicker.getColor() & 0x00FFFFFF);
        switch (activeColorTarget) {
            case EXPLORED -> exploredColorInput.setText(hex);
            case NEW -> newColorInput.setText(hex);
            case OLD -> oldColorInput.setText(hex);
            case BLOCK -> blockColorInput.setText(hex);
        }
        activeColorTarget = null;
        saveValues();
    }

    private void cancelColorPickerSelection() {
        activeColorTarget = null;
    }

    private int parseColor(String colorText, int fallbackRgb) {
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
}
