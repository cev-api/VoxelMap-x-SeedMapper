package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiValueSliderMinimap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperEspStyle;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperEspTarget;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

public class GuiSeedMapperEspProfiles extends GuiScreenMinimap {
    private enum ColorTarget {
        OUTLINE,
        FILL
    }

    private static final double[] TIMEOUT_OPTIONS = {0.0D, 0.5D, 1.0D, 2.0D, 5.0D, 10.0D, 15.0D, 30.0D, 60.0D};
    private final SeedMapperSettingsManager settings;
    private final MapSettingsManager mapSettings;
    private SeedMapperEspTarget activeTarget = SeedMapperEspTarget.BLOCK_HIGHLIGHT;

    private Button profileButton;
    private Button timeoutButton;
    private Button fillEnabledButton;
    private Button rainbowButton;
    private Button outlineColorButton;
    private Button outlineColorPickerButton;
    private Button fillColorButton;
    private Button fillColorPickerButton;
    private GuiValueSliderMinimap outlineAlphaSlider;
    private GuiValueSliderMinimap fillAlphaSlider;
    private GuiValueSliderMinimap rainbowSpeedSlider;

    private GuiColorPickerContainer colorPicker;
    private Button colorPickerModeButton;
    private Button colorPickerApplyButton;
    private Button colorPickerCancelButton;
    private ColorTarget activeColorTarget;
    private boolean swallowNextMouseRelease;

    public GuiSeedMapperEspProfiles(Screen parent) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        this.mapSettings = VoxelConstants.getVoxelMapInstance().getMapOptions();
    }

    @Override
    public void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = this.height / 6 + 8;

        profileButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            activeTarget = SeedMapperEspTarget.values()[(activeTarget.ordinal() + 1) % SeedMapperEspTarget.values().length];
            refreshLabels();
        }).bounds(left, y, 310, 20).build());

        y += 24;
        timeoutButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.espTimeoutMinutes = cycleOption(settings.espTimeoutMinutes, TIMEOUT_OPTIONS);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(left, y, 310, 20).build());

        y += 24;
        fillEnabledButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            activeStyle().fillEnabled = !activeStyle().fillEnabled;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(left, y, 150, 20).build());
        rainbowButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            activeStyle().rainbow = !activeStyle().rainbow;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y += 24;
        outlineColorButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {})
                .bounds(left, y, 118, 20).build());
        outlineColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.OUTLINE))
                .bounds(left + 122, y, 28, 20).build());
        fillColorButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {})
                .bounds(right, y, 118, 20).build());
        fillColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.FILL))
                .bounds(right + 122, y, 28, 20).build());

        y += 24;
        outlineAlphaSlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 150, 20, activeStyle().outlineAlpha, 0.0D, 1.0D, value -> {
            activeStyle().outlineAlpha = value;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Outline Alpha: " + formatDecimal(value)));
        fillAlphaSlider = addRenderableWidget(new GuiValueSliderMinimap(right, y, 150, 20, activeStyle().fillAlpha, 0.0D, 1.0D, value -> {
            activeStyle().fillAlpha = value;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Fill Alpha: " + formatDecimal(value)));

        y += 24;
        rainbowSpeedSlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 310, 20, activeStyle().rainbowSpeed, 0.05D, 5.0D, value -> {
            activeStyle().rainbowSpeed = value;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Rainbow Speed: " + formatDecimal(value)));

        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 34, 200, 20).build());

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
        profileButton.setMessage(Component.literal("Profile: " + activeTarget.displayName().replace(" ESP", "")));
        timeoutButton.setMessage(Component.literal("Timeout: " + formatTimeout(settings.espTimeoutMinutes)));
        fillEnabledButton.setMessage(Component.literal("Fill Enabled: " + toggleText(activeStyle().fillEnabled)));
        rainbowButton.setMessage(Component.literal("Rainbow: " + toggleText(activeStyle().rainbow)));
        outlineColorButton.setMessage(Component.literal("Outline Color: " + activeStyle().outlineColor));
        fillColorButton.setMessage(Component.literal("Fill Color: " + activeStyle().fillColor));
        outlineAlphaSlider.setActualValue(activeStyle().outlineAlpha);
        fillAlphaSlider.setActualValue(activeStyle().fillAlpha);
        rainbowSpeedSlider.setActualValue(activeStyle().rainbowSpeed);
    }

    private SeedMapperEspStyle activeStyle() {
        return settings.getEspStyle(activeTarget);
    }

    private String toggleText(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private String formatDecimal(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String formatTimeout(double timeoutMinutes) {
        if (timeoutMinutes <= 0.0D) {
            return "Off";
        }
        if (timeoutMinutes >= 1.0D) {
            return formatDecimal(timeoutMinutes) + " min";
        }
        return Integer.toString((int) Math.round(timeoutMinutes * 60.0D)) + " sec";
    }

    private double cycleOption(double current, double[] options) {
        int index = 0;
        for (int i = 0; i < options.length; i++) {
            if (Math.abs(options[i] - current) < 0.0001D) {
                index = i;
                break;
            }
        }
        return options[(index + 1) % options.length];
    }

    private void openColorPicker(ColorTarget colorTarget) {
        activeColorTarget = colorTarget;
        int currentColor = parseColorForPicker(colorTarget == ColorTarget.OUTLINE ? activeStyle().outlineColor : activeStyle().fillColor,
                0x00CFFF);
        colorPicker.setColor(currentColor);
        swallowNextMouseRelease = true;
    }

    private int parseColorForPicker(String colorText, int fallbackRgb) {
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
        activeStyle().useCommandColor = false;
        if (activeColorTarget == ColorTarget.OUTLINE) {
            activeStyle().outlineColor = hex;
        } else if (activeColorTarget == ColorTarget.FILL) {
            activeStyle().fillColor = hex;
        }
        activeColorTarget = null;
        MapSettingsManager.instance.saveAll();
        refreshLabels();
    }

    private void cancelColorPickerSelection() {
        activeColorTarget = null;
        refreshLabels();
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
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (!isColorPickerOpen()) {
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        boolean handled = false;
        handled = colorPicker.mouseClicked(mouseButtonEvent, doubleClick) || handled;
        handled = colorPickerModeButton.mouseClicked(mouseButtonEvent, doubleClick) || handled;
        handled = colorPickerApplyButton.mouseClicked(mouseButtonEvent, doubleClick) || handled;
        handled = colorPickerCancelButton.mouseClicked(mouseButtonEvent, doubleClick) || handled;
        if (handled) {
            swallowNextMouseRelease = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        if (swallowNextMouseRelease) {
            swallowNextMouseRelease = false;
            return true;
        }
        if (!isColorPickerOpen()) {
            return super.mouseReleased(mouseButtonEvent);
        }
        colorPicker.mouseReleased(mouseButtonEvent);
        return true;
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
        if (isColorPickerOpen()) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.getFont(), Component.literal("ESP Settings"), this.width / 2, 20, 0xFFFFFFFF);
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
}
