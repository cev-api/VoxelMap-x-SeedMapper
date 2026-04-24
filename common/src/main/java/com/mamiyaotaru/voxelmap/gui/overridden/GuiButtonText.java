package com.mamiyaotaru.voxelmap.gui.overridden;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class GuiButtonText extends Button.Plain {
    private boolean editing;
    private final EditBox textField;

    public GuiButtonText(Font fontRenderer, int x, int y, int width, int height, Component message, OnPress onPress) {
        super (x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.textField = new EditBox(fontRenderer, x + 1, y + 1, width - 2, height - 2, Component.empty());
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int right = x + this.getWidth();
        int bottom = y + this.getHeight();
        boolean hovered = this.active && this.isHovered();
        int borderColor = editing ? 0xFF9CC7FF : hovered ? 0xFF8FA7C8 : 0xFF1A2028;

        graphics.fill(x, y, right, bottom, 0xAA05070A);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, borderColor);
        graphics.fillGradient(x + 2, y + 2, right - 2, bottom - 2, 0xFF28313B, 0xFF202832);
        graphics.fill(x + 3, y + 3, right - 3, y + 4, 0x44FFFFFF);
        if (editing) {
            textField.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }
        super.extractContents(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (editing) {
            boolean handled = textField.mouseClicked(mouseButtonEvent, doubleClick);
            if (handled) {
                return true;
            }
        }

        boolean pressed = super.mouseClicked(mouseButtonEvent, doubleClick);
        this.setEditing(pressed);
        if (pressed) {
            return textField.mouseClicked(mouseButtonEvent, doubleClick);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        if (editing) {
            return textField.mouseReleased(mouseButtonEvent);
        }
        return super.mouseReleased(mouseButtonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        if (editing) {
            return textField.mouseDragged(mouseButtonEvent, deltaX, deltaY);
        }
        return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
        if (editing) {
            this.setFocused(true);
        }

        textField.setFocused(editing);

        textField.setX(this.getX() + 1);
        textField.setY(this.getY() + 1);
        textField.setWidth(this.getWidth() - 2);
        textField.setHeight(this.getHeight() - 2);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        if (!(editing)) {
            return super.keyPressed(keyEvent);
        }
        if (keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_KP_ENTER && keyCode != GLFW.GLFW_KEY_TAB) {
            return textField.keyPressed(keyEvent);
        }

        setEditing(false);
        return false;
    }


    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (!(editing)) {
            return super.charTyped(characterEvent);
        }
        if (characterEvent.codepoint() != '\r') {
            return textField.charTyped(characterEvent);
        }

        setEditing(false);
        return false;
    }

    public boolean isEditing() { return editing; }

    public void setText(String text) { textField.setValue(text); }

    public String getText() { return textField.getValue(); }

    public void setMaxLength(int maxLength) { textField.setMaxLength(maxLength); }
}
