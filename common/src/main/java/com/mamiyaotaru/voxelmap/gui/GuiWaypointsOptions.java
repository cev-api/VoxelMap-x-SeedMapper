package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiOptionButtonMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiOptionSliderMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GuiWaypointsOptions extends GuiScreenMinimap {
    private static final EnumOptionsMinimap[] WAYPOINT_OPTIONS = { EnumOptionsMinimap.WAYPOINT_DISTANCE, EnumOptionsMinimap.WAYPOINT_SIGN_SCALE, EnumOptionsMinimap.WAYPOINT_BEACONS, EnumOptionsMinimap.AUTO_PORTAL_WAYPOINTS, EnumOptionsMinimap.CONFIRM_WAYPOINT_DELETE, EnumOptionsMinimap.DEATHPOINTS, EnumOptionsMinimap.WAYPOINT_DISTANCE_UNIT_CONVERSION, EnumOptionsMinimap.SHOW_IN_GAME_WAYPOINT_NAMES, EnumOptionsMinimap.SHOW_IN_GAME_WAYPOINT_DISTANCES };
    private static final EnumOptionsMinimap[] COMPASS_OPTIONS = { EnumOptionsMinimap.WAYPOINT_COMPASS, EnumOptionsMinimap.WAYPOINT_COMPASS_SHOW_COORDS, EnumOptionsMinimap.WAYPOINT_COMPASS_TEXT_OUTLINE, EnumOptionsMinimap.WAYPOINT_COMPASS_ICON_RANGE, EnumOptionsMinimap.WAYPOINT_COMPASS_X, EnumOptionsMinimap.WAYPOINT_COMPASS_Y, EnumOptionsMinimap.WAYPOINT_COMPASS_TEXT_OPACITY, EnumOptionsMinimap.WAYPOINT_COMPASS_OUTLINE_OPACITY, EnumOptionsMinimap.WAYPOINT_COMPASS_BACKGROUND_OPACITY };
    private final MapSettingsManager options;
    private final List<SectionHeader> sectionHeaders = new ArrayList<>();
    protected Component screenTitle;

    public GuiWaypointsOptions(Screen parent, MapSettingsManager options) {
        this.lastScreen = parent;

        this.options = options;
    }

    @Override
    public void init() {
        this.screenTitle = Component.translatable("options.minimap.waypoints.title");
        this.sectionHeaders.clear();
        boolean twoColumns = this.getWidth() >= 560;
        int optionWidth = twoColumns ? 250 : Math.min(320, this.getWidth() - 40);
        int optionGap = 12;
        int optionX = twoColumns ? this.getWidth() / 2 - optionWidth - optionGap / 2 : this.getWidth() / 2 - optionWidth / 2;
        int nextY = addSection("Waypoint Display", WAYPOINT_OPTIONS, 52, optionX, optionWidth, optionGap, twoColumns);
        addSection("Compass", COMPASS_OPTIONS, nextY + 14, optionX, optionWidth, optionGap, twoColumns);

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> this.onClose()).bounds(this.getWidth() / 2 - 100, this.getHeight() - 26, 200, 20).build());
    }

    private int addSection(String title, EnumOptionsMinimap[] sectionOptions, int startY, int optionX, int optionWidth, int optionGap, boolean twoColumns) {
        sectionHeaders.add(new SectionHeader(title, optionX, startY, twoColumns ? optionWidth * 2 + optionGap : optionWidth));
        int optionY = startY + 14;

        for (int index = 0; index < sectionOptions.length; index++) {
            EnumOptionsMinimap option = sectionOptions[index];
            if (option.getType() == EnumOptionsMinimap.Type.FLOAT) {
                float value = this.options.getFloatValue(option);
                switch (option) {
                    case WAYPOINT_DISTANCE -> {
                        if (value < 0.0F) {
                            value = 10001.0F;
                        }

                        value = (value - 50.0F) / 9951.0F;
                    }
                    case WAYPOINT_SIGN_SCALE -> value = value - 0.5F;
                    case WAYPOINT_COMPASS_ICON_RANGE -> {
                        if (value < 0.0F) {
                            value = 10001.0F;
                        }
                        value = (value - 100.0F) / 9901.0F;
                    }
                    case WAYPOINT_COMPASS_X, WAYPOINT_COMPASS_TEXT_OPACITY, WAYPOINT_COMPASS_OUTLINE_OPACITY, WAYPOINT_COMPASS_BACKGROUND_OPACITY -> value = value / 100.0F;
                    case WAYPOINT_COMPASS_Y -> value = value / 40.0F;
                }
                int x = twoColumns ? optionX + (index % 2) * (optionWidth + optionGap) : optionX;
                int y = optionY + 22 * (twoColumns ? index >> 1 : index);
                this.addRenderableWidget(new GuiOptionSliderMinimap(x, y, optionWidth, option, value, this.options));
            } else {
                int x = twoColumns ? optionX + (index % 2) * (optionWidth + optionGap) : optionX;
                int y = optionY + 22 * (twoColumns ? index >> 1 : index);
                GuiOptionButtonMinimap optionButton = new GuiOptionButtonMinimap(x, y, optionWidth, option, Component.literal(this.options.getKeyText(option)), this::optionClicked);

                switch (option) {
                    case DEATHPOINTS -> optionButton.setTooltip(Tooltip.create(Component.translatable("options.minimap.waypoints.deathpoints.tooltip")));
                    case WAYPOINT_DISTANCE_UNIT_CONVERSION -> optionButton.setTooltip(Tooltip.create(Component.translatable("options.minimap.waypoints.distanceUnitConversion.tooltip")));
                }

                this.addRenderableWidget(optionButton);
            }
        }

        int rowCount = twoColumns ? (sectionOptions.length + 1) / 2 : sectionOptions.length;
        return optionY + rowCount * 22;
    }

    protected void optionClicked(Button par1GuiButton) {
        EnumOptionsMinimap option = ((GuiOptionButtonMinimap) par1GuiButton).returnEnumOptions();
        MapSettingsManager.updateBooleanOrListValue(this.options, option);
        par1GuiButton.setMessage(Component.literal(this.options.getKeyText(option)));

        for (GuiEventListener item : children()) {
            if (!(item instanceof GuiOptionButtonMinimap button)) {
                continue;
            }

            switch (button.returnEnumOptions()) {
                case SHOW_IN_GAME_WAYPOINT_NAMES -> button.active = this.options.showWaypointSigns;
                case SHOW_IN_GAME_WAYPOINT_DISTANCES -> {
                    button.setMessage(Component.literal(this.options.getKeyText(EnumOptionsMinimap.SHOW_IN_GAME_WAYPOINT_DISTANCES)));
                    button.active = this.options.showWaypointSigns;
                }
                case WAYPOINT_BEACONS, AUTO_PORTAL_WAYPOINTS -> button.active = this.options.waypointsAllowed;
            }
        }

        for (GuiEventListener item : children()) {
            if (!(item instanceof GuiOptionSliderMinimap slider)) {
                continue;
            }

            if (slider.returnEnumOptions() == EnumOptionsMinimap.WAYPOINT_SIGN_SCALE) {
                slider.active = this.options.showWaypointSigns;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.font, this.screenTitle, this.getWidth() / 2, 20, 0xFFFFFFFF);

        for (SectionHeader sectionHeader : sectionHeaders) {
            graphics.fill(sectionHeader.x(), sectionHeader.y() + 8, sectionHeader.x() + 34, sectionHeader.y() + 9, 0xFF8FA7C8);
            graphics.text(this.font, Component.literal(sectionHeader.title()), sectionHeader.x() + 44, sectionHeader.y() + 4, 0xFFE6EAF0, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private record SectionHeader(String title, int x, int y, int width) {
    }
}
