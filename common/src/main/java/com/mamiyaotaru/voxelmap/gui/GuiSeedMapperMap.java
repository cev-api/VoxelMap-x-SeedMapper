package com.mamiyaotaru.voxelmap.gui;

import com.github.cubiomes.Cubiomes;
import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCompat;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperLocatorService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperMarker;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperMapBiomeSampler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.util.DimensionManager;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

/**
 * The standalone SeedMapper map.  The VoxelMap world map remains responsible
 * for terrain, waypoints, plots, entities, and its existing overlays; this
 * screen provides the upstream-style SeedMapper map entry point and zoom
 * model without coupling the two map renderers together.
 */
public final class GuiSeedMapperMap extends GuiScreenMinimap {
    private static final double MIN_BLOCKS_PER_PIXEL = 0.25D;
    private static final double MAX_BLOCKS_PER_PIXEL = 2048.0D;
    /**
     * Upper bound on the window handed to the locator. The locator skips
     * features whose region grid would exceed its own budget, so this stays
     * inside that budget for the structures that have a usable spacing.
     */
    private static final int MAX_QUERY_SPAN = 65536;
    private static final double ZOOM_STEP_IN = 0.8D;
    private static final double ZOOM_STEP_OUT = 1.25D;
    private static final double FAST_ZOOM_MULTIPLIER = 3.0D;
    /** Roughly how many grid lines to keep on screen at any zoom level. */
    private static final double TARGET_GRID_LINES = 12.0D;
    private static final int MAX_RENDERED_MARKERS = 12_000;
    private static final int MAP_MARGIN = 18;
    private static final int MAP_TOP = 28;
    private static final int CONTROLS_HEIGHT = 42;

    private final SeedMapperSettingsManager settings;
    private double centerX;
    private double centerZ;
    private double blocksPerPixel = 1.0D;
    private boolean dragging;
    private boolean queryDirty = true;
    private long lastQueryMs;
    private List<SeedMapperMarker> markers = List.of();
    private SeedMapperMarker hoveredMarker;
    private String queryStatus = "Loading SeedMapper data...";
    private EditBox xInput;
    private EditBox zInput;
    private Button reloadButton;
    private Button biomeYButton;
    private SeedMapperMapBiomeSampler.CacheKey biomePreviewKey;
    private SeedMapperMapBiomeSampler.Sample biomePreview;
    private Future<SeedMapperMapBiomeSampler.Sample> biomePreviewFuture;
    private boolean biomePreviewDirty = true;
    private boolean controlHeld;
    private boolean shiftHeld;
    private boolean queryClamped;

