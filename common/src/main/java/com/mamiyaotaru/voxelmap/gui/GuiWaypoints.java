package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.gui.overridden.Popup;
import com.mamiyaotaru.voxelmap.gui.overridden.PopupGuiScreen;
import com.mamiyaotaru.voxelmap.util.CommandUtils;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TreeSet;

public class GuiWaypoints extends PopupGuiScreen implements IGuiWaypoints {
    protected final MapSettingsManager options;
    protected final WaypointManager waypointManager;
    protected Component screenTitle;
    private GuiListWaypoints waypointList;
    private Button buttonEdit;
    private boolean editClicked;
    private Button buttonDelete;
    private boolean deleteClicked;
    private Button buttonHighlight;
    private Button buttonShare;
    private Button buttonTeleport;
    private Button buttonSortName;
    private Button buttonSortCreated;
    private Button buttonSortDistance;
    private Button buttonSortColor;
    protected EditBox filter;
    private boolean addClicked;
    private Component tooltip;
    protected Waypoint selectedWaypoint;
    private final LinkedHashSet<Waypoint> selectedWaypoints = new LinkedHashSet<>();
    private List<Waypoint> pendingDeleteWaypoints = List.of();
    protected Waypoint selectionAnchor;
    protected Waypoint highlightedWaypoint;
    protected Waypoint newWaypoint;
    private final Random generator = new Random();
    private boolean changedSort;
    private Component importStatus;

    public GuiWaypoints(Screen parentScreen) {
        lastScreen = parentScreen;

        options = VoxelConstants.getVoxelMapInstance().getMapOptions();
        waypointManager = VoxelConstants.getVoxelMapInstance().getWaypointManager();
        highlightedWaypoint = waypointManager.getHighlightedWaypoint();
    }

    @Override
    public void tick() {
    }

