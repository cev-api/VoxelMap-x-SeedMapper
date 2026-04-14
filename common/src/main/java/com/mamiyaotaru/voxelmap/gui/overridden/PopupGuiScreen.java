package com.mamiyaotaru.voxelmap.gui.overridden;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;

public abstract class PopupGuiScreen extends GuiScreenMinimap implements IPopupGuiScreen {
    private final ArrayList<Popup> popups = new ArrayList<>();

    protected boolean hasOpenPopup() {
        return !popups.isEmpty();
    }

    @Override
    public void removed() {
    }

    public void createPopup(int x, int y, int directX, int directY, int minWidth, ArrayList<Popup.PopupEntry> entries) {
        popups.add(new Popup(x, y, directX, directY, minWidth, entries, this));
    }

    public void clearPopups() {
        popups.clear();
    }

    public boolean clickedPopup(double x, double y) {
        if (this.popups.isEmpty()) {
            return false;
        }

        // Only the top-most popup should receive clicks.
        Popup top = this.popups.get(this.popups.size() - 1);
        boolean clickedTop = top.clickedMe(x, y);
        if (!clickedTop || top.shouldClose()) {
            this.popups.remove(top);
        }
        return clickedTop;
    }

    @Override
    public boolean overPopup(int mouseX, int mouseY) {
        return !hasOpenPopup();
    }

    @Override
    public boolean popupOpen() {
        return popups.isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (hasOpenPopup()) {
            mouseX = 0;
            mouseY = 0;
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        for (Popup popup : this.popups) {
            popup.drawPopup(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (hasOpenPopup()) {
            this.clickedPopup(mouseButtonEvent.x(), mouseButtonEvent.y());
            return true;
        }

        return super.mouseClicked(mouseButtonEvent, doubleClick);
    }
}