    public GuiSeedMapperMap(Screen parent) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        try {
            this.centerX = GameVariableAccessShim.xCoordDouble();
            this.centerZ = GameVariableAccessShim.zCoordDouble();
        } catch (RuntimeException ignored) {
            this.centerX = 0.0D;
            this.centerZ = 0.0D;
        }
    }

    public static void openFromCommand() {
        Screen current = VoxelConstants.getMinecraft().gui.screen();
        VoxelConstants.getMinecraft().gui.setScreen(new GuiSeedMapperMap(current));
    }

    @Override
    public void init() {
        int controlsTop = this.height - CONTROLS_HEIGHT;
        int fieldWidth = 82;
        int left = MAP_MARGIN;

        xInput = new EditBox(this.font, left, controlsTop + 2, fieldWidth, 20, Component.literal("X"));
        xInput.setHint(Component.literal("X"));
        xInput.setMaxLength(12);
        xInput.setValue(Integer.toString(Mth.floor(centerX)));
        addRenderableWidget(xInput);

        zInput = new EditBox(this.font, left + fieldWidth + 6, controlsTop + 2, fieldWidth, 20, Component.literal("Z"));
        zInput.setHint(Component.literal("Z"));
        zInput.setMaxLength(12);
        zInput.setValue(Integer.toString(Mth.floor(centerZ)));
        addRenderableWidget(zInput);

        int buttonLeft = left + (fieldWidth + 6) * 2;
        addRenderableWidget(Button.builder(Component.literal("Center"), button -> commitCenter())
                .bounds(buttonLeft, controlsTop + 2, 62, 20).build());
        reloadButton = addRenderableWidget(Button.builder(Component.literal("Reload"), button -> {
            queryDirty = true;
            queryStatus = "Loading SeedMapper data...";
        }).bounds(buttonLeft + 68, controlsTop + 2, 62, 20).build());
        biomeYButton = addRenderableWidget(Button.builder(biomeYLabel(), button -> cycleBiomeY(1))
                .bounds(buttonLeft + 136, controlsTop + 2, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(this.width - MAP_MARGIN - 70, controlsTop + 2, 70, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (queryDirty || now - lastQueryMs >= 500L) {
            refreshQuery();
            lastQueryMs = now;
        }
        refreshBiomePreview();
    }

    private void refreshQuery() {
        int dimension = currentDimension();
        if (dimension == Integer.MIN_VALUE) {
            markers = List.of();
            queryStatus = "No supported world loaded.";
            queryDirty = false;
            biomePreviewDirty = true;
            return;
        }

        long seed;
        try {
            seed = settings.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException ignored) {
            markers = List.of();
            queryStatus = "No seed available. Set Seed Input in SeedMapper options.";
            queryDirty = false;
            biomePreviewDirty = true;
            return;
        }

        int[] bounds = queryBounds();
        SeedMapperLocatorService.QueryResult result = SeedMapperLocatorService.get().queryWithStatus(
                seed,
                dimension,
                SeedMapperCompat.getMcVersion(),
                settings.largeBiomes ? Cubiomes.LARGE_BIOMES() : 0,
                bounds[0], bounds[1], bounds[2], bounds[3], settings,
                currentWorldKey());
        markers = result.markers();
        if (queryClamped) {
            queryStatus = "Zoomed out: markers only cover the central " + MAX_QUERY_SPAN + " block area.";
        } else if (!result.exact()) {
            queryStatus = "Calculating SeedMapper markers...";
        } else {
            queryStatus = "";
        }
        queryDirty = !result.exact();
        biomePreviewDirty = true;
    }

    private void refreshBiomePreview() {
        int dimension = currentDimension();
        if (dimension == Integer.MIN_VALUE) {
            biomePreview = null;
            biomePreviewKey = null;
            return;
        }
        long seed;
        try {
            seed = settings.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException ignored) {
            biomePreview = null;
            biomePreviewKey = null;
            return;
        }
        int[] bounds = queryBounds();
        SeedMapperMapBiomeSampler.CacheKey key = new SeedMapperMapBiomeSampler.CacheKey(
                seed, dimension, SeedMapperCompat.getMcVersion(),
                settings.largeBiomes ? Cubiomes.LARGE_BIOMES() : 0,
                bounds[0], bounds[1], bounds[2], bounds[3], settings.seedMapBiomeY);
        if (key.equals(biomePreviewKey) && biomePreview != null && biomePreview.available()) {
            biomePreviewDirty = false;
            return;
        }
        if (biomePreviewFuture != null && !biomePreviewFuture.isDone()) {
            return;
        }
        if (!biomePreviewDirty && key.equals(biomePreviewKey)) {
            return;
        }
        biomePreviewKey = key;
        biomePreview = null;
        biomePreviewDirty = false;
        biomePreviewFuture = SeedMapperMapBiomeSampler.request(key);
        if (biomePreviewFuture.isDone()) {
            applyBiomePreview();
        }
    }

    private void applyBiomePreview() {
        if (biomePreviewFuture == null || !biomePreviewFuture.isDone()) {
            return;
        }
        try {
            SeedMapperMapBiomeSampler.Sample result = biomePreviewFuture.get();
            if (result != null && result.key().equals(biomePreviewKey)) {
                biomePreview = result;
            }
        } catch (Exception ignored) {
            biomePreview = null;
        } finally {
            biomePreviewFuture = null;
        }
    }

    private int[] queryBounds() {
        double halfWidth = Math.max(64.0D, (mapRight() - mapLeft()) * blocksPerPixel / 2.0D);
        double halfHeight = Math.max(64.0D, (mapBottom() - mapTop()) * blocksPerPixel / 2.0D);
        queryClamped = halfWidth > MAX_QUERY_SPAN / 2.0D || halfHeight > MAX_QUERY_SPAN / 2.0D;
        halfWidth = Math.min(halfWidth, MAX_QUERY_SPAN / 2.0D);
        halfHeight = Math.min(halfHeight, MAX_QUERY_SPAN / 2.0D);
        return new int[]{
                safeBlock(centerX - halfWidth), safeBlock(centerX + halfWidth),
                safeBlock(centerZ - halfHeight), safeBlock(centerZ + halfHeight)
        };
    }

    /** Visible width of the map in blocks, for the on-screen readout. */
    private long visibleSpanX() {
        return Math.round(Math.max(1, mapRight() - mapLeft()) * blocksPerPixel);
    }

    private int safeBlock(double value) {
        if (value <= Integer.MIN_VALUE + 2.0D) return Integer.MIN_VALUE + 2;
        if (value >= Integer.MAX_VALUE - 2.0D) return Integer.MAX_VALUE - 2;
        return Mth.floor(value);
    }

    private int currentDimension() {
        Level level = VoxelConstants.getMinecraft().level;
        if (level == null) return Integer.MIN_VALUE;
        return switch (DimensionManager.getEnvironment(level)) {
            case OVERWORLD -> Cubiomes.DIM_OVERWORLD();
            case NETHER -> Cubiomes.DIM_NETHER();
            case END -> Cubiomes.DIM_END();
            case CUSTOM_OR_UNKNOWN -> Integer.MIN_VALUE;
        };
    }

    private String currentWorldKey() {
        var waypointManager = VoxelConstants.getVoxelMapInstance().getWaypointManager();
        String world = waypointManager.getCurrentWorldName();
        String subworld = waypointManager.getCurrentSubworldDescriptor(false);
        Level level = VoxelConstants.getMinecraft().level;
        String dimension = level == null ? "unknown" : level.dimension().identifier().toString();
        return (world == null ? "unknown" : world) + "|"
                + (subworld == null ? "" : subworld) + "|" + dimension;
    }

    private int mapLeft() {
        return MAP_MARGIN;
    }

    private int mapRight() {
        return Math.max(mapLeft() + 1, this.width - MAP_MARGIN);
    }

    private int mapTop() {
        return MAP_TOP;
    }

    private int mapBottom() {
        return Math.max(mapTop() + 1, this.height - CONTROLS_HEIGHT - 8);
    }

    private boolean inMap(double x, double z) {
        return x >= mapLeft() && x <= mapRight() && z >= mapTop() && z <= mapBottom();
    }

    private int screenX(int blockX) {
        return (int) Math.round((this.width / 2.0D) + (blockX - centerX) / blocksPerPixel);
    }

    private int screenZ(int blockZ) {
        return (int) Math.round(((mapTop() + mapBottom()) / 2.0D) + (blockZ - centerZ) / blocksPerPixel);
    }

    private void commitCenter() {
        try {
            centerX = Integer.parseInt(xInput.getValue().trim());
            centerZ = Integer.parseInt(zInput.getValue().trim());
            queryDirty = true;
            queryStatus = "Loading SeedMapper data...";
        } catch (NumberFormatException ignored) {
            queryStatus = "Enter integer X and Z coordinates.";
        }
    }

    private void cycleBiomeY(int direction) {
        settings.setSeedMapBiomeY(settings.seedMapBiomeY + direction * 4);
        MapSettingsManager.instance.saveAll();
        if (biomeYButton != null) {
            biomeYButton.setMessage(biomeYLabel());
        }
        queryDirty = true;
        biomePreviewDirty = true;
        queryStatus = "Loading SeedMapper data...";
    }

    private Component biomeYLabel() {
        return Component.literal("Biome Y: " + settings.seedMapBiomeY);
    }

    private void updateCoordinateFields() {
        if (xInput != null && !xInput.isFocused()) xInput.setValue(Integer.toString(Mth.floor(centerX)));
        if (zInput != null && !zInput.isFocused()) zInput.setValue(Integer.toString(Mth.floor(centerZ)));
    }

    private void zoom(double scrollY, double mouseX, double mouseY) {
        if (scrollY == 0.0D) return;
        double oldScale = blocksPerPixel;
        double stepIn = ZOOM_STEP_IN;
        double stepOut = ZOOM_STEP_OUT;
        if (shiftHeld) {
            // Holding shift multiplies the notch size, which makes crossing the
            // full range practical without adding extra buttons.
            stepIn = Math.pow(stepIn, FAST_ZOOM_MULTIPLIER);
            stepOut = Math.pow(stepOut, FAST_ZOOM_MULTIPLIER);
        }
        blocksPerPixel = Mth.clamp(oldScale * (scrollY > 0.0D ? stepIn : stepOut), MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
        if (oldScale == blocksPerPixel) return;

        // Keep the block under the pointer fixed while zooming, like the
        // upstream SeedMapper map.
        double mapCenterX = this.width / 2.0D;
        double mapCenterZ = (mapTop() + mapBottom()) / 2.0D;
        double worldX = centerX + (mouseX - mapCenterX) * oldScale;
        double worldZ = centerZ + (mouseY - mapCenterZ) * oldScale;
        centerX = worldX - (mouseX - mapCenterX) * blocksPerPixel;
        centerZ = worldZ - (mouseY - mapCenterZ) * blocksPerPixel;
        updateCoordinateFields();
        queryDirty = true;
        biomePreviewDirty = true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount)) return true;
        if (inMap(mouseX, mouseY) && controlDown()) {
            cycleBiomeY(amount > 0.0D ? 1 : -1);
            return true;
        }
        zoom(amount, mouseX, mouseY);
        return true;
    }

    private boolean controlDown() {
        return controlHeld;
    }

    /** Called by the SDL touchpad bridge used by the upstream SeedMapper map. */
    public void pinchUpdated(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale) || scale == 1.0F) return;
        double mouseX = VoxelConstants.getMinecraft().mouseHandler.xpos()
                * this.width / (double) VoxelConstants.getMinecraft().getWindow().getScreenWidth();
        double mouseY = VoxelConstants.getMinecraft().mouseHandler.ypos()
                * this.height / (double) VoxelConstants.getMinecraft().getWindow().getScreenHeight();
        zoom(scale > 1.0F ? 1.0D : -1.0D, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inMap(event.x(), event.y())) {
            dragging = true;
            hoveredMarker = findMarker(event.x(), event.y());
            return true;
        }
        return event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            centerX -= dragX * blocksPerPixel;
            centerZ -= dragY * blocksPerPixel;
            updateCoordinateFields();
            queryDirty = true;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        hoveredMarker = inMap(mouseX, mouseY) ? findMarker(mouseX, mouseY) : null;
        super.mouseMoved(mouseX, mouseY);
    }

    private SeedMapperMarker findMarker(double mouseX, double mouseY) {
        SeedMapperMarker closest = null;
        double bestDistance = 10.0D;
        int examined = 0;
        for (SeedMapperMarker marker : markers) {
            if (++examined > MAX_RENDERED_MARKERS) break;
            double distance = Math.hypot(screenX(marker.blockX()) - mouseX, screenZ(marker.blockZ()) - mouseY);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = marker;
            }
        }
        return closest;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LCONTROL || event.key() == InputConstants.KEY_RCONTROL) {
            controlHeld = true;
        }
        if (event.key() == InputConstants.KEY_LSHIFT || event.key() == InputConstants.KEY_RSHIFT) {
            shiftHeld = true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if ((xInput != null && xInput.isFocused()) || (zInput != null && zInput.isFocused())) {
                commitCenter();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LCONTROL || event.key() == InputConstants.KEY_RCONTROL) {
            controlHeld = false;
        }
        if (event.key() == InputConstants.KEY_LSHIFT || event.key() == InputConstants.KEY_RSHIFT) {
            shiftHeld = false;
        }
        return super.keyReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        applyBiomePreview();
        graphics.fill(0, 0, this.width, this.height, 0xFF101318);
        graphics.fill(mapLeft(), mapTop(), mapRight(), mapBottom(), 0xFF1B2630);

        drawBiomePreview(graphics);
        drawGrid(graphics);
        int drawn = 0;
        for (SeedMapperMarker marker : markers) {
            if (++drawn > MAX_RENDERED_MARKERS) break;
            int x = screenX(marker.blockX());
            int z = screenZ(marker.blockZ());
            if (!inMap(x, z)) continue;
            int color = featureColor(marker.feature());
            graphics.fill(x - 3, z - 3, x + 4, z + 4, 0xFF000000);
            graphics.fill(x - 2, z - 2, x + 3, z + 3, color);
        }

        int centerScreenX = this.width / 2;
        int centerScreenZ = (mapTop() + mapBottom()) / 2;
        graphics.fill(centerScreenX - 8, centerScreenZ, centerScreenX + 9, centerScreenZ + 1, 0xFFFFFFFF);
        graphics.fill(centerScreenX, centerScreenZ - 8, centerScreenX + 1, centerScreenZ + 9, 0xFFFFFFFF);

        graphics.text(this.font, Component.literal("SeedMapper Map  |  " + settings.resolveSeedText(VoxelConstants.getVoxelMapInstance().getWorldSeed())), MAP_MARGIN, 8, 0xFFFFFFFF);
        graphics.text(this.font, Component.literal(String.format(Locale.ROOT, "Zoom: %.2f blocks/pixel  |  View: %d blocks  |  Biome Y: %d  |  Markers: %d", blocksPerPixel, visibleSpanX(), settings.seedMapBiomeY, markers.size())), MAP_MARGIN + 220, 8, 0xFFB8C7D9);
        if (queryStatus != null && !queryStatus.isBlank()) {
            graphics.text(this.font, Component.literal(queryStatus), MAP_MARGIN + 4, mapBottom() - 18, 0xFFFFD166);
        }
        if (hoveredMarker != null) {
            String label = hoveredMarker.feature().id() + " @ " + hoveredMarker.blockX() + ", " + hoveredMarker.blockZ();
            graphics.fill(mouseX + 8, mouseY + 8, mouseX + 12 + this.font.width(label), mouseY + 25, 0xDD000000);
            graphics.text(this.font, Component.literal(label), mouseX + 10, mouseY + 11, 0xFFFFFFFF);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawBiomePreview(GuiGraphicsExtractor graphics) {
        if (biomePreview == null || !biomePreview.available()) {
            return;
        }
        int mapWidth = Math.max(1, mapRight() - mapLeft());
        int mapHeight = Math.max(1, mapBottom() - mapTop());
        for (int z = 0; z < SeedMapperMapBiomeSampler.SAMPLE_HEIGHT; z++) {
            int top = mapTop() + z * mapHeight / SeedMapperMapBiomeSampler.SAMPLE_HEIGHT;
            int bottom = mapTop() + (z + 1) * mapHeight / SeedMapperMapBiomeSampler.SAMPLE_HEIGHT;
            for (int x = 0; x < SeedMapperMapBiomeSampler.SAMPLE_WIDTH; x++) {
                int left = mapLeft() + x * mapWidth / SeedMapperMapBiomeSampler.SAMPLE_WIDTH;
                int right = mapLeft() + (x + 1) * mapWidth / SeedMapperMapBiomeSampler.SAMPLE_WIDTH;
                graphics.fill(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom), biomePreview.colorAt(x, z));
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        int gridStep = gridStep();
        int minX = safeBlock(centerX - (mapRight() - mapLeft()) * blocksPerPixel / 2.0D);
        int maxX = safeBlock(centerX + (mapRight() - mapLeft()) * blocksPerPixel / 2.0D);
        int minZ = safeBlock(centerZ - (mapBottom() - mapTop()) * blocksPerPixel / 2.0D);
        int maxZ = safeBlock(centerZ + (mapBottom() - mapTop()) * blocksPerPixel / 2.0D);
        for (long worldX = (long) Math.floorDiv(minX, gridStep) * gridStep; worldX <= maxX; worldX += gridStep) {
            int x = screenX((int) worldX);
            graphics.fill(x, mapTop(), x + 1, mapBottom(), 0x333E566B);
            if (x >= mapLeft() && x < mapRight()) {
                graphics.text(this.font, Component.literal(Long.toString(worldX)), x + 2, mapTop() + 2, 0x8890A4B8);
            }
        }
        for (long worldZ = (long) Math.floorDiv(minZ, gridStep) * gridStep; worldZ <= maxZ; worldZ += gridStep) {
            int z = screenZ((int) worldZ);
            graphics.fill(mapLeft(), z, mapRight(), z + 1, 0x333E566B);
            if (z >= mapTop() && z < mapBottom()) {
                graphics.text(this.font, Component.literal(Long.toString(worldZ)), mapLeft() + 2, z + 2, 0x8890A4B8);
            }
        }
    }

    /**
     * Picks a power-of-two grid spacing so the number of drawn lines stays
     * roughly constant from the closest to the furthest zoom level.
     */
    private int gridStep() {
        double span = Math.max(mapRight() - mapLeft(), mapBottom() - mapTop()) * blocksPerPixel;
        double target = Math.max(16.0D, span / TARGET_GRID_LINES);
        int step = 16;
        while (step < target && step < (1 << 28)) {
            step <<= 1;
        }
        return step;
    }

    private int featureColor(SeedMapperFeature feature) {
        return switch (feature) {
            case STRONGHOLD -> 0xFFFFD166;
            case TREASURE, TREASURE_CLUSTER -> 0xFFFF9F43;
            case END_CITY, ELYTRA, END_GATEWAY -> 0xFFB084FF;
            case FORTRESS, BASTION, NETHER_FOSSIL, RUINED_PORTAL_N -> 0xFFFF6B6B;
            case MINESHAFT, GEODE, COPPER_ORE_VEIN, IRON_ORE_VEIN -> 0xFF8DCCFF;
            case CANYON, SULFUR_CAVES -> 0xFFEF8354;
            default -> 0xFF77DD77;
        };
    }
}
