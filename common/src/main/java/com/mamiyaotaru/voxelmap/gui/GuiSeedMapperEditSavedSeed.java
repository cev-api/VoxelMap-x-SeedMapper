package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class GuiSeedMapperEditSavedSeed extends GuiScreenMinimap {
    private final SeedMapperSettingsManager settings;
    private final String originalKey;
    private final String originalSeed;
    private EditBox keyInput;
    private EditBox seedInput;

    public GuiSeedMapperEditSavedSeed(GuiSeedMapperSavedSeeds parent, String key, String seed) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        this.originalKey = key;
        this.originalSeed = seed;
    }

    @Override
    public void init() {
        int left = this.width / 2 - 250;
        int y = this.height / 4 + 8;

        keyInput = new EditBox(font, left, y + 18, 500, 20, Component.literal("Server Key"));
        keyInput.setMaxLength(256);
        keyInput.setValue(originalKey != null ? originalKey : settings.getCurrentServerKey());
        addRenderableWidget(keyInput);

        seedInput = new EditBox(font, left, y + 66, 500, 20, Component.literal("Seed"));
        seedInput.setMaxLength(256);
        seedInput.setValue(originalSeed != null ? originalSeed : "");
        addRenderableWidget(seedInput);

        setInitialFocus(keyInput);

        addRenderableWidget(new Button.Builder(Component.literal("Save"), button -> saveAndClose())
                .bounds(this.width / 2 - 120, y + 112, 90, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Cancel"), button -> onClose())
                .bounds(this.width / 2 + 30, y + 112, 90, 20).build());
    }

    private void saveAndClose() {
        String newKey = keyInput.getValue().trim();
        String newSeed = seedInput.getValue().trim();
        if (newKey.isEmpty()) {
            return;
        }
        if (originalKey != null && !originalKey.equals(newKey)) {
            settings.putSavedSeed(originalKey, "");
        }
        settings.putSavedSeed(newKey, newSeed);
        MapSettingsManager.instance.saveAll();
        if (this.lastScreen instanceof GuiSeedMapperSavedSeeds savedSeedsScreen) {
            savedSeedsScreen.reloadEntries();
        }
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        return super.charTyped(characterEvent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.getFont(), Component.literal("Edit Saved Seed"), this.width / 2, 20, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = this.width / 2 - 250;
        int y = this.height / 4 + 8;
        graphics.text(this.getFont(), "Server Key", left, y + 4, 0xFFFFFFFF);
        graphics.text(this.getFont(), "Seed", left, y + 52, 0xFFFFFFFF);
    }
}
