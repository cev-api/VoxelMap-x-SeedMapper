package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.gui.GuiAddWaypoint;
import com.mamiyaotaru.voxelmap.gui.GuiMinimapOptions;
import com.mamiyaotaru.voxelmap.gui.GuiSubworldsSelect;
import com.mamiyaotaru.voxelmap.gui.GuiWaypoints;
import com.mamiyaotaru.voxelmap.gui.IGuiWaypoints;
import com.mamiyaotaru.voxelmap.gui.overridden.Popup;
import com.mamiyaotaru.voxelmap.gui.overridden.PopupGuiButton;
import com.mamiyaotaru.voxelmap.gui.overridden.PopupGuiScreen;
import com.mamiyaotaru.voxelmap.interfaces.AbstractMapData;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperChestLootData;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperChestLootWidget;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperLocatorService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperLootService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperMarker;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCompat;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperImportedDatapackManager;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.textures.TextureAtlas;
import com.mamiyaotaru.voxelmap.util.BackgroundImageInfo;
import com.mamiyaotaru.voxelmap.util.BiomeMapData;
import com.mamiyaotaru.voxelmap.util.CommandUtils;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.EasingUtils;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.ImageUtils;
import com.mamiyaotaru.voxelmap.util.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

public class GuiPersistentMap extends PopupGuiScreen implements IGuiWaypoints {
    private final Random generator = new Random();
    private final PersistentMap persistentMap;
    private final WaypointManager waypointManager;
    private final MapSettingsManager mapOptions;
    private final PersistentMapSettingsManager options;
    private final SeedMapperSettingsManager seedMapperOptions;
    protected String screenTitle = "World Map";
    protected String worldNameDisplay = "";
    protected int worldNameDisplayLength;
    protected int maxWorldNameDisplayLength;
    private String subworldName = "";
    private PopupGuiButton buttonMultiworld;
    private int top;
    private int bottom;
    private boolean oldNorth;
    private boolean lastStill;
    private boolean editingCoordinates;
    private boolean lastEditingCoordinates;
    private EditBox coordinates;
    int centerX;
    int centerY;
    float mapCenterX;
    float mapCenterZ;
    float deltaX;
    float deltaY;
    float deltaXonRelease;
    float deltaYonRelease;
    long timeOfRelease;
    boolean mouseCursorShown = true;
    long timeAtLastTick;
    long timeOfLastKBInput;
    long timeOfLastMouseInput;
    float lastMouseX;
    float lastMouseY;
    protected int mouseX;
    protected int mouseY;
    boolean leftMouseButtonDown;
    float zoom;
    float zoomStart;
    float zoomGoal;
    long timeOfZoom;
    float zoomDirectX;
    float zoomDirectY;
    private float scScale = 1.0F;
    private float guiToMap = 2.0F;
    private float mapToGui = 0.5F;
    private float mouseDirectToMap = 1.0F;
    private float guiToDirectMouse = 2.0F;
    private static boolean gotSkin;
    private boolean closed;
    private CachedRegion[] regions = new CachedRegion[0];
    BackgroundImageInfo backGroundImageInfo;
    private final BiomeMapData biomeMapData = new BiomeMapData(760, 360);
    private float mapPixelsX;
    private float mapPixelsY;
    private final Object closedLock = new Object();
    private Component multiworldButtonName;
    private MutableComponent multiworldButtonNameRed;
    int sideMargin = 10;
    int buttonCount = 5;
    int buttonSeparation = 4;
    int buttonWidth = 66;
    public boolean editClicked;
    public boolean deleteClicked;
    public boolean addClicked;
    Waypoint newWaypoint;
    Waypoint selectedWaypoint;
    Waypoint pendingDeleteWaypoint;
    Waypoint hoverdWaypoint;
    SeedMapperMarker selectedSeedMapperMarker;
    String selectedSeedMapperWorldKey;
    Waypoint selectedSeedMapperWaypoint;
    Waypoint selectedSeedMapperAssociatedWaypoint;
    private PopupGuiButton buttonWaypoints;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Identifier voxelmapSkinLocation = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "persistentmap/playerskin");
    private final Identifier crosshairResource = Identifier.parse("textures/gui/sprites/hud/crosshair.png");
    private final Identifier seedMapperDirectionArrowResource = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/seedmapper/arrow.png");
    private final List<FeatureIconHitbox> seedMapperIconHitboxes = new ArrayList<>();
    private final List<SeedMapperMarkerHitbox> seedMapperMarkerHitboxes = new ArrayList<>();
    private Set<SeedMapperFeature> seedMapperSavedToggles;
    private SeedMapperFeature seedMapperIsolatedFeature;
    private int seedMapperIsolationBaseHash;
    private int seedMapperLegendPage = 0;
    private int seedMapperLegendMaxPage = 0;
    private int legendPrevX;
    private int legendPrevY;
    private int legendNextX;
    private int legendNextY;
    private int legendArrowSize;
    private long seedMapperLastMarkerQueryMs = 0L;
    private SeedMapperQueryCacheKey seedMapperLastMarkerQueryKey;
    private List<SeedMapperMarker> seedMapperLastMarkerResult = List.of();
    private SeedMapperChestLootWidget seedMapperChestLootWidget;
    private boolean currentDragging;
    private boolean keySprintPressed;
    private boolean keyUpPressed;
    private boolean keyDownPressed;
    private boolean keyLeftPressed;
    private boolean keyRightPressed;
    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 16;
    private static final int COMPLETED_TICK_COLOR = 0xFF22C84A;
    private static final int COMPLETED_TICK_OUTLINE_COLOR = 0xFF000000;

    public GuiPersistentMap(Screen parent) {
        this.lastScreen = parent;

        this.waypointManager = VoxelConstants.getVoxelMapInstance().getWaypointManager();
        mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
        this.persistentMap = VoxelConstants.getVoxelMapInstance().getPersistentMap();
        this.options = VoxelConstants.getVoxelMapInstance().getPersistentMapOptions();
        this.seedMapperOptions = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        this.zoom = this.options.zoom;
        this.zoomStart = this.options.zoom;
        this.zoomGoal = this.options.zoom;
        this.persistentMap.setLightMapArray(VoxelConstants.getVoxelMapInstance().getMap().getLightmapArray());
        if (!gotSkin) {
            this.getSkin();
        }

    }

    private void getSkin() {
        BufferedImage skinImage = ImageUtils.createBufferedImageFromIdentifier(VoxelConstants.getPlayer().getSkin().body().texturePath());

        if (skinImage == null) {
            if (VoxelConstants.DEBUG) {
                VoxelConstants.getLogger().warn("Got no player skin!");
            }
            return;
        }

        gotSkin = true;

        boolean showHat = VoxelConstants.getPlayer().isModelPartShown(PlayerModelPart.HAT);
        if (showHat) {
            skinImage = ImageUtils.addImages(ImageUtils.loadImage(skinImage, 8, 8, 8, 8), ImageUtils.loadImage(skinImage, 40, 8, 8, 8), 0.0F, 0.0F, 8, 8);
        } else {
            skinImage = ImageUtils.loadImage(skinImage, 8, 8, 8, 8);
        }

        float scale = skinImage.getWidth() / 8.0F;
        skinImage = ImageUtils.fillOutline(ImageUtils.pad(ImageUtils.scaleImage(skinImage, 2.0F / scale)), true, 1);

        DynamicTexture texture = new DynamicTexture(() -> "Voxelmap player", ImageUtils.nativeImageFromBufferedImage(skinImage));
        texture.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        minecraft.getTextureManager().register(voxelmapSkinLocation, texture);
    }

    @Override
    public void init() {
        this.oldNorth = mapOptions.oldNorth;
        this.centerAt(this.options.mapX, this.options.mapZ);
        if (minecraft.screen == this) {
            this.closed = false;
        }

        this.screenTitle = I18n.get("worldmap.title");
        this.buildWorldName();
        this.leftMouseButtonDown = false;
        this.sideMargin = 10;
        this.buttonCount = 5;
        this.buttonSeparation = 4;
        this.buttonWidth = (this.width - this.sideMargin * 2 - this.buttonSeparation * (this.buttonCount - 1)) / this.buttonCount;
        this.buttonWaypoints = new PopupGuiButton(this.sideMargin, this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("options.minimap.waypoints"), button -> minecraft.setScreen(new GuiWaypoints(this)), this);
        this.addRenderableWidget(this.buttonWaypoints);
        this.multiworldButtonName = Component.translatable(VoxelConstants.isRealmServer() ? "menu.online" : "options.worldmap.multiworld");
        this.multiworldButtonNameRed = (Component.translatable(VoxelConstants.isRealmServer() ? "menu.online" : "options.worldmap.multiworld")).withStyle(ChatFormatting.RED);
        if (!minecraft.hasSingleplayerServer() && !VoxelConstants.getVoxelMapInstance().getWaypointManager().receivedAutoSubworldName()) {
            this.addRenderableWidget(this.buttonMultiworld = new PopupGuiButton(this.sideMargin + (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, this.multiworldButtonName, button -> minecraft.setScreen(new GuiSubworldsSelect(this)), this));
        }

        this.addRenderableWidget(new PopupGuiButton(this.sideMargin + 3 * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("menu.options"), button -> minecraft.setScreen(new GuiMinimapOptions(this)), this));
        this.addRenderableWidget(new PopupGuiButton(this.sideMargin + 4 * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("gui.done"), button -> this.onClose(), this));
        this.coordinates = new EditBox(this.getFont(), this.sideMargin, 10, 140, 20, Component.empty());
        this.top = 32;
        this.bottom = this.getHeight() - 32;
        this.centerX = this.getWidth() / 2;
        this.centerY = (this.bottom - this.top) / 2;
        this.scScale = (float) minecraft.getWindow().getGuiScale();
        this.mapPixelsX = minecraft.getWindow().getWidth();
        this.mapPixelsY = (minecraft.getWindow().getHeight() - (int) (64.0F * this.scScale));
        this.lastStill = false;
        this.timeAtLastTick = System.currentTimeMillis();
    }

    @Override
    public void added() {
        currentDragging = false;
        super.added();
    }

    private void centerAt(int x, int z) {
        if (this.oldNorth) {
            this.mapCenterX = (-z);
            this.mapCenterZ = x;
        } else {
            this.mapCenterX = x;
            this.mapCenterZ = z;
        }

    }

    private void buildWorldName() {
        final AtomicReference<String> worldName = new AtomicReference<>();

        VoxelConstants.getIntegratedServer().ifPresentOrElse(integratedServer -> {
            worldName.set(integratedServer.getWorldData().getLevelName());

            if (worldName.get() == null || worldName.get().isBlank()) {
                worldName.set("Singleplayer World");
            }
        }, () -> {
            ServerData info = minecraft.getCurrentServer();

            if (info != null) {
                worldName.set(info.name);
            }
            if (worldName.get() == null || worldName.get().isBlank()) {
                worldName.set("Multiplayer Server");
            }
            if (VoxelConstants.isRealmServer()) {
                worldName.set("Realms");
            }
        });

        StringBuilder worldNameBuilder = (new StringBuilder("§r")).append(worldName.get());
        String subworldName = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true);
        this.subworldName = subworldName;
        if ((subworldName == null || subworldName.isEmpty()) && VoxelConstants.getVoxelMapInstance().getWaypointManager().isMultiworld()) {
            subworldName = "???";
        }

        if (subworldName != null && !subworldName.isEmpty()) {
            worldNameBuilder.append(" - ").append(subworldName);
        }

        this.worldNameDisplay = worldNameBuilder.toString();
        this.worldNameDisplayLength = this.getFont().width(this.worldNameDisplay);

        for (this.maxWorldNameDisplayLength = this.getWidth() / 2 - this.getFont().width(this.screenTitle) / 2 - this.sideMargin * 2; this.worldNameDisplayLength > this.maxWorldNameDisplayLength
                && worldName.get().length() > 5; this.worldNameDisplayLength = this.getFont().width(this.worldNameDisplay)) {
            worldName.set(worldName.get().substring(0, worldName.get().length() - 1));
            worldNameBuilder = new StringBuilder(worldName.get());
            worldNameBuilder.append("...");
            if (subworldName != null && !subworldName.isEmpty()) {
                worldNameBuilder.append(" - ").append(subworldName);
            }

            this.worldNameDisplay = worldNameBuilder.toString();
        }

        if (subworldName != null && !subworldName.isEmpty()) {
            while (this.worldNameDisplayLength > this.maxWorldNameDisplayLength && subworldName.length() > 5) {
                worldNameBuilder = new StringBuilder(worldName.get());
                worldNameBuilder.append("...");
                subworldName = subworldName.substring(0, subworldName.length() - 1);
                worldNameBuilder.append(" - ").append(subworldName);
                this.worldNameDisplay = worldNameBuilder.toString();
                this.worldNameDisplayLength = this.getFont().width(this.worldNameDisplay);
            }
        }

    }

    private float bindZoom(float zoom) {
        zoom = Math.max(this.options.minZoom, zoom);
        return Math.min(this.options.maxZoom, zoom);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        this.timeOfLastMouseInput = System.currentTimeMillis();
        this.switchToMouseInput();
        float mouseDirectX = (float) minecraft.mouseHandler.xpos();
        float mouseDirectY = (float) minecraft.mouseHandler.ypos();
        if (amount != 0.0) {
            if (amount > 0.0) {
                this.zoomGoal *= 1.26F;
            } else if (amount < 0.0) {
                this.zoomGoal /= 1.26F;
            }

            this.zoomStart = this.zoom;
            this.zoomGoal = this.bindZoom(this.zoomGoal);
            this.timeOfZoom = System.currentTimeMillis();
            this.zoomDirectX = mouseDirectX;
            this.zoomDirectY = mouseDirectY;
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        currentDragging = false;

        if (mouseButtonEvent.button() == 1 && handleSeedMapperMarkerRightClick((int) mouseButtonEvent.x(), (int) mouseButtonEvent.y())) {
            return true;
        }

        selectedWaypoint = getHoveredWaypoint();
        if (mouseButtonEvent.button() == 1 && (selectedWaypoint != null || (mouseY > this.top && mouseY < this.bottom))) {
            this.timeOfLastKBInput = 0L;
            int mouseDirectX = (int) minecraft.mouseHandler.xpos();
            int mouseDirectY = (int) minecraft.mouseHandler.ypos();
            if (mapOptions.worldmapAllowed) {
                this.createPopup((int) mouseButtonEvent.x(), (int) mouseButtonEvent.y(), mouseDirectX, mouseDirectY);
            }
        }

        return super.mouseReleased(mouseButtonEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (seedMapperChestLootWidget != null) {
            if (seedMapperChestLootWidget.mouseClicked(mouseButtonEvent, doubleClick)) {
                return true;
            }
            if (seedMapperChestLootWidget.isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                return true;
            }
            if (mouseButtonEvent.button() == 0) {
                seedMapperChestLootWidget = null;
            }
        }

        if (mouseButtonEvent.button() == 0 && handleSeedMapperIconClick((int) mouseButtonEvent.x(), (int) mouseButtonEvent.y())) {
            return true;
        }
        if (mouseButtonEvent.button() == 0 && handleSeedMapperMarkerLeftClick((int) mouseButtonEvent.x(), (int) mouseButtonEvent.y())) {
            return true;
        }
        if (this.popupOpen()) {
            this.coordinates.mouseClicked(mouseButtonEvent, doubleClick);
            this.editingCoordinates = this.coordinates.isFocused();
            if (this.editingCoordinates && !this.lastEditingCoordinates) {
                int x;
                int z;
                if (this.oldNorth) {
                    x = (int) Math.floor(this.mapCenterZ);
                    z = -((int) Math.floor(this.mapCenterX));
                } else {
                    x = (int) Math.floor(this.mapCenterX);
                    z = (int) Math.floor(this.mapCenterZ);
                }

                this.coordinates.setValue(x + ", " + z);
                this.coordinates.setTextColor(0xFFFFFF);
            }

            this.lastEditingCoordinates = this.editingCoordinates;
        }
        if (mouseButtonEvent.button() == 0) {
            currentDragging = true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick) || mouseButtonEvent.button() == 1;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!this.editingCoordinates && minecraft.options.keyJump.matches(keyEvent)) {
            if (minecraft.options.keyJump.matches(keyEvent)) {
                this.zoomGoal /= 1.26F;
            }

            this.zoomStart = this.zoom;
            this.zoomGoal = this.bindZoom(this.zoomGoal);
            this.timeOfZoom = System.currentTimeMillis();
            this.zoomDirectX = (minecraft.getWindow().getWidth() / 2f);
            this.zoomDirectY = (minecraft.getWindow().getHeight() - minecraft.getWindow().getHeight() / 2f);
            this.switchToKeyboardInput();
        }

        this.clearPopups();
        if (this.editingCoordinates) {
            this.coordinates.keyPressed(keyEvent);
            boolean isGood = this.isAcceptable(this.coordinates.getValue());
            this.coordinates.setTextColor(isGood ? 0xFFFFFF : 0xFF0000);
            if ((keyEvent.key() == 257 || keyEvent.key() == 335) && this.coordinates.isFocused() && isGood) {
                String[] xz = this.coordinates.getValue().split(",");
                this.centerAt(Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim()));
                this.editingCoordinates = false;
                this.lastEditingCoordinates = false;
                this.switchToKeyboardInput();
            }

            if (keyEvent.key() == 258 && this.coordinates.isFocused()) {
                this.editingCoordinates = false;
                this.lastEditingCoordinates = false;
                this.switchToKeyboardInput();
            }
        }

        if (VoxelConstants.getVoxelMapInstance().getMapOptions().keyBindMenu.matches(keyEvent)) {
            keyEvent = new KeyEvent(GLFW.GLFW_KEY_ESCAPE, -1, -1);
        }

        keySprintPressed = minecraft.options.keySprint.matches(keyEvent) || keySprintPressed;
        keyUpPressed = minecraft.options.keyUp.matches(keyEvent) || keyUpPressed;
        keyDownPressed = minecraft.options.keyDown.matches(keyEvent) || keyDownPressed;
        keyLeftPressed = minecraft.options.keyLeft.matches(keyEvent) || keyLeftPressed;
        keyRightPressed = minecraft.options.keyRight.matches(keyEvent) || keyRightPressed;

        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        keySprintPressed = !minecraft.options.keySprint.matches(keyEvent) && keySprintPressed;
        keyUpPressed = !minecraft.options.keyUp.matches(keyEvent) && keyUpPressed;
        keyDownPressed = !minecraft.options.keyDown.matches(keyEvent) && keyDownPressed;
        keyLeftPressed = !minecraft.options.keyLeft.matches(keyEvent) && keyLeftPressed;
        keyRightPressed = !minecraft.options.keyRight.matches(keyEvent) && keyRightPressed;

        return super.keyReleased(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        this.clearPopups();
        if (this.editingCoordinates) {
            this.coordinates.charTyped(characterEvent);
            boolean isGood = this.isAcceptable(this.coordinates.getValue());
            this.coordinates.setTextColor(isGood ? 0xFFFFFF : 0xFF0000);
            if (characterEvent.codepoint() == '\r' && this.coordinates.isFocused() && isGood) {
                String[] xz = this.coordinates.getValue().split(",");
                this.centerAt(Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim()));
                this.editingCoordinates = false;
                this.lastEditingCoordinates = false;
                this.switchToKeyboardInput();
            }
        }

        return super.charTyped(characterEvent);
    }

    private boolean isAcceptable(String input) {
        try {
            String[] xz = this.coordinates.getValue().split(",");
            Integer.valueOf(xz[0].trim());
            Integer.valueOf(xz[1].trim());
            return true;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException var3) {
            return false;
        }
    }

    private void switchToMouseInput() {
        this.timeOfLastKBInput = 0L;
        if (!this.mouseCursorShown) {
            GLFW.glfwSetInputMode(minecraft.getWindow().handle(), 208897, 212993);
        }

        this.mouseCursorShown = true;
    }

    private void switchToKeyboardInput() {
        this.timeOfLastKBInput = System.currentTimeMillis();
        this.mouseCursorShown = false;
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), 208897, 212995);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();
        this.buttonWaypoints.active = mapOptions.waypointsAllowed;
        this.zoomGoal = this.bindZoom(this.zoomGoal);
        if (this.mouseX != mouseX || this.mouseY != mouseY) {
            this.timeOfLastMouseInput = System.currentTimeMillis();
            this.switchToMouseInput();
        }

        this.mouseX = mouseX;
        this.mouseY = mouseY;
        float mouseDirectX = (float) minecraft.mouseHandler.xpos();
        float mouseDirectY = (float) minecraft.mouseHandler.ypos();
        if (this.zoom != this.zoomGoal) {
            float previousZoom = this.zoom;
            long timeSinceZoom = System.currentTimeMillis() - this.timeOfZoom;
            if (timeSinceZoom < 700.0F) {
                this.zoom = EasingUtils.easeOutExpo(this.zoomStart, this.zoomGoal, timeSinceZoom, 700.0F);
            } else {
                this.zoom = this.zoomGoal;
            }

            float scaledZoom = this.zoom;
            if (minecraft.getWindow().getWidth() > 1600) {
                scaledZoom = this.zoom * minecraft.getWindow().getWidth() / 1600.0F;
            }

            float zoomDelta = this.zoom / previousZoom;
            float zoomOffsetX = this.centerX * this.guiToDirectMouse - this.zoomDirectX;
            float zoomOffsetY = (this.top + this.centerY) * this.guiToDirectMouse - this.zoomDirectY;
            float zoomDeltaX = zoomOffsetX - zoomOffsetX * zoomDelta;
            float zoomDeltaY = zoomOffsetY - zoomOffsetY * zoomDelta;
            this.mapCenterX += zoomDeltaX / scaledZoom;
            this.mapCenterZ += zoomDeltaY / scaledZoom;
        }

        this.options.zoom = this.zoomGoal;
        float scaledZoom = this.zoom;
        if (minecraft.getWindow().getScreenWidth() > 1600) {
            scaledZoom = this.zoom * minecraft.getWindow().getScreenWidth() / 1600.0F;
        }

        this.guiToMap = this.scScale / scaledZoom;
        this.mapToGui = 1.0F / this.scScale * scaledZoom;
        this.mouseDirectToMap = 1.0F / scaledZoom;
        this.guiToDirectMouse = this.scScale;
        this.renderBackground(graphics);
        if (currentDragging) {
            if (!this.leftMouseButtonDown && this.overPopup(mouseX, mouseY)) {
                this.deltaX = 0.0F;
                this.deltaY = 0.0F;
                this.lastMouseX = mouseDirectX;
                this.lastMouseY = mouseDirectY;
                this.leftMouseButtonDown = true;
            } else if (this.leftMouseButtonDown) {
                this.deltaX = (this.lastMouseX - mouseDirectX) * this.mouseDirectToMap;
                this.deltaY = (this.lastMouseY - mouseDirectY) * this.mouseDirectToMap;
                this.lastMouseX = mouseDirectX;
                this.lastMouseY = mouseDirectY;
                this.deltaXonRelease = this.deltaX;
                this.deltaYonRelease = this.deltaY;
                this.timeOfRelease = System.currentTimeMillis();
            }
        } else {
            long timeSinceRelease = System.currentTimeMillis() - this.timeOfRelease;
            if (timeSinceRelease < 700.0F) {
                this.deltaX = EasingUtils.easeOutExpo(this.deltaXonRelease, 0.0F, timeSinceRelease, 700.0F);
                this.deltaY = EasingUtils.easeOutExpo(this.deltaYonRelease, 0.0F, timeSinceRelease, 700.0F);
            } else {
                this.deltaX = 0.0F;
                this.deltaY = 0.0F;
                this.deltaXonRelease = 0.0F;
                this.deltaYonRelease = 0.0F;
            }

            this.leftMouseButtonDown = false;
        }

        long timeSinceLastTick = System.currentTimeMillis() - this.timeAtLastTick;
        this.timeAtLastTick = System.currentTimeMillis();
        if (!this.editingCoordinates) {
            int kbDelta = 5;
            if (keySprintPressed) {
                kbDelta = 10;
            }

            if (keyUpPressed) {
                this.deltaY -= kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }

            if (keyDownPressed) {
                this.deltaY += kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }

            if (keyLeftPressed) {
                this.deltaX -= kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }

            if (keyRightPressed) {
                this.deltaX += kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }
        }

        this.mapCenterX += this.deltaX;
        this.mapCenterZ += this.deltaY;
        if (this.oldNorth) {
            this.options.mapX = (int) this.mapCenterZ;
            this.options.mapZ = -((int) this.mapCenterX);
        } else {
            this.options.mapX = (int) this.mapCenterX;
            this.options.mapZ = (int) this.mapCenterZ;
        }

        this.centerX = this.getWidth() / 2;
        this.centerY = (this.bottom - this.top) / 2;
        int left;
        int right;
        int top;
        int bottom;
        if (this.oldNorth) {
            left = (int) Math.floor((this.mapCenterZ - this.centerY * this.guiToMap) / 256.0F);
            right = (int) Math.floor((this.mapCenterZ + this.centerY * this.guiToMap) / 256.0F);
            top = (int) Math.floor((-this.mapCenterX - this.centerX * this.guiToMap) / 256.0F);
            bottom = (int) Math.floor((-this.mapCenterX + this.centerX * this.guiToMap) / 256.0F);
        } else {
            left = (int) Math.floor((this.mapCenterX - this.centerX * this.guiToMap) / 256.0F);
            right = (int) Math.floor((this.mapCenterX + this.centerX * this.guiToMap) / 256.0F);
            top = (int) Math.floor((this.mapCenterZ - this.centerY * this.guiToMap) / 256.0F);
            bottom = (int) Math.floor((this.mapCenterZ + this.centerY * this.guiToMap) / 256.0F);
        }

        synchronized (this.closedLock) {
            if (this.closed) {
                return;
            }

            this.regions = this.persistentMap.getRegions(left - 1, right + 1, top - 1, bottom + 1);
        }

        this.backGroundImageInfo = this.waypointManager.getBackgroundImageInfo();
        if (this.backGroundImageInfo != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, backGroundImageInfo.getImageLocation(), backGroundImageInfo.left, backGroundImageInfo.top + 32, 0, 0, backGroundImageInfo.width, backGroundImageInfo.height, backGroundImageInfo.width, backGroundImageInfo.height);
        }

        graphics.pose().translate(this.centerX - this.mapCenterX * this.mapToGui, (this.top + this.centerY) - this.mapCenterZ * this.mapToGui);
        if (this.oldNorth) {
            graphics.pose().rotate(90.0F * Mth.DEG_TO_RAD);
        }

        float cursorCoordZ = 0.0f;
        float cursorCoordX = 0.0f;
        graphics.pose().scale(this.mapToGui, this.mapToGui);
        if (mapOptions.worldmapAllowed) {
            for (CachedRegion region : this.regions) {
                Identifier resource = region.getTextureLocation(this.zoom);
                if (resource != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, resource, region.getX() * 256, region.getZ() * 256, 0, 0, region.getWidth(), region.getWidth(), region.getWidth(), region.getWidth());
                }
            }

            if (mapOptions.worldBorder) {
                WorldBorder worldBorder = minecraft.level.getWorldBorder();
                float scale = 1.0f / (float) minecraft.getWindow().getGuiScale() / mapToGui;

                float x1 = (float) (worldBorder.getMinX());
                float z1 = (float) (worldBorder.getMinZ());
                float x2 = (float) (worldBorder.getMaxX());
                float z2 = (float) (worldBorder.getMaxZ());

                VoxelMapGuiGraphics.fillGradient(graphics, x1 - scale, z1 - scale, x2 + scale, z1 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);
                VoxelMapGuiGraphics.fillGradient(graphics, x1 - scale, z2 - scale, x2 + scale, z2 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);

                VoxelMapGuiGraphics.fillGradient(graphics, x1 - scale, z1 - scale, x1 + scale, z2 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);
                VoxelMapGuiGraphics.fillGradient(graphics, x2 - scale, z1 - scale, x2 + scale, z2 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);
            }

            float cursorX;
            float cursorY;
            if (this.mouseCursorShown) {
                cursorX = mouseDirectX;
                cursorY = mouseDirectY - this.top * this.guiToDirectMouse;
            } else {
                cursorX = (minecraft.getWindow().getWidth() / 2f);
                cursorY = (minecraft.getWindow().getHeight() - minecraft.getWindow().getHeight() / 2f) - this.top * this.guiToDirectMouse;
            }

            if (this.oldNorth) {
                cursorCoordX = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
                cursorCoordZ = -(cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
            } else {
                cursorCoordX = cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
                cursorCoordZ = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
            }

            if (this.oldNorth) {
                graphics.pose().rotate(-90.0F * Mth.DEG_TO_RAD);
            }

            graphics.pose().scale(this.guiToMap, this.guiToMap);
            graphics.pose().translate(-(this.centerX - this.mapCenterX * this.mapToGui), -((this.top + this.centerY) - this.mapCenterZ * this.mapToGui));
            if (mapOptions.biomeOverlay != 0) {
                float biomeScaleX = this.mapPixelsX / 760.0F;
                float biomeScaleY = this.mapPixelsY / 360.0F;
                boolean still = !this.leftMouseButtonDown;
                still = still && this.zoom == this.zoomGoal;
                still = still && this.deltaX == 0.0F && this.deltaY == 0.0F;
                still = still && ThreadManager.executorService.getActiveCount() == 0;
                if (still && !this.lastStill) {
                    int column;
                    if (this.oldNorth) {
                        column = (int) Math.floor(Math.floor(this.mapCenterZ - this.centerY * this.guiToMap) / 256.0) - (left - 1);
                    } else {
                        column = (int) Math.floor(Math.floor(this.mapCenterX - this.centerX * this.guiToMap) / 256.0) - (left - 1);
                    }

                    for (int x = 0; x < this.biomeMapData.getWidth(); ++x) {
                        for (int z = 0; z < this.biomeMapData.getHeight(); ++z) {
                            float floatMapX;
                            float floatMapZ;
                            if (this.oldNorth) {
                                floatMapX = z * biomeScaleY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
                                floatMapZ = -(x * biomeScaleX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
                            } else {
                                floatMapX = x * biomeScaleX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
                                floatMapZ = z * biomeScaleY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
                            }

                            int mapX = (int) Math.floor(floatMapX);
                            int mapZ = (int) Math.floor(floatMapZ);
                            int regionX = (int) Math.floor(mapX / 256.0F) - (left - 1);
                            int regionZ = (int) Math.floor(mapZ / 256.0F) - (top - 1);
                            if (!this.oldNorth && regionX != column || this.oldNorth && regionZ != column) {
                                this.persistentMap.compress();
                            }

                            column = !this.oldNorth ? regionX : regionZ;
                            CachedRegion region = this.regions[regionZ * (right + 1 - (left - 1) + 1) + regionX];
                            Biome biome = null;
                            if (region.getMapData() != null && region.isLoaded() && !region.isEmpty()) {
                                int inRegionX = mapX - region.getX() * region.getWidth();
                                int inRegionZ = mapZ - region.getZ() * region.getWidth();
                                int height = region.getMapData().getHeight(inRegionX, inRegionZ);
                                int light = region.getMapData().getLight(inRegionX, inRegionZ);
                                if (height != Short.MIN_VALUE || light != 0) {
                                    biome = region.getMapData().getBiome(inRegionX, inRegionZ);
                                }
                            }

                            this.biomeMapData.setBiome(x, z, biome);
                        }
                    }

                    this.persistentMap.compress();
                    this.biomeMapData.segmentBiomes();
                    this.biomeMapData.findCenterOfSegments(true);
                }

                this.lastStill = still;
                boolean displayStill = !this.leftMouseButtonDown;
                displayStill = displayStill && this.zoom == this.zoomGoal;
                displayStill = displayStill && this.deltaX == 0.0F && this.deltaY == 0.0F;
                if (displayStill) {
                    int minimumSize = (int) (20.0F * this.scScale / biomeScaleX);
                    minimumSize *= minimumSize;
                    ArrayList<AbstractMapData.BiomeLabel> labels = this.biomeMapData.getBiomeLabels();
                    for (AbstractMapData.BiomeLabel biomeLabel : labels) {
                        if (biomeLabel.segmentSize > minimumSize) {
                            String label = biomeLabel.name; // + " (" + biomeLabel.x + "," + biomeLabel.z + ")";
                            float x = biomeLabel.x * biomeScaleX / this.scScale;
                            float z = biomeLabel.z * biomeScaleY / this.scScale;

                            this.writeCentered(graphics, label, x, this.top + z - 3.0F, 0xFFFFFFFF, true);
                        }
                    }
                }
            }
        }
        graphics.pose().popMatrix();

        if (options.showDistantWaypoints) {
            this.overlayBackground(graphics, 0, this.top, 255, 255);
            this.overlayBackground(graphics, this.bottom, this.getHeight(), 255, 255);
        }

        drawSeedMapperMarkers(graphics, mouseX, mouseY);

        Waypoint currentlyHovered = null;
        if (mapOptions.waypointsAllowed && options.showWaypoints) {
            TextureAtlas textureAtlas = VoxelConstants.getVoxelMapInstance().getWaypointManager().getTextureAtlas();

            for (Waypoint waypoint : waypointManager.getWaypoints()) {
                if (!waypoint.inWorld || !waypoint.inDimension) continue;
                if (hasVisibleSeedMapperMarkerAt(waypoint.getX(), waypoint.getZ())) continue;

                boolean isHighlighted = waypointManager.isHighlightedWaypoint(waypoint);
                boolean isHovered = drawWaypoint(graphics, waypoint, textureAtlas, null, isHighlighted, -1, mouseX, mouseY);
                if (isHovered) {
                    currentlyHovered = waypoint;
                }
            }

            Waypoint highlightedPoint = waypointManager.getHighlightedWaypoint();
            if (highlightedPoint != null) {
                boolean isHovered = drawWaypoint(graphics, highlightedPoint, textureAtlas, textureAtlas.getAtlasSprite("marker/target"), true, 0xFFFF0000, mouseX, mouseY);
                if (isHovered) {
                    currentlyHovered = highlightedPoint;
                }
            }
        }
        hoverdWaypoint = currentlyHovered;

        if (!options.showDistantWaypoints) {
            this.overlayBackground(graphics, 0, this.top, 255, 255);
            this.overlayBackground(graphics, this.bottom, this.getHeight(), 255, 255);
        }

        if (gotSkin) {
            float playerX = (float) GameVariableAccessShim.xCoordDouble();
            float playerZ = (float) GameVariableAccessShim.zCoordDouble();
            drawPlayer(graphics, voxelmapSkinLocation, playerX, playerZ, mouseX, mouseY);
        }

        if (System.currentTimeMillis() - this.timeOfLastKBInput < 2000L) {
            int scWidth = minecraft.getWindow().getGuiScaledWidth();
            int scHeight = minecraft.getWindow().getGuiScaledHeight();
            graphics.blit(RenderPipelines.CROSSHAIR, crosshairResource, scWidth / 2 - 8, scHeight / 2 - 8, 0, 0, 15, 15, 15, 15);
        } else {
            this.switchToMouseInput();
        }

        if (mapOptions.worldmapAllowed) {
            graphics.centeredText(this.getFont(), this.screenTitle, this.getWidth() / 2, 16, 0xFFFFFFFF);
            drawSeedMapperFeatureStrip(graphics, mouseX, mouseY);
            int x = (int) Math.floor(cursorCoordX);
            int z = (int) Math.floor(cursorCoordZ);
            if (options.showCoordinates) {
                if (!this.editingCoordinates) {
                    graphics.text(this.getFont(), "X: " + x, this.sideMargin, 16, 0xFFFFFFFF);
                    graphics.text(this.getFont(), "Z: " + z, this.sideMargin + 64, 16, 0xFFFFFFFF);
                } else {
                    this.coordinates.extractRenderState(graphics, mouseX, mouseY, delta);
                }
            }
            String enteredSeed = seedMapperOptions.manualSeed == null ? "" : seedMapperOptions.manualSeed.trim();
            boolean showSeedHeader = !enteredSeed.isEmpty();
            if (showSeedHeader) {
                String seedText = "Seed: " + enteredSeed;
                int seedWidth = this.getFont().width(seedText);
                graphics.text(this.getFont(), seedText, this.getWidth() - this.sideMargin - seedWidth, 16, 0xFFFFFFFF);
            }

            if (this.subworldName != null && !this.subworldName.equals(VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true))
                    || VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true) != null && !VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true).equals(this.subworldName)) {
                this.buildWorldName();
            }

            int worldNameY = showSeedHeader ? 28 : 16;
            graphics.text(this.getFont(), this.worldNameDisplay, this.getWidth() - this.sideMargin - this.worldNameDisplayLength, worldNameY, 0xFFFFFF);
            if (this.buttonMultiworld != null) {
                if ((this.subworldName == null || this.subworldName.isEmpty()) && VoxelConstants.getVoxelMapInstance().getWaypointManager().isMultiworld()) {
                    if ((int) (System.currentTimeMillis() / 1000L % 2L) == 0) {
                        this.buttonMultiworld.setMessage(this.multiworldButtonNameRed);
                    } else {
                        this.buttonMultiworld.setMessage(this.multiworldButtonName);
                    }
                } else {
                    this.buttonMultiworld.setMessage(this.multiworldButtonName);
                }
            }
        } else {
            graphics.text(this.getFont(), Component.translatable("worldmap.disabled"), this.sideMargin, 16, 0xFFFFFFFF);
        }

        if (seedMapperChestLootWidget != null) {
            graphics.nextStratum();
            seedMapperChestLootWidget.extractRenderState(graphics, mouseX, mouseY, this.getFont());
            List<ClientTooltipComponent> tooltip = seedMapperChestLootWidget.getPendingItemTooltip();
            if (tooltip != null) {
                graphics.nextStratum();
                graphics.tooltip(this.getFont(), tooltip, seedMapperChestLootWidget.getPendingTooltipX(), seedMapperChestLootWidget.getPendingTooltipY(), DefaultTooltipPositioner.INSTANCE, null);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private boolean drawPlayer(GuiGraphicsExtractor graphics, Identifier skin, float playerX, float playerZ, int mouseX, int mouseY) {
        float headWidth = ICON_WIDTH * 0.75F;
        float headHeight = ICON_HEIGHT * 0.75F;

        int x = this.width / 2;
        int y = this.height / 2;
        int borderX = x - 4;
        int borderY = y - this.top;

        double wayX = this.mapCenterX - (this.oldNorth ? -playerZ : playerX);
        double wayY = this.mapCenterZ - (this.oldNorth ? playerX : playerZ);
        float locate = (float) Math.atan2(wayX, wayY);
        float hypot = (float) Math.sqrt(wayX * wayX + wayY * wayY) * mapToGui;

        double dispX = hypot * Math.sin(locate);
        double dispY = hypot * Math.cos(locate);
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
            hypot *= (float) Math.min(borderX / Math.abs(dispX), borderY / Math.abs(dispY));
        }

        graphics.pose().pushMatrix();

        if (far) {
            graphics.pose().translate(x, y);
            graphics.pose().rotate(-locate);
            graphics.pose().translate(0.0F, -hypot);
            graphics.pose().rotate(locate);
            graphics.pose().translate(-x, -y);
        } else {
            graphics.pose().rotate(-locate);
            graphics.pose().translate(0.0F, -hypot);
            graphics.pose().rotate(locate);
        }

        Vector2f guiVector = graphics.pose().transformPosition(new Vector2f(x, y));
        float screenX = guiVector.x();
        float screenY = guiVector.y();

        boolean isHovered = mouseX >= screenX - ICON_WIDTH / 2.0F && mouseX <= screenX + ICON_WIDTH / 2.0F
                && mouseY >= screenY - ICON_HEIGHT / 2.0F && mouseY <= screenY + ICON_HEIGHT / 2.0F;
        if (isHovered) {
            graphics.requestCursor(CursorTypes.CROSSHAIR);
            if (options.showCoordinates) {
                renderTooltip(graphics, Component.literal("X: " + GameVariableAccessShim.xCoord() + ", Y: " + GameVariableAccessShim.yCoord() + ", Z: " + GameVariableAccessShim.zCoord()), this.mouseX, this.mouseY);
            }
        }

        VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, skin, x - headWidth / 2.0F, y - headHeight / 2.0F, headWidth, headHeight, 0, 1, 0, 1, 0xFFFFFFFF);
        if (options.showPlayerDirectionArrow) {
            float arrowSize = 10.0F;
            float angle = (float) (Math.toRadians(GameVariableAccessShim.rotationYaw()) + Math.PI);
            if (this.oldNorth) {
                angle += (float) (Math.PI / 2.0D);
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().rotate(angle);
            VoxelMapGuiGraphics.blitFloat(
                    graphics,
                    RenderPipelines.GUI_TEXTURED,
                    seedMapperDirectionArrowResource,
                    -arrowSize / 2.0F,
                    -headHeight / 2.0F - arrowSize - 2.0F,
                    arrowSize,
                    arrowSize,
                    0, 1, 0, 1,
                    0xFFFFFFFF
            );
            graphics.pose().popMatrix();
        }

        graphics.pose().popMatrix();

        return isHovered;
    }

    private boolean drawWaypoint(GuiGraphicsExtractor graphics, Waypoint waypoint, TextureAtlas textureAtlas, Sprite icon, boolean isHighlighted, int color, int mouseX, int mouseY) {
        float ptX = waypoint.getX() + 0.5F;
        float ptZ = waypoint.getZ() + 0.5F;

        int x = this.width / 2;
        int y = this.height / 2;

        int borderOffsetX = options.showDistantWaypoints ? -4 : ICON_WIDTH / 2;
        int borderOffsetY = options.showDistantWaypoints ? 0 : ICON_HEIGHT / 2;
        int borderX = x + borderOffsetX;
        int borderY = y - this.top + borderOffsetY;

        double wayX = this.mapCenterX - (this.oldNorth ? -ptZ : ptX);
        double wayY = this.mapCenterZ - (this.oldNorth ? ptX : ptZ);
        float locate = (float) Math.atan2(wayX, wayY);
        float hypot = (float) Math.sqrt(wayX * wayX + wayY * wayY) * mapToGui;

        double dispX = hypot * Math.sin(locate);
        double dispY = hypot * Math.cos(locate);
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
            if (!options.showDistantWaypoints) {
                return false;
            }

            hypot *= (float) Math.min(borderX / Math.abs(dispX), borderY / Math.abs(dispY));
        }

        boolean uprightIcon = icon != null;

        String name = waypoint.name;
        if (waypointManager.isCoordinateHighlight(waypoint)) {
            name = "X:" + waypoint.getX() + ", Y:" + waypoint.getY() + ", Z:" + waypoint.getZ();
        }

        if (icon == null) {
            String iconLocation = (far ? "marker/" : "selectable/") + waypoint.imageSuffix;
            String fallbackLocation = far ? "marker/arrow" : WaypointManager.fallbackIconLocation;

            icon = textureAtlas.getAtlasSprite(iconLocation);
            if (icon == textureAtlas.getMissingImage()) {
                icon = textureAtlas.getAtlasSprite(fallbackLocation);
            }
        }

        graphics.pose().pushMatrix();

        if (far) {
            graphics.pose().translate(x, y);
            graphics.pose().rotate(-locate);
            if (uprightIcon) {
                graphics.pose().translate(0.0F, -hypot);
                graphics.pose().rotate(locate);
                graphics.pose().translate(-x, -y);
            } else {
                graphics.pose().translate(-x, -y);
                graphics.pose().translate(0.0F, -hypot);
            }
        } else {
            graphics.pose().rotate(-locate);
            graphics.pose().translate(0.0F, -hypot);
            graphics.pose().rotate(locate);
        }

        Vector2f guiVector = graphics.pose().transformPosition(new Vector2f(x, y));
        float screenX = guiVector.x();
        float screenY = guiVector.y();

        boolean isHovered = mouseX >= screenX - ICON_WIDTH / 2.0F && mouseX <= screenX + ICON_WIDTH / 2.0F
                && mouseY >= screenY - ICON_HEIGHT / 2.0F && mouseY <= screenY + ICON_HEIGHT / 2.0F;
        if (isHovered) {
            graphics.requestCursor(CursorTypes.CROSSHAIR);
            if (options.showCoordinates) {
                renderTooltip(graphics, Component.literal("X: " + waypoint.getX() + ", Y: " + waypoint.getY() + ", Z: " + waypoint.getZ()), this.mouseX, this.mouseY);
            }
        }

        int iconColor = color == -1 ? waypoint.getUnifiedColor(!waypoint.enabled && !isHighlighted && !isHovered ? 0.3F : 1.0F) : color;
        int textColor = !waypoint.enabled && !isHighlighted && !isHovered ? 0x55FFFFFF : 0xFFFFFFFF;

        icon.blit(graphics, RenderPipelines.GUI_TEXTURED, x - ICON_WIDTH / 2.0F, y - ICON_HEIGHT / 2.0F, ICON_WIDTH, ICON_HEIGHT, iconColor);

        boolean textOverFrame = screenY + ICON_HEIGHT > this.bottom;
        if (options.showWaypointNames && !far && !textOverFrame) {
            graphics.pose().pushMatrix();
            float fontScale = 1.0F;
            graphics.pose().scale(fontScale, fontScale);
            writeCentered(graphics, name, x / fontScale, (y + ICON_HEIGHT / 2.0F) / fontScale, textColor, true);
            graphics.pose().popMatrix();
        }

        graphics.pose().popMatrix();

        return isHovered;
    }

    private void drawSeedMapperMarkers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!seedMapperOptions.enabled) {
            return;
        }
        seedMapperMarkerHitboxes.clear();

        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            return;
        }

        long seed;
        try {
            seed = seedMapperOptions.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        int mapHalfWidth = (int) (this.centerX * this.guiToMap);
        int mapHalfHeight = (int) (this.centerY * this.guiToMap);
        int margin = 256;
        int rawMinX = (int) Math.floor(this.mapCenterX - mapHalfWidth) - margin;
        int rawMaxX = (int) Math.ceil(this.mapCenterX + mapHalfWidth) + margin;
        int rawMinZ = (int) Math.floor(this.mapCenterZ - mapHalfHeight) - margin;
        int rawMaxZ = (int) Math.ceil(this.mapCenterZ + mapHalfHeight) + margin;
        int keySnap = currentDragging ? 1024 : 512;
        int minX = Math.floorDiv(rawMinX, keySnap) * keySnap;
        int maxX = Math.floorDiv(rawMaxX + keySnap - 1, keySnap) * keySnap;
        int minZ = Math.floorDiv(rawMinZ, keySnap) * keySnap;
        int maxZ = Math.floorDiv(rawMaxZ + keySnap - 1, keySnap) * keySnap;
        int maxSpan = 16384;
        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        if (spanX > maxSpan) {
            int cx = (minX + maxX) / 2;
            minX = cx - maxSpan / 2;
            maxX = cx + maxSpan / 2;
        }
        if (spanZ > maxSpan) {
            int cz = (minZ + maxZ) / 2;
            minZ = cz - maxSpan / 2;
            maxZ = cz + maxSpan / 2;
        }

        int generatorFlags = 0;
        SeedMapperQueryCacheKey key = new SeedMapperQueryCacheKey(
                seed,
                dimension,
                generatorFlags,
                minX,
                maxX,
                minZ,
                maxZ,
                seedMapperOptions.showLootableOnly,
                enabledFeatureSetHash(),
                seedMapperOptions.getDatapackMarkerHash(),
                seedMapperOptions.lootSearch == null ? "" : seedMapperOptions.lootSearch,
                currentSeedMapperWorldKey()
        );

        long now = System.currentTimeMillis();
        long minIntervalMs = 1000L;
        String datapackWorldKey = currentSeedMapperWorldKey();
        boolean shouldRefresh = seedMapperLastMarkerQueryKey == null
                || !seedMapperLastMarkerQueryKey.equals(key)
                || now - seedMapperLastMarkerQueryMs >= minIntervalMs;
        if (shouldRefresh) {
            SeedMapperLocatorService.QueryResult result = SeedMapperLocatorService.get().queryWithStatus(
                    seed,
                    dimension,
                    SeedMapperCompat.getMcVersion(),
                    generatorFlags,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    seedMapperOptions,
                    datapackWorldKey);
            if (result.exact() || seedMapperLastMarkerResult.isEmpty()) {
                seedMapperLastMarkerResult = result.markers();
                seedMapperLastMarkerQueryKey = key;
            }
            seedMapperLastMarkerQueryMs = now;
        }
        List<SeedMapperMarker> markers = new ArrayList<>(seedMapperLastMarkerResult);
        boolean showNetherPortals = mapOptions.showNetherPortalMarkers && seedMapperOptions.isFeatureEnabled(SeedMapperFeature.NETHER_PORTAL);
        boolean showEndPortals = mapOptions.showEndPortalMarkers && seedMapperOptions.isFeatureEnabled(SeedMapperFeature.END_PORTAL);
        boolean showEndBeacons = mapOptions.showEndGatewayMarkers && seedMapperOptions.isFeatureEnabled(SeedMapperFeature.END_BEACON);
        if (showNetherPortals || showEndPortals || showEndBeacons) {
            for (com.mamiyaotaru.voxelmap.PortalMarkersManager.PortalMarker marker :
                    VoxelConstants.getVoxelMapInstance().getPortalMarkersManager()
                            .getMarkersInBounds(minX, maxX, minZ, maxZ, showNetherPortals, showEndPortals, showEndBeacons)) {
                SeedMapperFeature feature = switch (marker.type()) {
                    case NETHER -> SeedMapperFeature.NETHER_PORTAL;
                    case END -> SeedMapperFeature.END_PORTAL;
                    case END_BEACON -> SeedMapperFeature.END_BEACON;
                };
                if (feature == SeedMapperFeature.END_BEACON && !showEndBeacons) {
                    continue;
                }
                markers.add(new SeedMapperMarker(feature, marker.pos().getX(), marker.pos().getZ()));
            }
        }

        boolean lowDetail = mapToGui < 0.35F;
        int maxTotal = currentDragging ? 180 : 420;
        int maxDense = currentDragging ? 36 : 100;
        int denseDrawn = 0;
        int totalDrawn = 0;
        int scanned = 0;
        int maxScanned = currentDragging ? 1800 : 3200;
        double visibleHalfX = (this.centerX + ICON_WIDTH + 12.0D) / Math.max(0.0001D, this.mapToGui);
        double visibleHalfZ = (this.centerY + ICON_HEIGHT + 12.0D) / Math.max(0.0001D, this.mapToGui);
        for (SeedMapperMarker marker : markers) {
            if (++scanned > maxScanned || totalDrawn >= maxTotal) {
                break;
            }
            if (Math.abs(marker.blockX() - this.mapCenterX) > visibleHalfX
                    || Math.abs(marker.blockZ() - this.mapCenterZ) > visibleHalfZ) {
                continue;
            }
            if (lowDetail && (marker.feature() == SeedMapperFeature.SLIME_CHUNK
                    || marker.feature() == SeedMapperFeature.IRON_ORE_VEIN
                    || marker.feature() == SeedMapperFeature.COPPER_ORE_VEIN)) {
                if (denseDrawn >= maxDense) {
                    continue;
                }
                denseDrawn++;
            }
            drawSeedMapperMarker(graphics, marker, mouseX, mouseY);
            totalDrawn++;
        }
    }

    private void drawSeedMapperFeatureStrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        seedMapperIconHitboxes.clear();
        int currentDimension = getCurrentCubiomesDimension();
        int startX = this.sideMargin;
        int y = 32;
        int iconSize = 14;
        int gap = 3;
        int barHeight = iconSize + 8;
        int barWidth = Math.max(180, this.width - this.sideMargin * 2);
        graphics.fill(startX - 2, y - 2, startX + barWidth + 2, y + barHeight + 2, 0xAA000000);
        graphics.text(this.getFont(), Component.translatable("options.seedmapper.tab"), startX + 3, y + 2, 0xFFFFFFFF);

        int x = startX + this.getFont().width(Component.translatable("options.seedmapper.tab")) + 12;
        int contentEnd = startX + barWidth - 40;
        int perPage = Math.max(1, (contentEnd - x) / (iconSize + gap));
        List<SeedMapperFeature> visibleFeatures = new ArrayList<>();
        for (SeedMapperFeature feature : SeedMapperFeature.values()) {
            if (featureMatchesDimension(feature, currentDimension) && isSeedMapperFeatureVisible(feature)) {
                visibleFeatures.add(feature);
            }
        }
        SeedMapperFeature[] all = visibleFeatures.toArray(SeedMapperFeature[]::new);
        seedMapperLegendMaxPage = Math.max(0, (all.length - 1) / perPage);
        if (seedMapperLegendPage > seedMapperLegendMaxPage) {
            seedMapperLegendPage = seedMapperLegendMaxPage;
        }

        int startIndex = seedMapperLegendPage * perPage;
        int endIndex = Math.min(all.length, startIndex + perPage);
        for (int i = startIndex; i < endIndex; i++) {
            SeedMapperFeature feature = all[i];
            boolean enabled = seedMapperOptions.isFeatureEnabled(feature);
            int color = enabled ? 0xFFFFFFFF : 0x55FFFFFF;
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, feature.icon(), x, y, iconSize, iconSize, 0, 1, 0, 1, color);

            if (mouseX >= x && mouseX <= x + iconSize && mouseY >= y && mouseY <= y + iconSize) {
                graphics.requestCursor(CursorTypes.CROSSHAIR);
                Component tooltip = Component.translatable(feature.translationKey())
                        .append(Component.literal(enabled ? " (ON)" : " (OFF)"));
                renderTooltip(graphics, tooltip, mouseX, mouseY);
            }

            seedMapperIconHitboxes.add(new FeatureIconHitbox(feature, x, y, iconSize));
            x += iconSize + gap;
        }

        legendArrowSize = 12;
        legendPrevX = startX + barWidth - 32;
        legendPrevY = y + 1;
        legendNextX = startX + barWidth - 16;
        legendNextY = y + 1;

        int prevColor = seedMapperLegendPage > 0 ? 0xFFFFFFFF : 0x55FFFFFF;
        int nextColor = seedMapperLegendPage < seedMapperLegendMaxPage ? 0xFFFFFFFF : 0x55FFFFFF;
        graphics.text(this.getFont(), "<", legendPrevX, legendPrevY + 2, prevColor);
        graphics.text(this.getFont(), ">", legendNextX, legendNextY + 2, nextColor);
        graphics.text(this.getFont(), Component.literal((seedMapperLegendPage + 1) + "/" + (seedMapperLegendMaxPage + 1)), startX + barWidth - 68, y + 2, 0xFFAAAAAA);
    }

    private boolean handleSeedMapperIconClick(int mouseX, int mouseY) {
        if (!seedMapperOptions.enabled || seedMapperIconHitboxes.isEmpty()) {
            return false;
        }
        if (mouseX >= legendPrevX && mouseX <= legendPrevX + legendArrowSize && mouseY >= legendPrevY && mouseY <= legendPrevY + legendArrowSize) {
            if (seedMapperLegendPage > 0) {
                seedMapperLegendPage--;
            }
            return true;
        }
        if (mouseX >= legendNextX && mouseX <= legendNextX + legendArrowSize && mouseY >= legendNextY && mouseY <= legendNextY + legendArrowSize) {
            if (seedMapperLegendPage < seedMapperLegendMaxPage) {
                seedMapperLegendPage++;
            }
            return true;
        }
        for (FeatureIconHitbox hitbox : seedMapperIconHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                boolean ctrlDown = isCtrlDown();
                SeedMapperFeature clicked = hitbox.feature();
                if (ctrlDown) {
                    int currentHash = enabledFeatureSetHash();
                    if (seedMapperSavedToggles != null
                            && seedMapperIsolatedFeature == clicked
                            && currentHash == enabledFeatureSetHash(EnumSet.of(clicked))) {
                        if (enabledFeatureSetHash(seedMapperSavedToggles) == seedMapperIsolationBaseHash) {
                            seedMapperOptions.setEnabledFeatures(seedMapperSavedToggles);
                        }
                        seedMapperSavedToggles = null;
                        seedMapperIsolatedFeature = null;
                    } else {
                        if (seedMapperSavedToggles == null) {
                            seedMapperSavedToggles = seedMapperOptions.getEnabledFeaturesSnapshot();
                            seedMapperIsolationBaseHash = enabledFeatureSetHash(seedMapperSavedToggles);
                        }
                        seedMapperOptions.setOnlyFeatureEnabled(clicked);
                        seedMapperIsolatedFeature = clicked;
                        if (clicked == SeedMapperFeature.WORLD_SPAWN) {
                            centerOnWorldSpawn();
                        }
                    }
                } else {
                    seedMapperSavedToggles = null;
                    seedMapperIsolatedFeature = null;
                    seedMapperOptions.toggleFeature(clicked);
                }
                MapSettingsManager.instance.saveAll();
                return true;
            }
        }
        return false;
    }

    private boolean isSeedMapperFeatureVisible(SeedMapperFeature feature) {
        if (feature == SeedMapperFeature.NETHER_PORTAL) {
            return mapOptions.showNetherPortalMarkers;
        }
        if (feature == SeedMapperFeature.END_PORTAL) {
            return mapOptions.showEndPortalMarkers;
        }
        if (feature == SeedMapperFeature.END_BEACON) {
            return mapOptions.showEndGatewayMarkers;
        }
        return true;
    }

    private void drawSeedMapperMarker(GuiGraphicsExtractor graphics, SeedMapperMarker marker, int mouseX, int mouseY) {
        float ptX = marker.blockX() + 0.5F;
        float ptZ = marker.blockZ() + 0.5F;

        boolean datapackStructure = marker.feature() == SeedMapperFeature.DATAPACK_STRUCTURE;
        int iconWidth = datapackStructure ? SeedMapperImportedDatapackManager.iconSizeForPersistentMap() : ICON_WIDTH;
        int iconHeight = datapackStructure ? SeedMapperImportedDatapackManager.iconSizeForPersistentMap() : ICON_HEIGHT;
        int x = this.width / 2;
        int y = this.height / 2;
        int borderX = this.centerX + iconWidth / 2;
        int borderY = this.centerY + iconHeight / 2;

        double wayX = this.mapCenterX - (this.oldNorth ? -ptZ : ptX);
        double wayY = this.mapCenterZ - (this.oldNorth ? ptX : ptZ);
        float locate = (float) Math.atan2(wayX, wayY);
        float hypot = (float) Math.sqrt(wayX * wayX + wayY * wayY) * mapToGui;

        double dispX = hypot * Math.sin(locate);
        double dispY = hypot * Math.cos(locate);
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().rotate(-locate);
        graphics.pose().translate(0.0F, -hypot);
        graphics.pose().rotate(locate);

        Vector2f guiVector = graphics.pose().transformPosition(new Vector2f(x, y));
        float screenX = guiVector.x();
        float screenY = guiVector.y();
        // SeedMapper parity: never render offscreen icons on persistent map.
        if (screenX < iconWidth / 2.0F
                || screenX > this.width - iconWidth / 2.0F
                || screenY < this.top + iconHeight / 2.0F
                || screenY > this.bottom - iconHeight / 2.0F) {
            graphics.pose().popMatrix();
            return;
        }
        String worldKey = currentSeedMapperWorldKey();
        boolean completed = seedMapperOptions.isCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ());
        Waypoint linkedWaypoint = findWaypointForMarker(marker);
        seedMapperMarkerHitboxes.add(new SeedMapperMarkerHitbox(marker, worldKey, screenX, screenY, iconWidth, iconHeight));

        boolean isHovered = mouseX >= screenX - iconWidth / 2.0F && mouseX <= screenX + iconWidth / 2.0F
                && mouseY >= screenY - iconHeight / 2.0F && mouseY <= screenY + iconHeight / 2.0F;
        if (isHovered && popupOpen()) {
            graphics.requestCursor(CursorTypes.CROSSHAIR);
            Component tooltip = Component.translatable(marker.feature().translationKey())
                    .append(marker.label() == null || marker.label().isBlank() ? Component.empty() : Component.literal(" [" + marker.label() + "]"))
                    .append(Component.literal(" (X: " + marker.blockX() + ", Z: " + marker.blockZ() + ")"))
                    .append(Component.literal(completed ? " [Completed]" : ""));
            renderTooltip(graphics, tooltip, this.mouseX, this.mouseY);
        }

        int iconColor = datapackStructure
                ? SeedMapperImportedDatapackManager.colorForStructureId(marker.label())
                : 0xFFFFFFFF;
        if (datapackStructure) {
            drawDatapackMarker(graphics, x, y, iconWidth, iconHeight, iconColor);
        } else {
            Identifier icon = marker.feature().icon();
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, icon, x - iconWidth / 2.0F, y - iconHeight / 2.0F, iconWidth, iconHeight, 0, 1, 0, 1, iconColor);
        }
        if (linkedWaypoint != null) {
            drawIconStroke(graphics, Math.round(x - iconWidth / 2.0F), Math.round(y - iconHeight / 2.0F), iconWidth, iconHeight, linkedWaypoint.getUnifiedColor());
        }
        if (completed) {
            drawCompletedTick(graphics, Math.round(x - iconWidth / 2.0F), Math.round(y - iconHeight / 2.0F), iconWidth, iconHeight);
        }
        graphics.pose().popMatrix();
    }

    private void drawDatapackMarker(GuiGraphicsExtractor graphics, int x, int y, int iconWidth, int iconHeight, int color) {
        float left = x - iconWidth / 2.0F;
        float top = y - iconHeight / 2.0F;
        if (SeedMapperImportedDatapackManager.usesPotionIcon()) {
            Identifier potion = SeedMapperImportedDatapackManager.iconForStructureId("");
            Identifier overlay = SeedMapperImportedDatapackManager.iconOverlayForStructureId("");
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, potion, left, top, iconWidth, iconHeight, 0, 1, 0, 1, 0xFFFFFFFF);
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, overlay, left, top, iconWidth, iconHeight, 0, 1, 0, 1, color);
            return;
        }

        graphics.fill(Math.round(left) - 1, Math.round(top) - 1, Math.round(left + iconWidth) + 1, Math.round(top + iconHeight) + 1, 0xFF000000);
        graphics.fill(Math.round(left), Math.round(top), Math.round(left + iconWidth), Math.round(top + iconHeight), color);
    }

    private int getCurrentCubiomesDimension() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        if (currentLevel == null) {
            return Integer.MIN_VALUE;
        }
        if (currentLevel.dimension() == Level.NETHER) {
            return com.github.cubiomes.Cubiomes.DIM_NETHER();
        }
        if (currentLevel.dimension() == Level.END) {
            return com.github.cubiomes.Cubiomes.DIM_END();
        }
        return com.github.cubiomes.Cubiomes.DIM_OVERWORLD();
    }

    private boolean handleSeedMapperMarkerRightClick(int mouseX, int mouseY) {
        for (SeedMapperMarkerHitbox hitbox : seedMapperMarkerHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                selectedSeedMapperMarker = hitbox.marker();
                selectedSeedMapperWorldKey = hitbox.worldKey();
                selectedSeedMapperAssociatedWaypoint = findWaypointForMarker(selectedSeedMapperMarker);
                selectedSeedMapperWaypoint = selectedSeedMapperAssociatedWaypoint != null ? selectedSeedMapperAssociatedWaypoint : createTransientStructureWaypoint(selectedSeedMapperMarker);
                selectedWaypoint = selectedSeedMapperWaypoint;
                int mouseDirectX = (int) minecraft.mouseHandler.xpos();
                int mouseDirectY = (int) minecraft.mouseHandler.ypos();
                createStructurePopup(mouseX, mouseY, mouseDirectX, mouseDirectY);
                return true;
            }
        }
        return false;
    }

    private boolean handleSeedMapperMarkerLeftClick(int mouseX, int mouseY) {
        for (SeedMapperMarkerHitbox hitbox : seedMapperMarkerHitboxes) {
            if (!hitbox.contains(mouseX, mouseY)) {
                continue;
            }

            SeedMapperMarker marker = hitbox.marker();
            if (!marker.feature().lootable()) {
                return false;
            }

            long seed;
            try {
                seed = seedMapperOptions.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
            } catch (IllegalArgumentException ignored) {
                return true;
            }

            int dimension = getCurrentCubiomesDimension();
            int generatorFlags = 0;
            List<SeedMapperChestLootData> chestData = SeedMapperLootService.buildStructureChestLoot(
                    seed,
                    dimension,
                    SeedMapperCompat.getMcVersion(),
                    generatorFlags,
                    marker.feature(),
                    marker.blockX(),
                    marker.blockZ()
            );
            if (chestData.isEmpty()) {
                minecraft.gui.getChat().addClientSystemMessage(Component.literal("[SeedMapper] No chest loot data available for this structure."));
                return true;
            }

            int widgetX = Mth.clamp(mouseX + 10, 4, this.width - SeedMapperChestLootWidget.WIDTH - 4);
            int widgetY = Mth.clamp(mouseY + 10, this.top + 4, this.bottom - SeedMapperChestLootWidget.HEIGHT - 4);
            seedMapperChestLootWidget = new SeedMapperChestLootWidget(widgetX, widgetY, chestData);
            return true;
        }
        return false;
    }

    private void drawCompletedTick(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int size = Math.max(8, Math.min(width, height) - 4);
        int baseX = x + (width - size) / 2;
        int baseY = y + (height - size) / 2;
        int startX = baseX + size / 5;
        int startY = baseY + size * 3 / 5;
        int midX = baseX + size * 2 / 5;
        int midY = baseY + size * 4 / 5;
        int endX = baseX + size * 4 / 5;
        int endY = baseY + size / 5;
        drawLine(graphics, startX, startY, midX, midY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        drawLine(graphics, midX, midY, endX, endY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        drawLine(graphics, startX, startY, midX, midY, 1, COMPLETED_TICK_COLOR);
        drawLine(graphics, midX, midY, endX, endY, 1, COMPLETED_TICK_COLOR);
    }

    private void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            graphics.fill(x1 - thickness / 2, y1 - thickness / 2, x1 + thickness / 2 + 1, y1 + thickness / 2 + 1, color);
            return;
        }
        int radius = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + dx * i / steps;
            int py = y1 + dy * i / steps;
            graphics.fill(px - radius, py - radius, px + radius + 1, py + radius + 1, color);
        }
    }

    private boolean isCtrlDown() {
        long window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean isShiftDown() {
        long window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean featureMatchesDimension(SeedMapperFeature feature, int dimension) {
        if (feature == null) {
            return false;
        }

        if (dimension == Integer.MIN_VALUE) {
            return true;
        }

        if (feature == SeedMapperFeature.END_GATEWAY) {
            return dimension == com.github.cubiomes.Cubiomes.DIM_END();
        }

        if (feature == SeedMapperFeature.END_PORTAL) {
            return dimension != com.github.cubiomes.Cubiomes.DIM_NETHER();
        }

        if (feature == SeedMapperFeature.NETHER_PORTAL) {
            return dimension == com.github.cubiomes.Cubiomes.DIM_OVERWORLD()
                    || dimension == com.github.cubiomes.Cubiomes.DIM_NETHER();
        }

        if (feature == SeedMapperFeature.END_BEACON) {
            return dimension == com.github.cubiomes.Cubiomes.DIM_END();
        }

        return feature.availableInDimension(dimension);
    }

    private void createSeedMapperWaypoint(SeedMapperMarker marker) {
        if (marker == null || marker.feature() == null) {
            return;
        }

        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        DimensionContainer currentDimension = VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(VoxelConstants.getPlayer().level());
        dimensions.add(currentDimension);

        double dimensionScale = VoxelConstants.getPlayer().level().dimensionType().coordinateScale();
        int y = this.persistentMap.getHeightAt(marker.blockX(), marker.blockZ());
        if (y < VoxelConstants.getPlayer().level().getMinY()) {
            y = VoxelConstants.getPlayer().level().dimensionType().hasCeiling() ? 64 : VoxelConstants.getPlayer().level().getMaxY();
        }

        String name = Component.translatable(marker.feature().translationKey()).getString();
        if (marker.label() != null && !marker.label().isBlank()) {
            name = name + " [" + marker.label() + "]";
        }
        name = name + " (" + marker.blockX() + ", " + marker.blockZ() + ")";

        Waypoint waypoint = new Waypoint(
                name,
                (int) Math.round(marker.blockX() * dimensionScale),
                (int) Math.round(marker.blockZ() * dimensionScale),
                y,
                true,
                0.20F,
                0.85F,
                1.0F,
                "temple",
                waypointManager.getCurrentSubworldDescriptor(false),
                dimensions
        );
        waypointManager.addWaypoint(waypoint);
        minecraft.gui.getChat().addClientSystemMessage(Component.literal("[SeedMapper] Waypoint created for " + name));
    }

    private Waypoint createTransientStructureWaypoint(SeedMapperMarker marker) {
        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(VoxelConstants.getPlayer().level()));
        int y = this.persistentMap.getHeightAt(marker.blockX(), marker.blockZ());
        if (y < VoxelConstants.getPlayer().level().getMinY()) {
            y = VoxelConstants.getPlayer().level().dimensionType().hasCeiling() ? 64 : VoxelConstants.getPlayer().level().getMaxY();
        }
        return new Waypoint(
                displayMarkerName(marker),
                marker.blockX(),
                marker.blockZ(),
                y,
                true,
                0.20F,
                0.85F,
                1.0F,
                "temple",
                waypointManager.getCurrentSubworldDescriptor(false),
                dimensions
        );
    }

    private String displayMarkerName(SeedMapperMarker marker) {
        String name = Component.translatable(marker.feature().translationKey()).getString();
        if (marker.label() != null && !marker.label().isBlank()) {
            name = name + " [" + marker.label() + "]";
        }
        return name + " (" + marker.blockX() + ", " + marker.blockZ() + ")";
    }

    private void toggleSeedMapperMarkerCompleted(SeedMapperMarker marker, String worldKey) {
        if (marker == null || worldKey == null) {
            return;
        }
        boolean completed = seedMapperOptions.isCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ());
        seedMapperOptions.setCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ(), !completed);
        MapSettingsManager.instance.saveAll();
    }

    private Waypoint findWaypointForMarker(SeedMapperMarker marker) {
        if (marker == null) {
            return null;
        }
        for (Waypoint waypoint : waypointManager.getWaypoints()) {
            if (!waypoint.inWorld || !waypoint.inDimension) continue;
            if (waypoint.getX() == marker.blockX() && waypoint.getZ() == marker.blockZ()) {
                return waypoint;
            }
        }
        return null;
    }

    private boolean hasVisibleSeedMapperMarkerAt(int x, int z) {
        for (SeedMapperMarker marker : seedMapperLastMarkerResult) {
            if (marker.blockX() == x && marker.blockZ() == z) {
                return true;
            }
        }
        return false;
    }

    private void drawIconStroke(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y, color);
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
        graphics.fill(x - 1, y, x, y + height, color);
        graphics.fill(x + width, y, x + width + 1, y + height, color);
    }

    private int enabledFeatureSetHash() {
        return enabledFeatureSetHash(seedMapperOptions.getEnabledFeaturesSnapshot());
    }

    private int enabledFeatureSetHash(Set<SeedMapperFeature> features) {
        int hash = 1;
        if (features != null) {
            for (SeedMapperFeature feature : features) {
                hash = 31 * hash + feature.ordinal();
            }
        }
        return hash;
    }

    private String currentSeedMapperWorldKey() {
        String world = waypointManager.getCurrentWorldName();
        String sub = waypointManager.getCurrentSubworldDescriptor(false);
        Level level = GameVariableAccessShim.getWorld();
        String dim = level == null ? "unknown" : level.dimension().identifier().toString();
        return (world == null ? "unknown" : world) + "|" + (sub == null ? "" : sub) + "|" + dim;
    }

    private void centerOnWorldSpawn() {
        int dimension = getCurrentCubiomesDimension();
        if (dimension != com.github.cubiomes.Cubiomes.DIM_OVERWORLD()) {
            return;
        }
        long seed;
        try {
            seed = seedMapperOptions.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException ignored) {
            return;
        }
        List<SeedMapperMarker> markers = SeedMapperLocatorService.get().queryBlocking(
                seed,
                com.github.cubiomes.Cubiomes.DIM_OVERWORLD(),
                SeedMapperCompat.getMcVersion(),
                0,
                -8192,
                8192,
                -8192,
                8192,
                seedMapperOptions,
                currentSeedMapperWorldKey()
        );
        for (SeedMapperMarker marker : markers) {
            if (marker.feature() == SeedMapperFeature.WORLD_SPAWN) {
                centerAt(marker.blockX(), marker.blockZ());
                return;
            }
        }
    }

    private record FeatureIconHitbox(SeedMapperFeature feature, int x, int y, int size) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        }
    }

    private record SeedMapperMarkerHitbox(SeedMapperMarker marker, String worldKey, float centerX, float centerY, int width, int height) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= centerX - width / 2.0F && mouseX <= centerX + width / 2.0F
                    && mouseY >= centerY - height / 2.0F && mouseY <= centerY + height / 2.0F;
        }
    }

    private record SeedMapperQueryCacheKey(long seed, int dimension, int generatorFlags, int minX, int maxX, int minZ, int maxZ, boolean lootOnly, int enabledFeatureHash, int datapackHash, String lootSearch, String datapackWorldKey) {
    }

    public void renderBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, this.getWidth(), this.getHeight(), 0xFF000000);
    }

    protected void overlayBackground(GuiGraphicsExtractor graphics, int startY, int endY, int startAlpha, int endAlpha) {
        int colorBase = 0x404040;
        int colorStart = (startAlpha << 24) | colorBase;
        int colorEnd = (endAlpha << 24) | colorBase;
        float renderedTextureSize = 32.0F;
        VoxelMapGuiGraphics.blitFloatGradient(graphics, RenderPipelines.GUI_TEXTURED, VoxelConstants.getOptionsBackgroundTexture(), 0, startY, this.getWidth(), endY, 0, this.width / renderedTextureSize, 0, endY / renderedTextureSize, colorStart, colorEnd);
    }

    @Override
    public void tick() {
    }

    @Override
    public void removed() {
        synchronized (this.closedLock) {
            this.closed = true;
            this.persistentMap.getRegions(0, -1, 0, -1);
            this.regions = new CachedRegion[0];
        }
    }

    private void createPopup(int x, int y, int directX, int directY) {
        selectedSeedMapperMarker = null;
        selectedSeedMapperWorldKey = null;
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        float cursorX = directX;
        float cursorY = directY - this.top * this.guiToDirectMouse;
        float cursorCoordX;
        float cursorCoordZ;
        if (this.oldNorth) {
            cursorCoordX = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
            cursorCoordZ = -(cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
        } else {
            cursorCoordX = cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
            cursorCoordZ = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
        }

        Popup.PopupEntry entry;
        if (selectedWaypoint != null && this.waypointManager.getWaypoints().contains(selectedWaypoint)) {
            entry = new Popup.PopupEntry(I18n.get("selectServer.edit"), 4, true, true);
            entries.add(entry);
            entry = new Popup.PopupEntry(I18n.get("selectServer.delete"), 5, true, true);
            entries.add(entry);
            entry = new Popup.PopupEntry(I18n.get(selectedWaypoint != this.waypointManager.getHighlightedWaypoint() ? "minimap.waypoints.highlight" : "minimap.waypoints.removeHighlight"), 1, true, true);
        } else {
            entry = new Popup.PopupEntry(I18n.get("minimap.waypoints.newWaypoint"), 0, true, mapOptions.waypointsAllowed);
            entries.add(entry);
            entry = new Popup.PopupEntry(I18n.get(selectedWaypoint == null ? "minimap.waypoints.highlight" : "minimap.waypoints.removeHighlight"), 1, true, mapOptions.waypointsAllowed);
        }
        entries.add(entry);
        entry = new Popup.PopupEntry(I18n.get("minimap.waypoints.teleportTo"), 3, true, true);
        entries.add(entry);
        entry = new Popup.PopupEntry(I18n.get("minimap.waypoints.share"), 2, true, true);
        entries.add(entry);
        entry = new Popup.PopupEntry("Export Visible SeedMap", 6, true, seedMapperOptions.enabled);
        entries.add(entry);

        this.createPopup(x, y, directX, directY, 60, entries);
        if (VoxelConstants.DEBUG) {
            persistentMap.debugLog((int) cursorCoordX, (int) cursorCoordZ);
        }
    }

    private void createStructurePopup(int x, int y, int directX, int directY) {
        if (selectedSeedMapperMarker == null) {
            return;
        }
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        boolean completed = selectedSeedMapperWorldKey != null
                && seedMapperOptions.isCompleted(selectedSeedMapperWorldKey, selectedSeedMapperMarker.feature(), selectedSeedMapperMarker.blockX(), selectedSeedMapperMarker.blockZ());
        if (selectedSeedMapperAssociatedWaypoint != null) {
            entries.add(new Popup.PopupEntry(I18n.get("selectServer.edit"), 4, true, true));
            entries.add(new Popup.PopupEntry(I18n.get("selectServer.delete"), 5, true, true));
        } else {
            entries.add(new Popup.PopupEntry("Create Waypoint", 7, true, true));
        }
        entries.add(new Popup.PopupEntry(I18n.get(selectedWaypoint != waypointManager.getHighlightedWaypoint() ? "minimap.waypoints.highlight" : "minimap.waypoints.removeHighlight"), 1, true, true));
        entries.add(new Popup.PopupEntry(I18n.get("minimap.waypoints.teleportTo"), 3, true, true));
        entries.add(new Popup.PopupEntry(I18n.get("minimap.waypoints.share"), 2, true, true));
        entries.add(new Popup.PopupEntry(completed ? "Mark Incomplete" : "Mark Complete", 8, true, true));
        if (selectedSeedMapperMarker.feature().lootable()) {
            entries.add(new Popup.PopupEntry("Open Loot", 9, true, true));
        }
        this.createPopup(x, y, directX, directY, 110, entries);
    }

    private Waypoint getHoveredWaypoint() {
        if (!mapOptions.waypointsAllowed) {
            return null;
        }

        return hoverdWaypoint;
    }

    @Override
    public void popupAction(Popup popup, int action) {
        int mouseDirectX = popup.getClickedDirectX();
        int mouseDirectY = popup.getClickedDirectY();
        float cursorX = mouseDirectX;
        float cursorY = mouseDirectY - this.top * this.guiToDirectMouse;
        float cursorCoordX;
        float cursorCoordZ;
        if (this.oldNorth) {
            cursorCoordX = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
            cursorCoordZ = -(cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
        } else {
            cursorCoordX = cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
            cursorCoordZ = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
        }

        int x = (int) Math.floor(cursorCoordX);
        int z = (int) Math.floor(cursorCoordZ);
        int y = this.persistentMap.getHeightAt(x, z);
        this.editClicked = false;
        this.addClicked = false;
        this.deleteClicked = false;
        double dimensionScale = VoxelConstants.getPlayer().level().dimensionType().coordinateScale();
        switch (action) {
            case 0 -> {
                if (selectedWaypoint != null) {
                    x = selectedWaypoint.getX();
                    z = selectedWaypoint.getZ();
                }
                this.addClicked = true;
                float r;
                float g;
                float b;
                if (this.waypointManager.getWaypoints().isEmpty()) {
                    r = 0.0F;
                    g = 1.0F;
                    b = 0.0F;
                } else {
                    r = this.generator.nextFloat();
                    g = this.generator.nextFloat();
                    b = this.generator.nextFloat();
                }
                TreeSet<DimensionContainer> dimensions = new TreeSet<>();
                dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(VoxelConstants.getPlayer().level()));
                y = y > VoxelConstants.getPlayer().level().getMinY() ? y : 64;
                this.newWaypoint = new Waypoint("", (int) (x * dimensionScale), (int) (z * dimensionScale), y, true, r, g, b, "", VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions);
                minecraft.setScreen(new GuiAddWaypoint(this, this.newWaypoint, false));
            }
            case 1 -> {
                if (selectedWaypoint != null) {
                    this.waypointManager.setHighlightedWaypoint(selectedWaypoint, true);
                } else {
                    y = y > VoxelConstants.getPlayer().level().getMinY() ? y : 64;
                    TreeSet<DimensionContainer> dimensions2 = new TreeSet<>();
                    dimensions2.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(VoxelConstants.getPlayer().level()));
                    Waypoint fakePoint = new Waypoint("", (int) (x * dimensionScale), (int) (z * dimensionScale), y, true, 1.0F, 0.0F, 0.0F, "", VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions2);
                    this.waypointManager.setHighlightedWaypoint(fakePoint, true);
                }
            }
            case 2 -> {
                if (selectedWaypoint != null) {
                    CommandUtils.sendWaypoint(selectedWaypoint);
                } else {
                    y = y > VoxelConstants.getPlayer().level().getMinY() ? y : 64;
                    CommandUtils.sendCoordinate(x, y, z);
                }
            }
            case 3 -> {
                if (selectedWaypoint == null) {
                    if (y < VoxelConstants.getPlayer().level().getMinY()) {
                        y = (!(VoxelConstants.getPlayer().level().dimensionType().hasCeiling()) ? VoxelConstants.getPlayer().level().getMaxY() : 64);
                    }
                    VoxelConstants.playerRunTeleportCommand(x, y, z);
                    break;
                }

                y = selectedWaypoint.getY() > VoxelConstants.getPlayer().level().getMinY() ? selectedWaypoint.getY() : (!(VoxelConstants.getPlayer().level().dimensionType().hasCeiling()) ? VoxelConstants.getPlayer().level().getMaxY() : 64);
                VoxelConstants.playerRunTeleportCommand(selectedWaypoint.getX(), y, selectedWaypoint.getZ());
            }
            case 4 -> {
                if (selectedWaypoint != null) {
                    this.editClicked = true;
                    minecraft.setScreen(new GuiAddWaypoint(this, selectedWaypoint, true));
                }
            }
            case 5 -> {
                if (selectedWaypoint != null) {
                    pendingDeleteWaypoint = selectedWaypoint;
                    if (mapOptions.confirmWaypointDelete) {
                        createDeleteConfirmationPopup();
                    } else {
                        deleteSelectedWaypoint();
                    }
                }
            }
            case 6 -> {
                int minX = (int) Math.floor(this.mapCenterX - this.centerX * this.guiToMap);
                int maxX = (int) Math.ceil(this.mapCenterX + this.centerX * this.guiToMap);
                int minZ = (int) Math.floor(this.mapCenterZ - this.centerY * this.guiToMap);
                int maxZ = (int) Math.ceil(this.mapCenterZ + this.centerY * this.guiToMap);
                SeedMapperCommandHandler.exportBounds(minX, maxX, minZ, maxZ, "persistent_map_visible");
            }
            case 7 -> {
                if (selectedSeedMapperMarker != null) {
                    createSeedMapperWaypoint(selectedSeedMapperMarker);
                }
            }
            case 8 -> {
                if (selectedSeedMapperMarker != null && selectedSeedMapperWorldKey != null) {
                    toggleSeedMapperMarkerCompleted(selectedSeedMapperMarker, selectedSeedMapperWorldKey);
                }
            }
            case 9 -> {
                if (selectedSeedMapperMarker != null) {
                    long seed;
                    try {
                        seed = seedMapperOptions.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
                    } catch (IllegalArgumentException ignored) {
                        break;
                    }

                    int dimension = getCurrentCubiomesDimension();
                    List<SeedMapperChestLootData> chestData = SeedMapperLootService.buildStructureChestLoot(
                            seed,
                            dimension,
                            SeedMapperCompat.getMcVersion(),
                            0,
                            selectedSeedMapperMarker.feature(),
                            selectedSeedMapperMarker.blockX(),
                            selectedSeedMapperMarker.blockZ()
                    );
                    if (chestData.isEmpty()) {
                        minecraft.gui.getChat().addClientSystemMessage(Component.literal("[SeedMapper] No chest loot data available for this structure."));
                    } else {
                        int widgetX = Mth.clamp((int) popup.getClickedDirectX() / (int) this.guiToDirectMouse + 10, 4, this.width - SeedMapperChestLootWidget.WIDTH - 4);
                        int widgetY = Mth.clamp((int) (popup.getClickedDirectY() / this.guiToDirectMouse) + 10, this.top + 4, this.bottom - SeedMapperChestLootWidget.HEIGHT - 4);
                        seedMapperChestLootWidget = new SeedMapperChestLootWidget(widgetX, widgetY, chestData);
                    }
                }
            }
            case 10 -> deleteSelectedWaypoint();
            case 11 -> pendingDeleteWaypoint = null;
            default -> VoxelConstants.getLogger().warn("unimplemented command");
        }

        if (action >= 7 && action <= 9) {
            selectedSeedMapperMarker = null;
            selectedSeedMapperWorldKey = null;
            selectedSeedMapperWaypoint = null;
            selectedSeedMapperAssociatedWaypoint = null;
        }

    }

    @Override
    public boolean isEditing() {
        return this.editClicked;
    }

    @Override
    public void accept(boolean b) {
        if (this.deleteClicked) {
            this.deleteClicked = false;
            if (b) {
                deleteSelectedWaypoint();
            }
        }

        if (this.editClicked) {
            this.editClicked = false;
            if (b) {
                this.waypointManager.saveWaypoints();
            }
        }

        if (this.addClicked) {
            this.addClicked = false;
            if (b) {
                this.waypointManager.addWaypoint(this.newWaypoint);
            }
        }

        minecraft.setScreen(this);
    }

    private void createDeleteConfirmationPopup() {
        // Remove the previous context popup so the confirmation dialog receives all clicks.
        clearPopups();
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        entries.add(new Popup.PopupEntry("Confirm Delete?", -1, false, false));
        entries.add(new Popup.PopupEntry(I18n.get("selectServer.deleteButton"), 10, true, true));
        entries.add(new Popup.PopupEntry(I18n.get("gui.cancel"), 11, true, true));
        createPopup(this.width / 2 - 45, this.height / 2 - 20, this.mouseX, this.mouseY, 90, entries);
    }

    private void deleteSelectedWaypoint() {
        Waypoint toDelete = this.selectedWaypoint != null ? this.selectedWaypoint : this.pendingDeleteWaypoint;
        if (toDelete == null) {
            return;
        }

        this.waypointManager.deleteWaypoint(toDelete);
        if (this.selectedWaypoint == toDelete) {
            this.selectedWaypoint = null;
        }
        this.pendingDeleteWaypoint = null;
    }

    private int textWidth(String string) {
        return minecraft.font.width(string);
    }

    private int textWidth(Component text) {
        return minecraft.font.width(text);
    }

    private void write(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean shadow) {
        write(graphics, Component.nullToEmpty(text), x, y, color, shadow);
    }

    private void write(GuiGraphicsExtractor graphics, Component text, float x, float y, int color, boolean shadow) {
        graphics.text(minecraft.font, text, (int) x, (int) y, color, shadow);
    }

    private void writeCentered(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean shadow) {
        writeCentered(graphics, Component.nullToEmpty(text), x, y, color, shadow);
    }

    private void writeCentered(GuiGraphicsExtractor graphics, Component text, float x, float y, int color, boolean shadow) {
        graphics.text(minecraft.font, text, (int) x - (textWidth(text) / 2), (int) y, color, shadow);
    }

    public void exportVisibleSeedMap() {
        int minX = (int) Math.floor(this.mapCenterX - this.centerX * this.guiToMap);
        int maxX = (int) Math.ceil(this.mapCenterX + this.centerX * this.guiToMap);
        int minZ = (int) Math.floor(this.mapCenterZ - this.centerY * this.guiToMap);
        int maxZ = (int) Math.ceil(this.mapCenterZ + this.centerY * this.guiToMap);
        SeedMapperCommandHandler.exportBounds(minX, maxX, minZ, maxZ, "persistent_map_visible");
    }
}
