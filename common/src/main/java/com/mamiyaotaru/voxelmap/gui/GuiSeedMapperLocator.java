package com.mamiyaotaru.voxelmap.gui;

import com.github.cubiomes.Cubiomes;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCompat;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperLootService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class GuiSeedMapperLocator extends GuiScreenMinimap {
    private static final int LIST_WIDTH = 300;
    private static final int LIST_ROW_HEIGHT = 18;
    private static final int VARIANT_ROW_HEIGHT = 16;
    private static final int VARIANT_VISIBLE_ROWS = 3;

    public enum Mode {
        STRUCTURE("Locate Structure"),
        BIOME("Locate Biome"),
        LOOT("Locate Loot");

        private final String title;

        Mode(String title) {
            this.title = title;
        }
    }

    private final Mode mode;
    private LocateList list;
    private VariantList variantList;
    private List<String> allQueries = List.of();
    private List<String> visiblePrimaryQueries = List.of();
    private boolean lootQueriesLoading = false;
    private boolean lootQueriesLoaded = false;
    private String selectedQuery = "";
    private String selectedVariantQuery = "";
    private SeedMapperCommandHandler.LocateResult locateResult;
    private String resultText = "No result yet";
    private String statusText = "";
    private int statusColor = 0xFFAAAAAA;
    private Button locateButton;
    private Button copyButton;
    private Button addWaypointButton;
    private Button addHighlightButton;
    private Button doneButton;
    private Button clearFromButton;
    private EditBox fromXInput;
    private EditBox fromZInput;
    private EditBox lootQueryInput;
    private EditBox lootAmountInput;
    private int buttonY;
    private int fromY;
    private int resultY;
    private String lastLootFilter = "";

    public GuiSeedMapperLocator(Screen parent, Mode mode) {
        this.lastScreen = parent;
        this.mode = mode;
    }

    @Override
    public void init() {
        fromY = fromY();
        buttonY = actionsY();
        resultY = statusY();

        allQueries = switch (mode) {
            case STRUCTURE -> SeedMapperCommandHandler.getStructureQueries();
            case BIOME -> SeedMapperCommandHandler.getBiomeQueries();
            case LOOT -> getLootQueries();
        };

        if (mode == Mode.LOOT) {
            int left = listX();
            lootQueryInput = new EditBox(font, left, searchY(), 220, 20, Component.literal("Search item"));
            lootQueryInput.setMaxLength(128);
            lootQueryInput.setHint(Component.literal("Search item"));
            addRenderableWidget(lootQueryInput);

            lootAmountInput = new EditBox(font, left + 228, searchY(), 72, 20, Component.literal("1"));
            lootAmountInput.setMaxLength(3);
            lootAmountInput.setValue("1");
            lootAmountInput.setHint(Component.literal("Amount"));
            addRenderableWidget(lootAmountInput);
        }

        if (mode == Mode.LOOT) {
            visiblePrimaryQueries = List.of();
        }
        rebuildList(mode == Mode.LOOT ? visiblePrimaryQueries : allQueries);
        if (!(mode == Mode.LOOT ? visiblePrimaryQueries : allQueries).isEmpty()) {
            setSelectedQuery((mode == Mode.LOOT ? visiblePrimaryQueries : allQueries).getFirst());
            if (mode == Mode.LOOT) {
                rebuildVariantListForSelectedItem();
            }
        }

        int left = listX();
        int buttonGap = 6;
        int locateWidth = 60;
        int copyWidth = 52;
        int waypointWidth = 82;
        int highlightWidth = 88;

        locateButton = addRenderableWidget(Button.builder(Component.literal("Locate"), button -> locateSelected())
                .bounds(left, buttonY, locateWidth, 20)
                .build());
        copyButton = addRenderableWidget(Button.builder(Component.literal("Copy"), button -> copyResult())
                .bounds(left + locateWidth + buttonGap, buttonY, copyWidth, 20)
                .build());
        addWaypointButton = addRenderableWidget(Button.builder(Component.literal("Add Waypoint"), button -> addWaypoint())
                .bounds(left + locateWidth + buttonGap + copyWidth + buttonGap, buttonY, waypointWidth, 20)
                .build());
        addHighlightButton = addRenderableWidget(Button.builder(Component.literal("Add Highlight"), button -> addHighlight())
                .bounds(left + locateWidth + buttonGap + copyWidth + buttonGap + waypointWidth + buttonGap, buttonY, highlightWidth, 20)
                .build());

        fromXInput = new EditBox(font, left + 138, fromY, 54, 20, Component.literal("X"));
        fromXInput.setMaxLength(12);
        fromXInput.setHint(Component.literal("X"));
        addRenderableWidget(fromXInput);
        fromZInput = new EditBox(font, left + 198, fromY, 54, 20, Component.literal("Z"));
        fromZInput.setMaxLength(12);
        fromZInput.setHint(Component.literal("Z"));
        addRenderableWidget(fromZInput);
        clearFromButton = addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            fromXInput.setValue("");
            fromZInput.setValue("");
        }).bounds(left + 258, fromY, 42, 20).build());

        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left, doneY(), 300, 20)
                .build());

        updateButtons();

        if (mode == Mode.LOOT) {
            loadLootQueriesAsync();
        }
    }

    private void setSelectedQuery(String query) {
        selectedQuery = query == null ? "" : query;
        if (mode != Mode.LOOT) {
            selectedVariantQuery = "";
        }
        statusText = "";
        statusColor = 0xFFAAAAAA;
        updateButtons();
    }

    private void setSelectedVariantQuery(String query) {
        selectedVariantQuery = query == null ? "" : query;
        statusText = "";
        statusColor = 0xFFAAAAAA;
        updateButtons();
    }

    private void locateSelected() {
        String lootQuery = getActiveLootQuery();
        if ((mode == Mode.LOOT && lootQuery.isBlank()) || (mode != Mode.LOOT && selectedQuery.isBlank())) {
            return;
        }

        Integer fromX = parseOptionalInt(fromXInput.getValue());
        Integer fromZ = parseOptionalInt(fromZInput.getValue());
        locateResult = switch (mode) {
            case STRUCTURE -> SeedMapperCommandHandler.locateStructureByQuery(selectedQuery, 32768, fromX, fromZ);
            case BIOME -> SeedMapperCommandHandler.locateBiomeByQuery(selectedQuery, 8192, fromX, fromZ);
            case LOOT -> SeedMapperCommandHandler.locateLootByQuery(lootQuery, parseLootAmount(), 32768, fromX, fromZ);
        };

        if (locateResult == null) {
            resultText = "No result found";
            statusText = "";
            updateButtons();
            return;
        }

        resultText = formatLocateResult(locateResult);
        statusText = formatLocateDetail(locateResult);
        statusColor = 0xFF8FE38F;
        updateButtons();
    }

    private String getActiveLootQuery() {
        if (mode != Mode.LOOT || lootQueryInput == null) {
            return selectedQuery == null ? "" : selectedQuery.trim().toLowerCase(Locale.ROOT);
        }
        if (!selectedVariantQuery.isBlank()) {
            return selectedVariantQuery.trim().toLowerCase(Locale.ROOT);
        }
        if (!selectedQuery.isBlank()) {
            return selectedQuery.trim().toLowerCase(Locale.ROOT);
        }
        String typed = lootQueryInput.getValue() == null ? "" : lootQueryInput.getValue().trim().toLowerCase(Locale.ROOT);
        return typed;
    }

    private String formatLocateResult(SeedMapperCommandHandler.LocateResult result) {
        Integer fromX = parseOptionalInt(fromXInput.getValue());
        Integer fromZ = parseOptionalInt(fromZInput.getValue());
        int px = fromX != null ? fromX : GameVariableAccessShim.xCoord();
        int pz = fromZ != null ? fromZ : GameVariableAccessShim.zCoord();
        int dx = result.x() - px;
        int dz = result.z() - pz;
        int distance = (int)Math.round(Math.sqrt((double)dx * dx + (double)dz * dz));
        if (mode == Mode.LOOT && result.hasLootSummary()) {
            return "Found " + result.lootCount() + " of " + result.lootItem() + " in " + result.lootStructure();
        }
        return "Found Coords: " + result.x() + ", 0, " + result.z() + " | Distance: " + distance + " blocks " + direction(dx, dz);
    }

    private String formatLocateDetail(SeedMapperCommandHandler.LocateResult result) {
        if (mode != Mode.LOOT || !result.hasLootSummary()) {
            return "";
        }
        Integer fromX = parseOptionalInt(fromXInput.getValue());
        Integer fromZ = parseOptionalInt(fromZInput.getValue());
        int px = fromX != null ? fromX : GameVariableAccessShim.xCoord();
        int pz = fromZ != null ? fromZ : GameVariableAccessShim.zCoord();
        int dx = result.x() - px;
        int dz = result.z() - pz;
        int distance = (int)Math.round(Math.sqrt((double)dx * dx + (double)dz * dz));
        return "Coords: " + result.x() + ", 0, " + result.z() + " | Distance: " + distance + " blocks " + direction(dx, dz);
    }

    private String direction(int dx, int dz) {
        if (dx == 0 && dz == 0) return "HERE";
        String northSouth = dz < 0 ? "NORTH" : dz > 0 ? "SOUTH" : "";
        String eastWest = dx > 0 ? "EAST" : dx < 0 ? "WEST" : "";
        if (!northSouth.isEmpty() && !eastWest.isEmpty()) {
            return northSouth + "-" + eastWest;
        }
        return !northSouth.isEmpty() ? northSouth : eastWest;
    }

    private void copyResult() {
        if (locateResult == null) return;
        String copied = locateResult.x() + ", 0, " + locateResult.z();
        minecraft.keyboardHandler.setClipboard(copied);
        statusText = "Copied " + copied;
        statusColor = 0xFF8FE38F;
    }

    private void addWaypoint() {
        if (locateResult == null) return;

        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }

        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(level));
        String name = getResultLabel();
        Waypoint waypoint = new Waypoint(
                name,
                locateResult.x(),
                locateResult.z(),
                Math.max(GameVariableAccessShim.yCoord(), 64),
                true,
                0.20F,
                0.85F,
                1.0F,
                mode == Mode.BIOME ? "world" : "temple",
                VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false),
                dimensions
        );
        VoxelConstants.getVoxelMapInstance().getWaypointManager().addWaypoint(waypoint);
        statusText = "Added waypoint for " + name;
        statusColor = 0xFF8FE38F;
    }

    private void addHighlight() {
        if (locateResult == null) return;

        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }

        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(level));
        String name = getResultLabel();
        Waypoint waypoint = new Waypoint(
                name,
                locateResult.x(),
                locateResult.z(),
                Math.max(GameVariableAccessShim.yCoord(), 64),
                true,
                1.0F,
                0.85F,
                0.1F,
                "target",
                VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false),
                dimensions
        );
        VoxelConstants.getVoxelMapInstance().getWaypointManager().setHighlightedWaypoint(waypoint, false);
        statusText = "Added highlight for " + name;
        statusColor = 0xFF8FE38F;
    }

    private String getResultLabel() {
        return switch (mode) {
            case STRUCTURE -> displayStructureName(selectedQuery);
            case BIOME -> "Biome: " + selectedQuery;
            case LOOT -> "Loot: " + selectedQuery;
        };
    }

    private String displayStructureName(String query) {
        for (SeedMapperFeature feature : SeedMapperFeature.values()) {
            if (feature.id().equalsIgnoreCase(query)) {
                return Component.translatable(feature.translationKey()).getString();
            }
        }
        return query;
    }

    private void updateButtons() {
        boolean hasSelection = mode == Mode.LOOT ? !getActiveLootQuery().isBlank() : !selectedQuery.isBlank();
        boolean hasResult = locateResult != null;
        if (locateButton != null) locateButton.active = hasSelection;
        if (copyButton != null) copyButton.active = hasResult;
        if (addWaypointButton != null) addWaypointButton.active = hasResult;
        if (addHighlightButton != null) addHighlightButton.active = hasResult;
    }

    @Override
    public void tick() {
        super.tick();
        if (mode != Mode.LOOT || lootQueryInput == null) return;

        String filter = lootQueryInput.getValue().trim().toLowerCase(Locale.ROOT);
        if (!filter.equals(lastLootFilter)) {
            lastLootFilter = filter;
            if (lootQueriesLoaded) {
                applyLootFilter(filter);
            }
        }
    }

    private void loadLootQueriesAsync() {
        if (lootQueriesLoading || lootQueriesLoaded) {
            return;
        }
        lootQueriesLoading = true;
        allQueries = List.of();
        visiblePrimaryQueries = List.of();
        rebuildList(List.of());
        clearVariantList();
        setSelectedQuery("");
        statusText = "Loading loot options...";
        statusColor = 0xFFAAAAAA;

        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            lootQueriesLoading = false;
            lootQueriesLoaded = true;
            statusText = "No world loaded.";
            return;
        }

        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        long seed;
        try {
            seed = settings.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException ignored) {
            lootQueriesLoading = false;
            lootQueriesLoaded = true;
            statusText = "No seed available.";
            return;
        }

        final long resolvedSeed = seed;
        final int resolvedDimension = dimension;
        final int resolvedMcVersion = SeedMapperCompat.getMcVersion();
        Thread loader = new Thread(() -> {
            List<String> queries = SeedMapperLootService.getLootSearchSuggestions(resolvedDimension, resolvedMcVersion, resolvedSeed);
            minecraft.execute(() -> applyLootQueriesLoaded(queries));
        }, "VoxelMap-LootLocator-Loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void applyLootQueriesLoaded(List<String> queries) {
        lootQueriesLoading = false;
        lootQueriesLoaded = true;
        allQueries = queries == null ? List.of() : List.copyOf(queries);
        applyLootFilter(lootQueryInput == null ? "" : lootQueryInput.getValue().trim().toLowerCase(Locale.ROOT));
        statusText = allQueries.isEmpty() ? "No loot options found." : "";
        statusColor = 0xFFAAAAAA;
        updateButtons();
    }

    private void applyLootFilter(String filter) {
        visiblePrimaryQueries = allQueries.stream()
                .filter(q -> filter == null || filter.isBlank() || q.contains(filter))
                .toList();
        rebuildList(visiblePrimaryQueries);
        clearVariantList();
        if (!visiblePrimaryQueries.contains(selectedQuery)) {
            setSelectedQuery(visiblePrimaryQueries.isEmpty() ? "" : visiblePrimaryQueries.getFirst());
        }
        if (!selectedQuery.isBlank()) {
            rebuildVariantListForSelectedItem();
        }
    }

    private void rebuildVariantListForSelectedItem() {
        if (mode != Mode.LOOT) {
            return;
        }
        if (variantList != null) {
            removeWidget(variantList);
            variantList = null;
        }
        selectedVariantQuery = "";
        if (selectedQuery.isBlank()) {
            return;
        }
        List<String> enchants = SeedMapperLootService.getEnchantSearchSuggestionsStrict(selectedQuery);
        if (enchants.isEmpty()) {
            resizePrimaryListForVariantVisibility();
            return;
        }
        List<String> variants = new ArrayList<>();
        variants.add(selectedQuery + " (no enchant filter)");
        for (String enchant : enchants) {
            variants.add(selectedQuery + " with " + enchant + " *");
        }
        int top = variantY();
        int bottom = top + variantHeight();
        variantList = new VariantList(this, variants, top, bottom);
        addRenderableWidget(variantList);
        setSelectedVariantQuery(variants.getFirst());
        resizePrimaryListForVariantVisibility();
    }

    private void clearVariantList() {
        if (variantList != null) {
            removeWidget(variantList);
            variantList = null;
        }
        selectedVariantQuery = "";
        resizePrimaryListForVariantVisibility();
    }

    private void resizePrimaryListForVariantVisibility() {
        if (list == null) {
            return;
        }
        list.setX(listX());
        list.setY(listY());
        list.setWidth(listWidth());
        list.setHeight(listHeight());
        if (variantList != null) {
            variantList.setX(listX());
            variantList.setY(variantY());
            variantList.setWidth(variantWidth());
            variantList.setHeight(variantHeight());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xAA000000);
        graphics.centeredText(getFont(), Component.literal(mode.title), width / 2, titleY(), 0xFFFFFFFF);
        String selectedText = selectedQuery;
        if (mode == Mode.LOOT) {
            if (!selectedVariantQuery.isBlank()) {
                selectedText = selectedVariantQuery;
            } else if (lootQueryInput != null) {
                String current = lootQueryInput.getValue() == null ? "" : lootQueryInput.getValue().trim();
                if (!current.isBlank()) {
                    selectedText = current;
                }
            }
        }
        graphics.centeredText(getFont(), Component.literal("Selected: " + (selectedText.isBlank() ? "-" : selectedText)), width / 2, selectedY(), 0xFFFFFFFF);
        if (mode == Mode.LOOT && variantList != null) {
            graphics.text(getFont(), Component.literal("Variant (item / item+enchant):"), listX(), variantLabelY(), 0xFFFFFFFF);
        }
        graphics.fill(listX(), listY(), listX() + listWidth(), listY() + listHeight(), 0xCC000000);
        if (mode == Mode.LOOT && variantList != null) {
            graphics.fill(listX(), variantY(), listX() + variantWidth(), variantY() + variantHeight(), 0xCC000000);
        }
        graphics.centeredText(getFont(), Component.literal(resultText), width / 2, resultY, 0xFFFFFFFF);
        graphics.text(getFont(), Component.literal("From X/Z (optional):"), listX(), fromY + 6, 0xFFFFFFFF);
        if (!statusText.isBlank()) {
            graphics.centeredText(getFont(), Component.literal(statusText), width / 2, resultY + 12, statusColor);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private void rebuildList(List<String> queries) {
        if (list != null) {
            removeWidget(list);
        }
        list = new LocateList(this, queries, listX(), listY(), listWidth(), listHeight(), rowHeight());
        addRenderableWidget(list);
    }

    private List<String> getLootQueries() {
        return List.of();
    }

    private int getCurrentCubiomesDimension() {
        var level = GameVariableAccessShim.getWorld();
        if (level == null) return Integer.MIN_VALUE;
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) return Cubiomes.DIM_NETHER();
        if (level.dimension() == net.minecraft.world.level.Level.END) return Cubiomes.DIM_END();
        return Cubiomes.DIM_OVERWORLD();
    }

    private int parseLootAmount() {
        if (lootAmountInput == null) return 1;
        try {
            return Math.max(1, Integer.parseInt(lootAmountInput.getValue().trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private Integer parseOptionalInt(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int panelTop() { return mode == Mode.LOOT ? height / 2 - 170 : height / 2 - 144; }
    private int titleY() { return panelTop(); }
    private int selectedY() { return panelTop() + (mode == Mode.LOOT ? 52 : 18); }
    private int searchY() { return panelTop() + 30; }
    private int listX() { return width / 2 - 150; }
    private int listY() { return panelTop() + (mode == Mode.LOOT ? 64 : 30); }
    private int listWidth() { return LIST_WIDTH; }
    private int rowHeight() { return LIST_ROW_HEIGHT; }
    private int visibleRows() { return mode == Mode.LOOT ? 6 : 8; }
    private int listHeight() { return rowHeight() * visibleRows(); }
    private int variantLabelY() { return listY() + listHeight() + 8; }
    private int variantY() { return variantLabelY() + 10; }
    private int variantWidth() { return listWidth(); }
    private int variantHeight() { return VARIANT_ROW_HEIGHT * VARIANT_VISIBLE_ROWS; }
    private int statusY() { return mode == Mode.LOOT ? variantY() + variantHeight() + 12 : listY() + listHeight() + 20; }
    private int fromY() { return statusY() + 34; }
    private int actionsY() { return fromY() + 28; }
    private int doneY() { return actionsY() + 24; }

    private static class LocateList extends AbstractSelectionList<LocateList.QueryItem> {
        private final GuiSeedMapperLocator parent;

        LocateList(GuiSeedMapperLocator parent, List<String> queries, int x, int top, int width, int height, int itemHeight) {
            super(VoxelConstants.getMinecraft(), width, height, top, itemHeight);
            this.parent = parent;
            setX(x);
            for (String query : queries) {
                addEntry(new QueryItem(this, query));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, parent.listWidth() - 4);
        }

        @Override
        public void setSelected(QueryItem entry) {
            super.setSelected(entry);
            if (entry != null) {
                parent.setSelectedQuery(entry.query);
                new GameNarrator(VoxelConstants.getMinecraft()).sayChatQueued(Component.translatable("narrator.select", entry.query));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }

        private static class QueryItem extends AbstractSelectionList.Entry<QueryItem> {
            private final LocateList parent;
            private final String query;

            QueryItem(LocateList parent, String query) {
                this.parent = parent;
                this.query = query;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int color = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
                graphics.text(VoxelConstants.getMinecraft().font, query, parent.getX() + 8, getY() + 6, color);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
                if (mouseButtonEvent.y() < getY() || mouseButtonEvent.y() > getY() + getHeight()) return false;
                parent.parent.onPrimaryQueryChosen(query, doubleClick);
                parent.setSelected(this);
                return true;
            }
        }
    }

    private void onPrimaryQueryChosen(String query, boolean doubleClick) {
        setSelectedQuery(query);
        if (mode == Mode.LOOT && lootQueryInput != null) {
            lootQueryInput.setValue(query);
            lastLootFilter = query;
            clearVariantList();
            rebuildVariantListForSelectedItem();
        }
        if (doubleClick) {
            locateSelected();
        }
    }

    private void onVariantQueryChosen(String query, boolean doubleClick) {
        if (query.endsWith(" (no enchant filter)")) {
            setSelectedVariantQuery(selectedQuery);
        } else if (query.endsWith(" *")) {
            setSelectedVariantQuery(query.substring(0, query.length() - 2));
        } else {
            setSelectedVariantQuery(query);
        }
        resizePrimaryListForVariantVisibility();
        if (doubleClick) {
            locateSelected();
        }
    }

    private static class VariantList extends AbstractSelectionList<VariantList.VariantItem> {
        private final GuiSeedMapperLocator parent;

        VariantList(GuiSeedMapperLocator parent, List<String> queries, int top, int bottom) {
            super(VoxelConstants.getMinecraft(), parent.variantWidth(), Math.max(24, bottom - top), top, VARIANT_ROW_HEIGHT);
            this.parent = parent;
            setX(parent.listX());
            for (String query : queries) {
                addEntry(new VariantItem(this, query));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, parent.variantWidth() - 4);
        }

        @Override
        public void setSelected(VariantItem entry) {
            super.setSelected(entry);
            if (entry != null) {
                parent.onVariantQueryChosen(entry.query, false);
                new GameNarrator(VoxelConstants.getMinecraft()).sayChatQueued(Component.translatable("narrator.select", entry.query));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }

        private static class VariantItem extends AbstractSelectionList.Entry<VariantItem> {
            private final VariantList parent;
            private final String query;

            VariantItem(VariantList parent, String query) {
                this.parent = parent;
                this.query = query;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int color = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
                graphics.text(VoxelConstants.getMinecraft().font, query, parent.getX() + 4, getY() + 3, color);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
                if (mouseButtonEvent.y() < getY() || mouseButtonEvent.y() > getY() + getHeight()) return false;
                parent.setSelected(this);
                parent.parent.onVariantQueryChosen(query, doubleClick);
                return true;
            }
        }
    }
}