    @Override
    public void init() {
        screenTitle = Component.translatable("minimap.waypoints.title");
        waypointList = new GuiListWaypoints(this);

        addRenderableWidget(waypointList);
        addRenderableWidget(buttonSortName = new Button.Builder(Component.translatable("minimap.waypoints.sortByName"), button -> sortClicked(2)).bounds(getWidth() / 2 - 154, 34, 77, 20).build());
        addRenderableWidget(buttonSortDistance = new Button.Builder(Component.translatable("minimap.waypoints.sortByDistance"), button -> sortClicked(3)).bounds(getWidth() / 2 - 77, 34, 77, 20).build());
        addRenderableWidget(buttonSortCreated = new Button.Builder(Component.translatable("minimap.waypoints.sortByCreated"), button -> sortClicked(1)).bounds(getWidth() / 2, 34, 77, 20).build());
        addRenderableWidget(buttonSortColor = new Button.Builder(Component.translatable("minimap.waypoints.sortByColor"), button -> sortClicked(4)).bounds(getWidth() / 2 + 77, 34, 77, 20).build());

        int filterStringWidth = getFont().width(I18n.get("minimap.waypoints.filter") + ":");
        filter = new EditBox(getFont(), getWidth() / 2 - 153 + filterStringWidth + 5, getHeight() - 78, 305 - filterStringWidth - 5, 20, Component.empty());
        filter.setMaxLength(35);
        filter.setResponder(this::filterUpdated);

        addRenderableWidget(filter);
        setFocused(filter);
        addRenderableWidget(new Button.Builder(Component.translatable("minimap.waypoints.add"), button -> addWaypoint()).bounds(getWidth() / 2 - 154, getHeight() - 50, 74, 20).build());
        addRenderableWidget(buttonEdit = new Button.Builder(Component.translatable("selectServer.edit"), button -> editWaypoint(selectedWaypoint)).bounds(getWidth() / 2 - 76, getHeight() - 50, 74, 20).build());
        addRenderableWidget(buttonDelete = new Button.Builder(Component.translatable("selectServer.delete"), button -> deleteClicked()).bounds(getWidth() / 2 + 2, getHeight() - 50, 74, 20).build());
        addRenderableWidget(buttonHighlight = new Button.Builder(Component.translatable("minimap.waypoints.highlight"), button -> setHighlightedWaypoint()).bounds(getWidth() / 2 + 80, getHeight() - 50, 74, 20).build());
        int bottomButtonWidth = 74;
        int bottomGap = 4;
        int bottomLeft = getWidth() / 2 - (bottomButtonWidth * 6 + bottomGap * 5) / 2;
        addRenderableWidget(buttonTeleport = new Button.Builder(Component.translatable("minimap.waypoints.teleportTo"), button -> teleportClicked()).bounds(bottomLeft, getHeight() - 26, bottomButtonWidth, 20).build());
        addRenderableWidget(buttonShare = new Button.Builder(Component.translatable("minimap.waypoints.share"), button -> CommandUtils.sendWaypoint(selectedWaypoint)).bounds(bottomLeft + (bottomButtonWidth + bottomGap), getHeight() - 26, bottomButtonWidth, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Import Xaero"), button -> importWaypoints(true)).bounds(bottomLeft + (bottomButtonWidth + bottomGap) * 2, getHeight() - 26, bottomButtonWidth, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Import Wurst"), button -> importWaypoints(false)).bounds(bottomLeft + (bottomButtonWidth + bottomGap) * 3, getHeight() - 26, bottomButtonWidth, 20).build());
        addRenderableWidget(new Button.Builder(Component.translatable("menu.options"), button -> VoxelConstants.getMinecraft().setScreen(new GuiWaypointsOptions(this, options))).bounds(bottomLeft + (bottomButtonWidth + bottomGap) * 4, getHeight() - 26, bottomButtonWidth, 20).build());
        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose()).bounds(bottomLeft + (bottomButtonWidth + bottomGap) * 5, getHeight() - 26, bottomButtonWidth, 20).build());

        updateSelectionButtons();

        sort();
    }

    private void sort() {
        int sortKey = Math.abs(options.waypointSort);
        boolean ascending = options.waypointSort > 0;
        waypointList.sortBy(sortKey, ascending);
        String arrow = ascending ? "↑" : "↓";

        if (sortKey == 1) {
            buttonSortCreated.setMessage(Component.literal(arrow + " " + I18n.get("minimap.waypoints.sortByCreated") + " " + arrow));
        } else {
            buttonSortCreated.setMessage(Component.translatable("minimap.waypoints.sortByCreated"));
        }

        if (sortKey == 2) {
            buttonSortName.setMessage(Component.literal(arrow + " " + I18n.get("minimap.waypoints.sortByName") + " " + arrow));
        } else {
            buttonSortName.setMessage(Component.translatable("minimap.waypoints.sortByName"));
        }

        if (sortKey == 3) {
            buttonSortDistance.setMessage(Component.literal(arrow + " " + I18n.get("minimap.waypoints.sortByDistance") + " " + arrow));
        } else {
            buttonSortDistance.setMessage(Component.translatable("minimap.waypoints.sortByDistance"));
        }

        if (sortKey == 4) {
            buttonSortColor.setMessage(Component.literal(arrow + " " + I18n.get("minimap.waypoints.sortByColor") + " " + arrow));
        } else {
            buttonSortColor.setMessage(Component.translatable("minimap.waypoints.sortByColor"));
        }
    }

    private void deleteClicked() {
        if (!selectedWaypoints.isEmpty()) {
            pendingDeleteWaypoints = new ArrayList<>(selectedWaypoints);
            if (!options.confirmWaypointDelete) {
                deleteSelectedWaypoint();
                return;
            }

            createDeleteConfirmationPopup();
        }
    }

    private void teleportClicked() {
        boolean hasCeiling = VoxelConstants.getPlayer().level().dimensionType().hasCeiling();
        int minY = VoxelConstants.getPlayer().level().getMinY();
        int maxY = VoxelConstants.getPlayer().level().getMaxY();
        int targetY = selectedWaypoint.getY() > minY ? selectedWaypoint.getY() : (!hasCeiling ? maxY : 64);

        VoxelConstants.playerRunTeleportCommand(selectedWaypoint.getX(), targetY, selectedWaypoint.getZ());
        VoxelConstants.getMinecraft().setScreen(null);
    }

    protected void sortClicked(int id) {
        options.setWaypointSort(id);
        changedSort = true;
        sort();
    }

    private void filterUpdated(String string) {
        waypointList.updateFilter(string.toLowerCase());
    }

    @Override
    public boolean isEditing() {
        return editClicked;
    }

    @Override
    public void accept(boolean b) {
        if (deleteClicked) {
            deleteClicked = false;
            if (b) {
                deleteSelectedWaypoint();
            }

            VoxelConstants.getMinecraft().setScreen(this);
        }

        if (editClicked) {
            editClicked = false;
            if (b) {
                waypointManager.saveWaypoints();
            }

            VoxelConstants.getMinecraft().setScreen(this);
        }

        if (addClicked) {
            addClicked = false;
            if (b) {
                waypointManager.addWaypoint(newWaypoint);
                setSelectedWaypoint(newWaypoint);
            }

            VoxelConstants.getMinecraft().setScreen(this);
        }

    }

    protected void setSelectedWaypoint(Waypoint waypoint) {
        selectedWaypoints.clear();
        if (waypoint != null) {
            selectedWaypoints.add(waypoint);
            selectionAnchor = waypoint;
        }
        selectedWaypoint = waypoint;
        updateSelectionButtons();
    }

    protected void toggleSelectedWaypoint(Waypoint waypoint) {
        if (selectedWaypoints.contains(waypoint)) {
            selectedWaypoints.remove(waypoint);
        } else {
            selectedWaypoints.add(waypoint);
        }
        selectionAnchor = waypoint;
        selectedWaypoint = selectedWaypoints.isEmpty() ? null : waypoint;
        updateSelectionButtons();
    }

    protected void setSelectedWaypointRange(Collection<Waypoint> waypoints, boolean additive, Waypoint activeWaypoint) {
        if (!additive) {
            selectedWaypoints.clear();
        }
        selectedWaypoints.addAll(waypoints);
        selectedWaypoint = activeWaypoint;
        updateSelectionButtons();
    }

    protected boolean isWaypointSelected(Waypoint waypoint) {
        return selectedWaypoints.contains(waypoint);
    }

    private void updateSelectionButtons() {
        int selectionCount = selectedWaypoints.size();
        boolean isSomethingSelected = selectionCount > 0;
        boolean isSingleSelected = selectionCount == 1;

        buttonEdit.active = isSingleSelected;
        buttonDelete.active = isSomethingSelected;
        buttonDelete.setMessage(selectionCount > 1 ? Component.literal("Delete (" + selectionCount + ")") : Component.translatable("selectServer.delete"));
        buttonHighlight.active = isSingleSelected;
        buttonHighlight.setMessage(Component.translatable(isSingleSelected && selectedWaypoint == highlightedWaypoint ? "minimap.waypoints.removeHighlight" : "minimap.waypoints.highlight"));
        buttonShare.active = isSingleSelected;
        buttonTeleport.active = isSingleSelected && canTeleport();
    }

    protected void setHighlightedWaypoint() {
        waypointManager.setHighlightedWaypoint(selectedWaypoint, true);
        highlightedWaypoint = waypointManager.getHighlightedWaypoint();

        boolean isSomethingSelected = selectedWaypoint != null;
        buttonHighlight.setMessage(Component.translatable(isSomethingSelected && selectedWaypoint == highlightedWaypoint ? "minimap.waypoints.removeHighlight" : "minimap.waypoints.highlight"));
    }

    protected void editWaypoint(Waypoint waypoint) {
        editClicked = true;
        VoxelConstants.getMinecraft().setScreen(new GuiAddWaypoint(this, waypoint, true));
    }

    protected void addWaypoint() {
        addClicked = true;
        float r;
        float g;
        float b;
        if (waypointManager.getWaypoints().isEmpty()) {
            r = 0.0F;
            g = 1.0F;
            b = 0.0F;
        } else {
            r = generator.nextFloat();
            g = generator.nextFloat();
            b = generator.nextFloat();
        }

        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(VoxelConstants.getPlayer().level()));

        double dimensionScale = VoxelConstants.getPlayer().level().dimensionType().coordinateScale();
        int scaledX = (int) (GameVariableAccessShim.xCoord() * dimensionScale);
        int scaledZ = (int) (GameVariableAccessShim.zCoord() * dimensionScale);
        int scaledY = (int) (GameVariableAccessShim.yCoord() * 1.0F);
        newWaypoint = new Waypoint("", scaledX, scaledZ, scaledY, true, r, g, b, "", VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions);

        VoxelConstants.getMinecraft().setScreen(new GuiAddWaypoint(this, newWaypoint, false));
    }

    protected void toggleWaypointVisibility() {
        selectedWaypoint.enabled = !selectedWaypoint.enabled;
        waypointManager.saveWaypoints();
    }

    private void importWaypoints(boolean xaero) {
        WaypointManager.ImportResult result = xaero ? waypointManager.importXaeroWaypoints() : waypointManager.importWurstWaypoints();
        importStatus = Component.literal(result.message());
        clearWidgets();
        init();
    }

    @Override
    public void popupAction(Popup popup, int action) {
        switch (action) {
            case 10 -> deleteSelectedWaypoint();
            case 11 -> pendingDeleteWaypoints = List.of();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        tooltip = null;

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(getFont(), screenTitle, getWidth() / 2, 20, 0xFFFFFFFF);
        graphics.text(getFont(), I18n.get("minimap.waypoints.filter") + ":", getWidth() / 2 - 153, getHeight() - 73, 0xFFA0A0A0);
        if (importStatus != null) {
            graphics.centeredText(getFont(), importStatus, getWidth() / 2, getHeight() - 96, 0xFFE0E0E0);
        }

        if (tooltip != null) {
            renderTooltip(graphics, tooltip, mouseX, mouseY);
        }
    }

    private void createDeleteConfirmationPopup() {
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        String title = pendingDeleteWaypoints.size() > 1 ? "Confirm Delete " + pendingDeleteWaypoints.size() + " Waypoints?" : "Confirm Delete?";
        entries.add(new Popup.PopupEntry(title, -1, false, false));
        entries.add(new Popup.PopupEntry(I18n.get("selectServer.deleteButton"), 10, true, true));
        entries.add(new Popup.PopupEntry(I18n.get("gui.cancel"), 11, true, true));
        createPopup(getWidth() / 2 - 45, getHeight() / 2 - 20, buttonDelete.getX(), buttonDelete.getY(), 100, entries);
    }

    private void deleteSelectedWaypoint() {
        List<Waypoint> waypointsToDelete = pendingDeleteWaypoints.isEmpty() ? new ArrayList<>(selectedWaypoints) : new ArrayList<>(pendingDeleteWaypoints);
        pendingDeleteWaypoints = List.of();
        if (waypointsToDelete.isEmpty()) {
            return;
        }

        waypointManager.deleteWaypoints(waypointsToDelete);
        for (Waypoint waypoint : waypointsToDelete) {
            waypointList.removeWaypoint(waypoint);
        }
        setSelectedWaypoint(null);
    }

    protected void setTooltip(Component tooltip) {
        this.tooltip = tooltip;
    }

    public boolean canTeleport() {
        Optional<IntegratedServer> integratedServer = VoxelConstants.getIntegratedServer();

        if (integratedServer.isEmpty()) {
            return true;
        }

        try {
            return integratedServer.get().getPlayerList().isOp(VoxelConstants.getPlayer().nameAndId());
        } catch (RuntimeException exception) {
            return integratedServer.get().getWorldData().isAllowCommands();
        }
    }

    @Override
    public void removed() {
        if (changedSort) {
            super.removed();
        }
    }
}
