package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SeedMapperChestLootWidget {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 78;
    private static final Identifier CHEST_CONTAINER = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/seedmapper/chest_container.png");
    private static final int ITEM_SLOT_SIZE = 18;
    private static final int BUTTON_W = 12;
    private static final int BUTTON_H = 12;
    private static final int BUTTON_Y_OFFSET = 4;
    private static final int BUTTON_X_OFFSET = 146;

    private final int x;
    private final int y;
    private int chestIndex = 0;
    private final List<SeedMapperChestLootData> chestDataList;
    private final List<List<ClientTooltipComponent>> extraChestInfo = new ArrayList<>();

    private List<ClientTooltipComponent> pendingItemTooltip;
    private int pendingTooltipX;
    private int pendingTooltipY;

    public SeedMapperChestLootWidget(int x, int y, List<SeedMapperChestLootData> chestDataList) {
        this.x = x;
        this.y = y;
        this.chestDataList = chestDataList;

        for (SeedMapperChestLootData chestData : this.chestDataList) {
            List<ClientTooltipComponent> tooltips = new ArrayList<>();
            tooltips.add(ClientTooltipComponent.create(Component.literal("Piece: " + chestData.pieceName()).getVisualOrderText()));
            BlockPos chestPos = chestData.chestPos();
            tooltips.add(ClientTooltipComponent.create(Component.literal("Chest: X " + chestPos.getX() + ", Z " + chestPos.getZ()).getVisualOrderText()));
            tooltips.add(ClientTooltipComponent.create(Component.literal("Loot table: " + chestData.lootTable()).getVisualOrderText()));
            tooltips.add(ClientTooltipComponent.create(Component.literal("Loot seed: " + chestData.lootSeed()).getVisualOrderText()));
            this.extraChestInfo.add(tooltips);
        }
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Font font) {
        this.pendingItemTooltip = null;

        graphics.fill(this.x, this.y, this.x + WIDTH, this.y + HEIGHT, 0xFF000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST_CONTAINER, this.x, this.y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);

        SeedMapperChestLootData chestData = this.chestDataList.get(this.chestIndex);
        Component title = Component.translatable(chestData.feature().translationKey())
                .append(Component.literal(" (" + (this.chestIndex + 1) + "/" + this.chestDataList.size() + ")"));

        int minX = this.x + 8;
        int minY = this.y + 6;
        graphics.text(font, title, minX, minY, 0xFFFFFFFF);

        int titleWidth = font.width(title.getVisualOrderText());
        if (mouseX >= minX && mouseX <= minX + titleWidth && mouseY >= minY && mouseY <= minY + font.lineHeight) {
            List<ClientTooltipComponent> tooltips = this.extraChestInfo.get(this.chestIndex);
            graphics.tooltip(font, tooltips, minX - 8, this.y - tooltips.size() * font.lineHeight - 8, DefaultTooltipPositioner.INSTANCE, null);
        }

        minY += 12;
        boolean tooltipRendered = false;
        for (int row = 0; row < 3; row++) {
            int rowY = minY + row * ITEM_SLOT_SIZE;
            for (int column = 0; column < 9; column++) {
                ItemStack item = chestData.container().getItem(row * 9 + column);
                if (item == ItemStack.EMPTY) {
                    continue;
                }
                int itemX = minX + column * ITEM_SLOT_SIZE;
                graphics.item(item, itemX, rowY);
                graphics.itemDecorations(font, item, itemX, rowY);

                if (!tooltipRendered && mouseX >= itemX && mouseX <= itemX + ITEM_SLOT_SIZE && mouseY >= rowY && mouseY <= rowY + ITEM_SLOT_SIZE) {
                    Minecraft minecraft = Minecraft.getInstance();
                    var tooltipLines = item.getTooltipLines(
                            net.minecraft.world.item.Item.TooltipContext.of(minecraft.level),
                            minecraft.player,
                            minecraft.options.advancedItemTooltips ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED : net.minecraft.world.item.TooltipFlag.Default.NORMAL
                    );
                    this.pendingItemTooltip = tooltipLines.stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
                    this.pendingTooltipX = mouseX;
                    this.pendingTooltipY = mouseY;
                    tooltipRendered = true;
                }
            }
        }

        int leftX = this.x + BUTTON_X_OFFSET;
        int buttonY = this.y + BUTTON_Y_OFFSET;
        int rightX = leftX + BUTTON_W + 2;
        graphics.fill(leftX, buttonY, leftX + BUTTON_W, buttonY + BUTTON_H, 0xCC202020);
        graphics.fill(rightX, buttonY, rightX + BUTTON_W, buttonY + BUTTON_H, 0xCC202020);
        graphics.centeredText(font, Component.literal("<"), leftX + BUTTON_W / 2, buttonY + 2, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(">"), rightX + BUTTON_W / 2, buttonY + 2, 0xFFFFFFFF);
    }

    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (mouseButtonEvent.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();

        int leftX = this.x + BUTTON_X_OFFSET;
        int buttonY = this.y + BUTTON_Y_OFFSET;
        int rightX = leftX + BUTTON_W + 2;
        if (mouseX >= leftX && mouseX <= leftX + BUTTON_W && mouseY >= buttonY && mouseY <= buttonY + BUTTON_H) {
            this.chestIndex = Math.max(0, this.chestIndex - 1);
            return true;
        }
        if (mouseX >= rightX && mouseX <= rightX + BUTTON_W && mouseY >= buttonY && mouseY <= buttonY + BUTTON_H) {
            this.chestIndex = Math.min(this.chestDataList.size() - 1, this.chestIndex + 1);
            return true;
        }
        return false;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX <= this.x + WIDTH
                && mouseY >= this.y && mouseY <= this.y + HEIGHT;
    }

    public List<ClientTooltipComponent> getPendingItemTooltip() {
        return this.pendingItemTooltip;
    }

    public int getPendingTooltipX() {
        return this.pendingTooltipX;
    }

    public int getPendingTooltipY() {
        return this.pendingTooltipY;
    }
}

