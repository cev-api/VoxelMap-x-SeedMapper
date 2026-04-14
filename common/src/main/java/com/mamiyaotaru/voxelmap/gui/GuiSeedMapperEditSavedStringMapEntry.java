package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiButtonText;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperSavedStringMap.Mode;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class GuiSeedMapperEditSavedStringMapEntry extends GuiScreenMinimap {
    private final SeedMapperSettingsManager settings;
    private final Mode mode;
    private final String originalKey;
    private final String originalValue;

    private GuiButtonText keyInput;
    private GuiButtonText valueInput;

    public GuiSeedMapperEditSavedStringMapEntry(GuiSeedMapperSavedStringMap parent, Mode mode, String key, String value) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        this.mode = mode;
        this.originalKey = key;
        this.originalValue = value;
    }

    @Override
    public void init() {
        int left = this.width / 2 - 250;
        int y = this.height / 4 + 8;
        String valueLabel = mode == Mode.URLS ? "URL" : "Cache Path";

        keyInput = new GuiButtonText(font, left, y + 18, 500, 20, Component.literal("Server Key"), button -> setActiveEditor(keyInput));
        keyInput.setMaxLength(256);
        keyInput.setText(originalKey != null ? originalKey : settings.getCurrentServerKey());
        addRenderableWidget(keyInput);

        valueInput = new GuiButtonText(font, left, y + 66, 500, 20, Component.literal(valueLabel), button -> setActiveEditor(valueInput));
        valueInput.setMaxLength(4096);
        valueInput.setText(originalValue != null ? originalValue : "");
        addRenderableWidget(valueInput);

        addRenderableWidget(new Button.Builder(Component.literal("Save"), button -> saveAndClose())
                .bounds(this.width / 2 - 120, y + 112, 90, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Cancel"), button -> onClose())
                .bounds(this.width / 2 + 30, y + 112, 90, 20).build());
    }

    private void setActiveEditor(GuiButtonText target) {
        if (keyInput != null) {
            keyInput.setEditing(keyInput == target);
        }
        if (valueInput != null) {
            valueInput.setEditing(valueInput == target);
        }
    }

    private GuiButtonText getActiveEditor() {
        if (keyInput != null && keyInput.isEditing()) {
            return keyInput;
        }
        if (valueInput != null && valueInput.isEditing()) {
            return valueInput;
        }
        return null;
    }

    private void saveAndClose() {
        String newKey = keyInput.getText().trim();
        String newValue = valueInput.getText().trim();
        if (newKey.isEmpty()) {
            return;
        }
        if (originalKey != null && !originalKey.equals(newKey)) {
            putValue(originalKey, "");
        }
        putValue(newKey, newValue);
        MapSettingsManager.instance.saveAll();
        if (this.lastScreen instanceof GuiSeedMapperSavedStringMap savedScreen) {
            savedScreen.reloadEntries();
        }
        onClose();
    }

    private void putValue(String key, String value) {
        switch (mode) {
            case URLS -> settings.putDatapackSavedUrl(key, value);
            case CACHE_PATHS -> settings.putDatapackSavedCachePath(key, value);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        GuiButtonText active = getActiveEditor();
        if (active != null) {
            active.keyPressed(keyEvent);
            return true;
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        GuiButtonText active = getActiveEditor();
        if (active != null) {
            return active.charTyped(characterEvent);
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.getFont(), Component.literal(mode == Mode.URLS ? "Edit Saved URL" : "Edit Saved Cache Path"), this.width / 2, 20, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = this.width / 2 - 250;
        int y = this.height / 4 + 8;
        graphics.text(this.getFont(), "Server Key", left, y + 4, 0xFFFFFFFF);
        graphics.text(this.getFont(), mode == Mode.URLS ? "URL" : "Cache Path", left, y + 52, 0xFFFFFFFF);
    }
}
