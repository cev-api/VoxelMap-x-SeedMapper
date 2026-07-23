package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Editor for the user-defined client-side transport commands used by the world map. */
public class GuiTransportShortcutsOptions extends GuiScreenMinimap {
    private final MapSettingsManager mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
    private final Component title = Component.literal("Transport Shortcuts");

    public GuiTransportShortcutsOptions(net.minecraft.client.gui.screens.Screen parent) {
        this.lastScreen = parent;
    }

    @Override
    public void init() {
        int left = width / 2 - 220;
        int row = height / 6 + 25;
        for (int i = 0; i < mapOptions.transportShortcuts.size(); i++) {
            MapSettingsManager.TransportShortcut shortcut = mapOptions.transportShortcuts.get(i);
            int y = row + i * 34;
            EditBox name = new EditBox(getFont(), left, y, 100, 20, Component.literal("Name"));
            name.setValue(shortcut.name);
            name.setMaxLength(64);
            addRenderableWidget(name);
            EditBox command = new EditBox(getFont(), left + 106, y, 220, 20, Component.literal("Command"));
            command.setValue(shortcut.command);
            command.setMaxLength(256);
            addRenderableWidget(command);
            addRenderableWidget(new Button.Builder(Component.literal(shortcut.visible ? "Shown" : "Hidden"), button -> {
                shortcut.visible = !shortcut.visible;
                button.setMessage(Component.literal(shortcut.visible ? "Shown" : "Hidden"));
                mapOptions.saveAll();
            }).bounds(left + 332, y, 72, 20).build());
            addRenderableWidget(new Button.Builder(Component.literal(shortcut.clientCommand ? "Client" : "Server"), button -> {
                shortcut.clientCommand = !shortcut.clientCommand;
                button.setMessage(Component.literal(shortcut.clientCommand ? "Client" : "Server"));
                mapOptions.saveAll();
            }).bounds(left + 410, y, 70, 20).build());
            final int index = i;
            addRenderableWidget(new Button.Builder(Component.literal("Remove"), button -> {
                syncFields();
                mapOptions.transportShortcuts.remove(index);
                mapOptions.saveAll();
                clearWidgets();
                init();
            }).bounds(left + 488, y, 70, 20).build());
        }

        int controlsY = row + mapOptions.transportShortcuts.size() * 34 + 12;
        addRenderableWidget(new Button.Builder(Component.literal("Add Shortcut"), button -> {
            syncFields();
            mapOptions.transportShortcuts.add(new MapSettingsManager.TransportShortcut("New Shortcut", "", true, false));
            mapOptions.saveAll();
            clearWidgets();
            init();
        }).bounds(width / 2 - 100, controlsY, 200, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Exclude Y: " + (mapOptions.transportExcludeY ? "ON" : "OFF")), button -> {
            syncFields();
            mapOptions.transportExcludeY = !mapOptions.transportExcludeY;
            button.setMessage(Component.literal("Exclude Y: " + (mapOptions.transportExcludeY ? "ON" : "OFF")));
            mapOptions.saveAll();
        }).bounds(width / 2 - 100, controlsY + 24, 200, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Show All In Main Menu: " + (mapOptions.transportShowAllInMainMenu ? "ON" : "OFF")), button -> {
            syncFields();
            mapOptions.transportShowAllInMainMenu = !mapOptions.transportShowAllInMainMenu;
            button.setMessage(Component.literal("Show All In Main Menu: " + (mapOptions.transportShowAllInMainMenu ? "ON" : "OFF")));
            mapOptions.saveAll();
        }).bounds(width / 2 - 100, controlsY + 48, 200, 20).build());
        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> {
            syncFields();
            onClose();
        }).bounds(width / 2 - 100, height - 26, 200, 20).build());
    }

    private void syncFields() {
        int field = 0;
        for (var child : children()) {
            if (child instanceof EditBox editBox) {
                int shortcutIndex = field++ / 2;
                if (shortcutIndex < mapOptions.transportShortcuts.size()) {
                    MapSettingsManager.TransportShortcut shortcut = mapOptions.transportShortcuts.get(shortcutIndex);
                    if (field % 2 == 1) shortcut.name = editBox.getValue().trim();
                    else {
                        shortcut.command = editBox.getValue().trim();
                        if (shortcutIndex == 0) mapOptions.teleportCommand = shortcut.command;
                    }
                }
            }
        }
        mapOptions.saveAll();
    }

    @Override
    public void onClose() {
        syncFields();
        super.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!isEmbeddedInParent()) graphics.centeredText(getFont(), title, width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(getFont(), Component.literal("Use %x, %y, %z, and %p as placeholders. Select Client or Server per shortcut."), width / 2, height / 6 + 5, 0xFFFFC857);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
