package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiSeedMapperCrackingMods extends GuiScreenMinimap {
    private static final Component TITLE = Component.literal("Recommended Seed Cracking Mods");
    private static final String BEDROCK_CRACKER_URL = "https://github.com/xpple/seed-cracking";
    private static final String NETHER_BEDROCK_CRACKER_URL = "https://github.com/xpple/NetherBedrockCracker";
    private static final String SEEDCRACKERX_URL = "https://github.com/19MisterX98/SeedcrackerX";

    public GuiSeedMapperCrackingMods(Screen parent) {
        this.lastScreen = parent;
    }

    @Override
    public void init() {
        int buttonWidth = 300;
        int left = this.width / 2 - buttonWidth / 2;
        int y = this.height / 6 + 36;
        int rowGap = 28;

        addRenderableWidget(new Button.Builder(Component.literal("Bedrock Cracker"), ConfirmLinkScreen.confirmLink(this, BEDROCK_CRACKER_URL))
                .bounds(left, y, buttonWidth, 20)
                .build());
        y += rowGap;

        addRenderableWidget(new Button.Builder(Component.literal("Nether Bedrock Cracker"), ConfirmLinkScreen.confirmLink(this, NETHER_BEDROCK_CRACKER_URL))
                .bounds(left, y, buttonWidth, 20)
                .build());
        y += rowGap;

        addRenderableWidget(new Button.Builder(Component.literal("SeedCrackerX"), ConfirmLinkScreen.confirmLink(this, SEEDCRACKERX_URL))
                .bounds(left, y, buttonWidth, 20)
                .build());

        addRenderableWidget(new Button.Builder(Component.literal("Back"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 32, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.getFont(), TITLE, this.width / 2, this.height / 6 + 8, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
