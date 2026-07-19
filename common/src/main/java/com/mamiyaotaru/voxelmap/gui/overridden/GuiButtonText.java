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

import java.util.List;
import java.util.Locale;

public class GuiButtonText extends Button.Plain {
    private boolean editing;
    private final EditBox textField;
    private List<String> autocompleteOptions = List.of();
    private final Font fontRenderer;

    public GuiButtonText(Font fontRenderer, int x, int y, int width, int height, Component message, OnPress onPress) {
        super (x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.fontRenderer = fontRenderer;
        this.textField = new EditBox(fontRenderer, x + 1, y + 1, width - 2, height - 2, Component.empty());
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (GuiOptionButtonMinimap.isOutsideGui(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight())) {
            return;
        }
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
    public void setX(int x) {
        super.setX(x);
        textField.setX(x + 1);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        textField.setY(y + 1);
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
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            String completion = getAutocompleteCompletion();
            if (completion != null) {
                setText(completion);
                return true;
            }
            setEditing(false);
            return false;
        }
        if (keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_KP_ENTER) {
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

    public void setAutocompleteOptions(List<String> options) {
        autocompleteOptions = options == null ? List.of() : List.copyOf(options);
    }

    public void extractAutocompleteSuggestions(GuiGraphicsExtractor graphics) {
        if (!editing) return;
        List<String> matches = getAutocompleteMatches();
        if (matches.isEmpty()) return;

        int x = getX();
        int y = getY() + getHeight();
        int rowHeight = 12;
        int rows = Math.min(6, matches.size());
        graphics.fill(x, y, x + getWidth(), y + rows * rowHeight + 2, 0xF010141A);
        graphics.fill(x, y, x + getWidth(), y + 1, 0xFF9CC7FF);
        for (int i = 0; i < rows; i++) {
            int rowY = y + 2 + i * rowHeight;
            if (i == 0) {
                graphics.fill(x + 1, rowY - 1, x + getWidth() - 1, rowY + rowHeight - 1, 0x664A83B8);
            }
            graphics.text(fontRenderer, Component.literal(matches.get(i)), x + 5, rowY + 1, 0xFFE6EAF0, false);
        }
    }

    private String getAutocompleteCompletion() {
        return getAutocompleteMatches().stream()
                .filter(option -> !option.equalsIgnoreCase(getText().trim()))
                .findFirst()
                .orElse(null);
    }

    private List<String> getAutocompleteMatches() {
        String current = getText().trim().toLowerCase(Locale.ROOT);
        if (current.isEmpty()) return List.of();
        return autocompleteOptions.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(current))
                .toList();
    }
}
