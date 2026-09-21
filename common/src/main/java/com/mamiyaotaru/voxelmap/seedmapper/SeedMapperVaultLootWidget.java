package com.mamiyaotaru.voxelmap.seedmapper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Small loot-viewer panel used by the Trial Chambers map-marker action. */
public final class SeedMapperVaultLootWidget {
    public static final int WIDTH = 270;
    public static final int HEIGHT = 122;

    private static final int BUTTON_WIDTH = 34;
    private static final int BUTTON_HEIGHT = 16;

    private final int x;
    private final int y;
    private final List<SeedMapperVaultService.VaultPrediction> normalPredictions;
    private final List<SeedMapperVaultService.VaultPrediction> ominousPredictions;
    private boolean ominous;
    private int predictionIndex;
    private boolean closeRequested;

    public SeedMapperVaultLootWidget(int x, int y,
                                     List<SeedMapperVaultService.VaultPrediction> normalPredictions,
                                     List<SeedMapperVaultService.VaultPrediction> ominousPredictions) {
        this.x = x;
        this.y = y;
        this.normalPredictions = normalPredictions == null ? List.of() : List.copyOf(normalPredictions);
        this.ominousPredictions = ominousPredictions == null ? List.of() : List.copyOf(ominousPredictions);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Font font) {
        List<SeedMapperVaultService.VaultPrediction> predictions = activePredictions();
        graphics.fill(this.x, this.y, this.x + WIDTH, this.y + HEIGHT, 0xEE101010);
        graphics.fill(this.x, this.y, this.x + WIDTH, this.y + 2, 0xFFB87BFF);
        graphics.fill(this.x, this.y + HEIGHT - 2, this.x + WIDTH, this.y + HEIGHT, 0xFF4A315E);

        String mode = this.ominous ? "Ominous" : "Normal";
        int number = predictions.isEmpty() ? 0 : predictions.get(Math.min(this.predictionIndex, predictions.size() - 1)).offset();
        graphics.text(font, Component.literal("Vault Loot (" + mode + ")"), this.x + 8, this.y + 7, 0xFFFFFFFF);
        graphics.text(font, Component.literal(predictions.isEmpty() ? "Unavailable" : "Reward #" + number),
                this.x + 8, this.y + 21, 0xFFD0D0D0);

        int contentY = this.y + 38;
        if (predictions.isEmpty()) {
            graphics.text(font, Component.literal("No prediction is available for this version."),
                    this.x + 8, contentY, 0xFFFF8080);
        } else {
            SeedMapperVaultService.VaultPrediction prediction = predictions.get(Math.min(this.predictionIndex, predictions.size() - 1));
            if (prediction.items().isEmpty()) {
                graphics.text(font, Component.literal("The vault generated no visible items."),
                        this.x + 8, contentY, 0xFFD0D0D0);
            } else {
                int row = 0;
                for (String item : prediction.items()) {
                    if (contentY + row * font.lineHeight > this.y + HEIGHT - 26) {
                        break;
                    }
                    graphics.text(font, Component.literal(trimToWidth(item, font, WIDTH - 16)),
                            this.x + 8, contentY + row * font.lineHeight, 0xFFFFFFFF);
                    row++;
                }
            }
            graphics.text(font, Component.literal("State: " + prediction.state()),
                    this.x + 8, this.y + HEIGHT - 18, 0xFF8E8E8E);
        }

        drawButton(graphics, font, this.x + WIDTH - 110, this.y + 6, "Normal", !this.ominous,
                mouseX, mouseY);
        drawButton(graphics, font, this.x + WIDTH - 70, this.y + 6, "Omin.", this.ominous,
                mouseX, mouseY);
        drawButton(graphics, font, this.x + WIDTH - 36, this.y + 6, "X", false,
                mouseX, mouseY);

        drawButton(graphics, font, this.x + WIDTH - 72, this.y + HEIGHT - 22, "<", false,
                mouseX, mouseY);
        drawButton(graphics, font, this.x + WIDTH - 36, this.y + HEIGHT - 22, ">", false,
                mouseX, mouseY);
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isMouseOver(event.x(), event.y())) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        if (inside(mouseX, mouseY, this.x + WIDTH - 110, this.y + 6, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            this.ominous = false;
            this.predictionIndex = 0;
            return true;
        }
        if (inside(mouseX, mouseY, this.x + WIDTH - 70, this.y + 6, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            this.ominous = true;
            this.predictionIndex = 0;
            return true;
        }
        if (inside(mouseX, mouseY, this.x + WIDTH - 36, this.y + 6, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            this.closeRequested = true;
            return true;
        }
        if (inside(mouseX, mouseY, this.x + WIDTH - 72, this.y + HEIGHT - 22, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            this.predictionIndex = Math.max(0, this.predictionIndex - 1);
            return true;
        }
        if (inside(mouseX, mouseY, this.x + WIDTH - 36, this.y + HEIGHT - 22, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            this.predictionIndex = Math.min(Math.max(0, activePredictions().size() - 1), this.predictionIndex + 1);
            return true;
        }
        return true;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX <= this.x + WIDTH
                && mouseY >= this.y && mouseY <= this.y + HEIGHT;
    }

    public boolean shouldClose() {
        return this.closeRequested;
    }

    private List<SeedMapperVaultService.VaultPrediction> activePredictions() {
        return this.ominous ? this.ominousPredictions : this.normalPredictions;
    }

    private void drawButton(GuiGraphicsExtractor graphics, Font font, int buttonX, int buttonY,
                            String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        int color = selected ? 0xFF66468A : hovered ? 0xFF4A4A4A : 0xFF252525;
        graphics.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, color);
        graphics.centeredText(font, Component.literal(label), buttonX + BUTTON_WIDTH / 2,
                buttonY + 4, 0xFFFFFFFF);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String trimToWidth(String value, Font font, int maxWidth) {
        if (value == null || value.isEmpty() || font.width(value) <= maxWidth) {
            return value == null ? "" : value;
        }
        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }
}
