package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.ChunkRailCache;
import net.phoenixvine.solaris.client.color.PersistentCaveStore;
import net.phoenixvine.solaris.client.export.SolarisWebExporter;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.ping.PingMarker;
import net.phoenixvine.solaris.client.ping.PingTracker;
import net.phoenixvine.solaris.client.plan.PlanShape;
import net.phoenixvine.solaris.client.plan.PlanShapeManager;
import net.phoenixvine.solaris.client.plan.QuickPlanShapeScreen;
import net.phoenixvine.solaris.client.player.RemotePlayerTracker;
import net.phoenixvine.solaris.client.render.CaveTileCache;
import net.phoenixvine.solaris.client.render.LabelSide;
import net.phoenixvine.solaris.client.render.LineRenderer;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.MapViewport;
import net.phoenixvine.solaris.client.render.MinimapShape;
import net.phoenixvine.solaris.client.render.MobFaceIcons;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.client.render.SmoothShapes;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.UnexploredImageStyle;
import net.phoenixvine.solaris.client.render.UnexploredStyle;
import net.phoenixvine.solaris.client.render.globe.GlobeCamera;
import net.phoenixvine.solaris.client.render.globe.SolarisGlobeRenderer;
import net.phoenixvine.solaris.client.render.globe.SphereMesh;
import net.phoenixvine.solaris.client.waypoint.QuickWaypointScreen;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointIconManager;
import net.phoenixvine.solaris.client.waypoint.WaypointListScreen;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.client.waypoint.WaypointPlacement;
import net.phoenixvine.solaris.common.SharedExplorationClient;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;
import net.phoenixvine.wiki.PhoenixWikiAPI;
import net.phoenixvine.wiki.client.screen.WikiTheme;
import net.phoenixvine.wiki.theme.PhoenixTheme;
import net.phoenixvine.wiki.theme.PhoenixThemeEditorScreen;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BG;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_TEXT;

@OnlyIn(Dist.CLIENT)
public class SolarisMapScreen extends Screen {

    private static final int MARGIN = 20;

    private static final int MAX_TILE_BUILDS_PER_FRAME = 300;

    private static final int PANEL_INSET = MARGIN - 4;
    private static final int BORDER_PAD = 4;

    private static final int BUTTON_MARGIN = 14;
    private static final int IDEAL_BUTTON_R = 9;
    private static final int IDEAL_BUTTON_GAP = 4;
    private static final int LEFT_BUTTON_GROUP = 4;

    private static SolarisTexture texture;

    private enum ViewMode {
        FLAT,
        GLOBE
    }

    private enum ToolMode {
        NAVIGATE,
        DRAW_RECTANGLE,
        DRAW_CIRCLE,
        DRAW_LINE
    }

    private final MapViewport viewport = new MapViewport(
            SolarisConfig.ZOOM_MIN.get().floatValue(), SolarisConfig.ZOOM_MAX.get().floatValue());
    private final List<IconButton> iconButtons = new ArrayList<>();
    private ViewMode mode = ViewMode.FLAT;
    private boolean undergroundView = false;
    private GlobeCamera globeCamera;
    private final SphereMesh globeMesh = new SphereMesh();
    private boolean dragging = false;
    private long lastDragRebuildMillis = 0L;
    private ToolMode toolMode = ToolMode.NAVIGATE;

    private boolean shapeDragging = false;

    private int[] drawAnchor;
    private int[] drawCurrent;

    private final List<int[]> lineDrawPoints = new ArrayList<>();
    private boolean hasPlayerMarker = false;

    private static final long WORLD_MARKER_REFRESH_MS = 150L;
    private long lastWorldMarkerRefreshMillis = 0L;
    private final List<Mob> cachedMobs = new ArrayList<>();
    private final List<GtceuIntegration.GtOreVein> cachedVeins = new ArrayList<>();
    private int anchorChunkX;
    private int anchorChunkZ;
    private int radiusPixels;
    private ChunkKey centerKey;
    private String hoveredVeinName;
    private String hoveredPlayerName;
    private String hoveredMobName;

    private int buttonR = IDEAL_BUTTON_R;

    private static boolean gtceuBroken = false;
    private final Screen parent;

    private boolean viewInitialized = false;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private double lastFrameCenterX;
    private double lastFrameCenterY;

    public SolarisMapScreen() {
        this(null);
    }

    public SolarisMapScreen(Screen parent) {
        super(Component.translatable("solaris.map.title"));
        this.parent = parent;
    }

    private static SolarisTexture texture() {
        if (texture == null) texture = new SolarisTexture("map", SolarisConfig.MAP_RADIUS_CHUNKS.get());
        return texture;
    }

    private int frameLeft() {
        return MARGIN;
    }

    private int frameTop() {
        return MARGIN;
    }

    private int frameRight() {
        return width - MARGIN;
    }

    private int frameBottom() {
        return panelBottom() - BORDER_PAD;
    }

    private int panelLeft() {
        return PANEL_INSET;
    }

    private int panelTop() {
        return PANEL_INSET;
    }

    private int panelRight() {
        return width - PANEL_INSET;
    }

    private int panelBottom() {
        return height - PANEL_INSET;
    }

    private int globeCx() {
        return (int) frameCenterX();
    }

    private int globeCy() {
        return (int) frameCenterY();
    }

    private double frameCenterX() {
        return frameLeft() + (frameRight() - frameLeft()) / 2.0;
    }

    private double frameCenterY() {
        return frameTop() + (frameBottom() - frameTop()) / 2.0;
    }

    private boolean onMap(double mx, double my) {
        return mx >= frameLeft() && mx <= frameRight() && my >= frameTop() && my <= frameBottom();
    }

    private void centerViewOn(double worldX, double worldZ) {
        viewport.setOffset(frameCenterX() - worldX * viewport.getZoom(),
                frameCenterY() - worldZ * viewport.getZoom());
    }

    @FunctionalInterface
    private interface IconDrawer {

        void draw(GuiGraphics g, int cx, int cy, boolean hover);
    }

    private record IconButton(int cx, int cy, int r, String label, IconDrawer draw, Runnable action) {

        boolean hit(double mx, double my) {
            double dx = mx - cx;
            double dy = my - cy;
            return dx * dx + dy * dy <= (double) r * r;
        }
    }

    private record IconSpec(String label, IconDrawer draw, Runnable action) {}

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        SolarisTexture tex = texture();

        boolean sizeChanged = width != lastWidth || height != lastHeight;

        boolean keepCenter = viewInitialized && sizeChanged;
        double keepWorldX = keepCenter ? viewport.toWorldX(lastFrameCenterX, 0) : 0;
        double keepWorldZ = keepCenter ? viewport.toWorldZ(lastFrameCenterY, 0) : 0;

        buildIconButtons();

        if (globeCamera == null || sizeChanged) {
            int span = Math.min(frameRight() - frameLeft(), frameBottom() - frameTop());
            float frameFitScale = span > 0 ? span / 2f : 100f;
            globeCamera = new GlobeCamera(frameFitScale * 0.4f, frameFitScale * 5f);
        }

        syncZoomLimits();

        radiusPixels = tex.getRadiusChunks() * 16;

        if (keepCenter) centerViewOn(keepWorldX, keepWorldZ);

        if (mc.player != null && mc.level != null) {
            hasPlayerMarker = true;

            if (!viewInitialized) {
                BlockPos blockPos = mc.player.blockPosition();
                anchorChunkX = blockPos.getX() >> 4;
                anchorChunkZ = blockPos.getZ() >> 4;
                centerKey = ChunkKey.of(mc.level, new ChunkPos(anchorChunkX, anchorChunkZ));

                undergroundView = DimensionModes.isCaveDimension(mc.level);

                centerViewOn(mc.player.getX(), mc.player.getZ());
                viewInitialized = true;
            }
        }

        lastWidth = width;
        lastHeight = height;
        lastFrameCenterX = frameCenterX();
        lastFrameCenterY = frameCenterY();
    }

    private static final float UNLIMITED_ZOOM_MIN = 0.01f;
    private static final float UNLIMITED_ZOOM_MAX = 1000f;

    private void syncZoomLimits() {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation dimension = mc.level != null ? mc.level.dimension().location() : null;
        boolean limitsEnabled = dimension == null ||
                SolarisAPI.getFeatureState(SolarisAPI.FEATURE_ZOOM_LIMITS, dimension)
                        .atLeast(SolarisFeatureState.VISIBLE);
        if (limitsEnabled) {
            viewport.setZoomBounds(SolarisConfig.ZOOM_MIN.get().floatValue(),
                    SolarisConfig.ZOOM_MAX.get().floatValue());
        } else {
            viewport.setZoomBounds(UNLIMITED_ZOOM_MIN, UNLIMITED_ZOOM_MAX);
        }
    }

    private static final ItemStack WAYPOINTS_ICON = new ItemStack(Items.FILLED_MAP);
    private static final ItemStack THEME_ICON = new ItemStack(Items.PAINTING);
    private static final ItemStack SETTINGS_ICON = new ItemStack(Items.COMPARATOR);
    private static final ItemStack PLAN_ICON = new ItemStack(Items.SCAFFOLDING);
    private static final ItemStack SHAPES_ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack EXPORT_ICON = new ItemStack(Items.PAPER);
    private static final ItemStack WEB_EXPORT_ICON = new ItemStack(Items.MAP);
    private static final ItemStack GOTO_ICON = new ItemStack(Items.COMPASS);
    private static final ItemStack MOBS_ICON = new ItemStack(Items.ZOMBIE_SPAWN_EGG);
    private static final ItemStack UNDERGROUND_ICON = new ItemStack(Items.LADDER);
    private static final ItemStack WIKI_ICON = new ItemStack(Items.BOOK);

    private void openWiki() {
        if (minecraft == null) return;
        PhoenixTheme t = PhoenixTheme.current();
        WikiTheme wikiTheme = new WikiTheme(
                t.bg.getColor(), t.panel.getColor(), t.header.getColor(), t.border.getColor(),
                t.accent.getColor(), t.text.getColor(), t.textDim.getColor(), t.textFaint.getColor(),
                t.done.getColor(), t.activeColor.getColor());
        PhoenixWikiAPI.open(this, "solaris", "wiki", wikiTheme);
    }

    private void buildIconButtons() {
        iconButtons.clear();

        List<IconSpec> specs = new ArrayList<>();

        specs.add(new IconSpec("Settings", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, SETTINGS_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_SETTINGS_MENU,
                        () -> Minecraft.getInstance().setScreen(new SolarisDisplaySettingsScreen(this)))));
        specs.add(new IconSpec("Export as PNG", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, EXPORT_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_PNG_EXPORT,
                        () -> texture().exportToPng(SolarisMapScreen::sendMapMessage))));
        specs.add(new IconSpec("Export for Web Map", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, WEB_EXPORT_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_WEB_EXPORT,
                        () -> SolarisWebExporter.exportCurrentDimension(SolarisMapScreen::sendMapMessage))));
        specs.add(new IconSpec("Go to Coordinate", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, GOTO_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_GOTO_COORDINATE,
                        () -> Minecraft.getInstance().setScreen(new QuickGotoScreen(this)))));

        specs.add(new IconSpec("Plan", (g, cx, cy, hover) -> {
            if (toolMode != ToolMode.NAVIGATE) SmoothShapes.drawRing(g, cx, cy, buttonR - 1, C_ACCENT);
            drawItemIcon(g, cx, cy, PLAN_ICON);
        }, () -> runIfEnabled(SolarisAPI.FEATURE_SHAPE_PLANNER, () -> {
            if (toolMode != ToolMode.NAVIGATE) {
                cancelDrawing();
            } else {
                mode = ViewMode.FLAT;
                switchDrawTool(ToolMode.DRAW_RECTANGLE);
            }
        })));
        specs.add(new IconSpec("Shapes", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, SHAPES_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_SHAPE_PLANNER,
                        () -> Minecraft.getInstance().setScreen(new WaypointListScreen(this, true)))));
        specs.add(new IconSpec("Unexplored Style",
                (g, cx, cy, hover) -> drawUnexploredStyleIcon(g, cx, cy, SolarisConfig.UNEXPLORED_STYLE.get()),
                () -> runIfEnabled(SolarisAPI.FEATURE_UNEXPLORED_STYLE, () -> {
                    SolarisConfig.UNEXPLORED_STYLE.set(SolarisConfig.UNEXPLORED_STYLE.get().next());
                    SolarisConfig.UNEXPLORED_STYLE.save();
                    MapTileCache.clearAll();
                    CaveTileCache.clearAll();
                })));
        specs.add(new IconSpec("Black & White",
                (g, cx, cy, hover) -> drawBlackAndWhiteIcon(g, cx, cy, SolarisConfig.BLACK_AND_WHITE.get()),
                () -> runIfEnabled(SolarisAPI.FEATURE_BLACK_AND_WHITE, () -> {
                    boolean on = !SolarisConfig.BLACK_AND_WHITE.get();
                    SolarisConfig.BLACK_AND_WHITE.set(on);
                    SolarisConfig.BLACK_AND_WHITE.save();
                    SolarisTexture.invalidateAll();
                    MapTileCache.clearAll();
                    CaveTileCache.clearAll();
                })));
        specs.add(new IconSpec("Vignette",
                (g, cx, cy, hover) -> drawVignetteIcon(g, cx, cy, SolarisConfig.VIGNETTE.get()),
                () -> runIfEnabled(SolarisAPI.FEATURE_VIGNETTE, () -> {
                    boolean on = !SolarisConfig.VIGNETTE.get();
                    SolarisConfig.VIGNETTE.set(on);
                    SolarisConfig.VIGNETTE.save();
                    SolarisTexture.invalidateAll();
                    MapTileCache.clearAll();
                    CaveTileCache.clearAll();
                })));
        specs.add(new IconSpec("Show Chunk Grid",
                (g, cx, cy, hover) -> drawChunkGridIcon(g, cx, cy, SolarisConfig.SHOW_CHUNK_GRID.get()),
                () -> runIfEnabled(SolarisAPI.FEATURE_CHUNK_GRID, () -> {
                    boolean on = !SolarisConfig.SHOW_CHUNK_GRID.get();
                    SolarisConfig.SHOW_CHUNK_GRID.set(on);
                    SolarisConfig.SHOW_CHUNK_GRID.save();
                })));
        specs.add(new IconSpec("Show Mobs", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, MOBS_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_SHOW_MOBS, () -> {
                    boolean on = !SolarisConfig.SHOW_MOBS.get();
                    SolarisConfig.SHOW_MOBS.set(on);
                    SolarisConfig.SHOW_MOBS.save();
                })));
        specs.add(new IconSpec("Hillshading",
                (g, cx, cy, hover) -> drawHillshadingIcon(g, cx, cy, SolarisConfig.HILLSHADING.get()),
                () -> runIfEnabled(SolarisAPI.FEATURE_HILLSHADING, () -> {
                    boolean on = !SolarisConfig.HILLSHADING.get();
                    SolarisConfig.HILLSHADING.set(on);
                    SolarisConfig.HILLSHADING.save();
                    SolarisTexture.invalidateAll();
                    MapTileCache.clearAll();
                    CaveTileCache.clearAll();
                })));
        specs.add(new IconSpec("Underground View", (g, cx, cy, hover) -> {
            if (undergroundView) SmoothShapes.drawRing(g, cx, cy, buttonR - 1, C_ACCENT);
            drawItemIcon(g, cx, cy, UNDERGROUND_ICON);
        }, () -> runIfEnabled(SolarisAPI.FEATURE_UNDERGROUND_MAP, () -> undergroundView = !undergroundView)));

        if (SolarisConfig.GLOBE_VIEW_ENABLED.get()) {
            specs.add(new IconSpec("Globe View", (g, cx, cy, hover) -> drawGlobeIcon(g, cx, cy),
                    () -> runIfEnabled(SolarisAPI.FEATURE_GLOBE_VIEW, () -> {
                        if (mode == ViewMode.FLAT) cancelDrawing();
                        mode = mode == ViewMode.FLAT ? ViewMode.GLOBE : ViewMode.FLAT;
                    })));
        }

        specs.add(new IconSpec("Wiki", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, WIKI_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_WIKI, this::openWiki)));
        specs.add(new IconSpec("Theme", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, THEME_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_THEME_SELECT,
                        () -> Minecraft.getInstance().setScreen(new PhoenixThemeEditorScreen(this, "Solaris")))));
        specs.add(new IconSpec("Waypoints", (g, cx, cy, hover) -> drawItemIcon(g, cx, cy, WAYPOINTS_ICON),
                () -> runIfStateAtLeast(SolarisAPI.FEATURE_WAYPOINTS, SolarisFeatureState.VISIBLE,
                        () -> Minecraft.getInstance().setScreen(new WaypointListScreen(this)))));

        int buttonCount = specs.size();
        int leftCount = Math.min(LEFT_BUTTON_GROUP, buttonCount);
        int rightCount = buttonCount - leftCount;
        int availableWidth = width - 2 * BUTTON_MARGIN;

        int idealMinFootprint = groupFootprint(leftCount, IDEAL_BUTTON_R, IDEAL_BUTTON_GAP) +
                groupFootprint(rightCount, IDEAL_BUTTON_R, IDEAL_BUTTON_GAP);

        int buttonRadius = IDEAL_BUTTON_R;
        int buttonGapPx = IDEAL_BUTTON_GAP;
        if (idealMinFootprint > availableWidth) {
            float scale = availableWidth / (float) idealMinFootprint;
            buttonRadius = Math.max(4, Math.round(IDEAL_BUTTON_R * scale));
            buttonGapPx = Math.max(1, Math.round(IDEAL_BUTTON_GAP * scale));
        }
        this.buttonR = buttonRadius;

        int bottomY = height - BUTTON_MARGIN - buttonRadius;
        int stride = buttonRadius * 2 + buttonGapPx;
        int rightSpan = Math.max(0, rightCount - 1) * stride;

        int leftStartX = BUTTON_MARGIN + buttonRadius;
        int rightEndX = width - BUTTON_MARGIN - buttonRadius;
        int rightStartX = rightEndX - rightSpan;

        for (int i = 0; i < specs.size(); i++) {
            IconSpec spec = specs.get(i);
            int cx = i < leftCount ? leftStartX + i * stride : rightStartX + (i - leftCount) * stride;
            iconButtons.add(new IconButton(cx, bottomY, buttonRadius, spec.label(), spec.draw(), spec.action()));
        }
    }

    private static int groupFootprint(int count, int r, int gap) {
        if (count <= 0) return 0;
        return (count - 1) * (r * 2 + gap) + r * 2;
    }

    private static void runIfEnabled(String featureId, Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation dimension = mc.level != null ? mc.level.dimension().location() : null;
        if (dimension != null ? SolarisAPI.isFeatureEnabled(featureId, dimension) :
                SolarisAPI.isFeatureEnabled(featureId)) {
            action.run();
        } else {
            sendMapMessage(Component.literal("This feature isn't available right now."));
        }
    }

    private static void runIfStateAtLeast(String featureId, SolarisFeatureState minimum, Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (SolarisAPI.getFeatureState(featureId, mc.level.dimension().location()).atLeast(minimum)) {
            action.run();
        } else {
            sendMapMessage(Component.literal("This feature isn't available right now."));
        }
    }

    private static void sendMapMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(message, false);
        });
    }

    private void drawItemIcon(GuiGraphics g, int cx, int cy, ItemStack stack) {
        int size = buttonR * 2 - 2;
        g.pose().pushPose();
        g.pose().translate(cx - size / 2.0, cy - size / 2.0, 0);
        g.pose().scale(size / 16f, size / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static void drawVeinMarker(GuiGraphics g, GtceuIntegration.GtOreVein vein, int vx, int vy, int iconR) {
        if (vein.icon().isEmpty()) {
            WaypointIconManager.draw(g, "STAR", vx, vy, iconR, vein.colorArgb());
            return;
        }
        SmoothShapes.drawCircle(g, vx, vy, iconR + 2, 0xE0000000);
        int itemSize = Math.max(8, iconR * 2);
        g.pose().pushPose();
        g.pose().translate(vx - itemSize / 2.0, vy - itemSize / 2.0, 0);
        g.pose().scale(itemSize / 16f, itemSize / 16f, 1f);
        g.renderItem(vein.icon(), 0, 0);
        g.pose().popPose();
    }

    private static final int MOB_ICON_COLOR_HOSTILE = 0xFFCC4444;
    private static final int MOB_ICON_COLOR_PASSIVE = 0xFF55AA55;

    private static void drawMobIcon(GuiGraphics g, Mob mob, int x, int y, int r) {
        int color = mob instanceof Enemy ? MOB_ICON_COLOR_HOSTILE : MOB_ICON_COLOR_PASSIVE;
        if (MobFaceIcons.isSupported(mob.getType())) {
            PlayerArrow.drawMob(g, mob, x, y, r, color);
        } else {
            PlayerArrow.drawFlatSquare(g, x, y, r, color);
        }
    }

    private void drawGlobeIcon(GuiGraphics g, int cx, int cy) {
        int r = buttonR - 2;
        SmoothShapes.drawRing(g, cx, cy, r, C_TEXT);
        g.fill(cx - r, cy, cx + r, cy + 1, C_TEXT);
        g.fill(cx, cy - r, cx + 1, cy + r, C_TEXT);
    }

    private void drawHillshadingIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = buttonR - 2;
        g.fill(cx - r, cy - r, cx, cy + r, on ? 0xFFF0F0F0 : 0xFF808080);
        g.fill(cx, cy - r, cx + r, cy + r, on ? 0xFF202020 : 0xFF606060);
    }

    private void drawChunkGridIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = buttonR - 2;
        int color = on ? C_ACCENT : C_TEXT;
        SmoothShapes.drawRing(g, cx, cy, r, color);
        int inset = Math.max(1, r * 2 / 3);
        g.fill(cx - inset, cy, cx + inset, cy + 1, color);
        g.fill(cx, cy - inset, cx + 1, cy + inset, color);
    }

    private void drawVignetteIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = buttonR - 2;
        int ringColor = on ? 0xFF000000 : C_TEXT;
        int centerColor = on ? C_ACCENT : C_TEXT;
        SmoothShapes.drawRing(g, cx, cy, r, ringColor);
        SmoothShapes.drawCircle(g, cx, cy, Math.max(1, r / 2), centerColor);
    }

    private void drawBlackAndWhiteIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = buttonR - 2;
        g.fill(cx - r, cy - r, cx, cy + r, 0xFFFFFFFF);
        g.fill(cx, cy - r, cx + r, cy + r, 0xFF000000);
    }

    private void drawUnexploredStyleIcon(GuiGraphics g, int cx, int cy, UnexploredStyle style) {
        int r = buttonR - 2;
        switch (style) {
            case STARFIELD -> {
                g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF0A0A18);
                g.fill(cx - r + 2, cy - r + 2, cx - r + 3, cy - r + 3, 0xFFFFFFFF);
                g.fill(cx + 1, cy - 2, cx + 2, cy - 1, 0xFFC8D0FF);
                g.fill(cx - 3, cy + 2, cx - 2, cy + 3, 0xFFFFFFFF);
                g.fill(cx + r - 3, cy + r - 3, cx + r - 2, cy + r - 2, 0xFFA0A8E0);
            }
            case PHOENIX -> {
                g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF200804);
                g.fill(cx - r + 2, cy - r + 2, cx - r + 4, cy - r + 4, 0xFFFF8020);
                g.fill(cx, cy - 2, cx + 2, cy, 0xFFFFC060);
                g.fill(cx - 3, cy + 1, cx - 1, cy + 3, 0xFFFF6010);
                g.fill(cx + r - 3, cy + r - 3, cx + r - 1, cy + r - 1, 0xFFFF4008);
            }
            case CLOUD -> {
                g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF15161C);
                g.fill(cx - r + 1, cy - 1, cx + r - 2, cy + 2, 0x90D8D8DC);
                g.fill(cx - r + 3, cy - r + 3, cx + 1, cy - 1, 0x60D0D0D8);
                g.fill(cx - 1, cy + 1, cx + r - 2, cy + r - 2, 0x50D0D0D8);
            }
            case IMAGE -> {
                g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF20242C);
                g.fill(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2, 0xFF3A4050);
                g.fill(cx - r + 3, cy + 1, cx, cy + r - 2, 0xFF566078);
                g.fill(cx - 1, cy - r + 3, cx + r - 3, cy + r - 4, 0xFF8894A8);
            }
            default -> SmoothShapes.drawRing(g, cx, cy, r, C_TEXT);
        }
    }

    private boolean isUnderground() {
        return undergroundView;
    }

    private void recenterGlobeIfNeeded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mode != ViewMode.GLOBE) return;

        GlobeCamera.SpherePoint sp = globeCamera.screenToSpherePoint(frameCenterX(), frameCenterY(), globeCx(),
                globeCy());
        if (sp == null) return;

        int size = texture().getSizePixels();
        int viewWorldX = (int) Math.floor(sp.u * size - radiusPixels + (anchorChunkX << 4));
        int viewWorldZ = (int) Math.floor(sp.v * size - radiusPixels + (anchorChunkZ << 4));
        int viewChunkX = viewWorldX >> 4;
        int viewChunkZ = viewWorldZ >> 4;

        if (viewChunkX == anchorChunkX && viewChunkZ == anchorChunkZ) return;
        if (!dragRebuildGateOpen()) return;

        anchorChunkX = viewChunkX;
        anchorChunkZ = viewChunkZ;
        centerKey = ChunkKey.of(mc.level, new ChunkPos(anchorChunkX, anchorChunkZ));
        texture().maybeRebuild(centerKey);
    }

    public void goToCoordinate(int worldX, int worldZ) {
        if (mode == ViewMode.GLOBE) {
            cancelDrawing();
            mode = ViewMode.FLAT;
        }
        centerViewOn(worldX, worldZ);
    }

    private double globePixelX(double worldX) {
        return radiusPixels + (worldX - (anchorChunkX << 4));
    }

    private double globePixelZ(double worldZ) {
        return radiusPixels + (worldZ - (anchorChunkZ << 4));
    }

    private static final long DRAG_REBUILD_THROTTLE_MS = 200L;

    private boolean dragRebuildGateOpen() {
        if (!dragging) return true;
        long now = System.currentTimeMillis();
        if (now - lastDragRebuildMillis < DRAG_REBUILD_THROTTLE_MS) return false;
        lastDragRebuildMillis = now;
        return true;
    }

    private void drawUnexploredCoverImage(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (SolarisAPI.getUnexploredStyle(mc.level.dimension().location()) != UnexploredStyle.IMAGE) return;
        if (!SolarisConfig.UNEXPLORED_IMAGE_COVER.get()) return;
        if (!UnexploredImageStyle.isAvailable()) return;

        g.blit(UnexploredImageStyle.textureId(), frameLeft(), frameTop(), frameRight() - frameLeft(),
                frameBottom() - frameTop(), 0, 0, UnexploredImageStyle.width(), UnexploredImageStyle.height(),
                UnexploredImageStyle.width(), UnexploredImageStyle.height());
    }

    private void beginFrame(GuiGraphics g) {
        ModernPanel.draw(g, panelLeft(), panelTop(), panelRight() - panelLeft(), panelBottom() - panelTop(),
                C_BORDER);
        g.enableScissor(frameLeft(), frameTop(), frameRight(), frameBottom());
        g.fill(frameLeft(), frameTop(), frameRight(), frameBottom(), C_BG);
        drawUnexploredCoverImage(g);
    }

    public void renderMapBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        beginFrame(g);
        if (isUnderground()) {
            renderCaveMapTiles(g);
        } else {
            renderFlatMapTiles(g);
        }
        g.disableScissor();
    }

    public void renderMapBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick, int targetWidth,
                                    int targetHeight) {
        if (minecraft != null && (targetWidth != width || targetHeight != height)) {
            resize(minecraft, targetWidth, targetHeight);
        }
        renderMapBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    private void refreshWorldMarkersIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastWorldMarkerRefreshMillis < WORLD_MARKER_REFRESH_MS) return;
        lastWorldMarkerRefreshMillis = now;

        Minecraft mc = Minecraft.getInstance();

        SharedExplorationClient.requestAround(anchorChunkX, anchorChunkZ);

        cachedMobs.clear();
        if (SolarisConfig.SHOW_MOBS.get() && mc.level != null) {
            SolarisProfiler.time("worldMarkers:mobScan", () -> {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof Mob mob && mob.isAlive()) cachedMobs.add(mob);
                }
            });
        }

        cachedVeins.clear();
        if (SolarisConfig.SHOW_GT_ORE_VEINS.get() && !gtceuBroken && mc.level != null &&
                GtceuIntegration.isAvailable()) {
            try {
                SolarisProfiler.time("worldMarkers:veinScan", () -> {
                    int minX = (anchorChunkX << 4) - radiusPixels;
                    int minZ = (anchorChunkZ << 4) - radiusPixels;
                    int span = radiusPixels * 2;
                    cachedVeins.addAll(GtceuIntegration.getVeinsInArea(mc.level.dimension().location(), minX, minZ,
                            span, span));
                });
            } catch (Throwable t) {
                gtceuBroken = true;
                PhoenixSolaris.LOGGER.error(
                        "GTCEu is present but its integration failed. Disabling ore vein markers for the rest of this session.",
                        t);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        refreshWorldMarkersIfNeeded();

        beginFrame(g);

        if (mode == ViewMode.FLAT) {
            renderFlatMap(g, mx, my);
        } else {
            renderGlobeMap(g, mx, my);
        }

        g.disableScissor();

        if (mode == ViewMode.FLAT) drawScaleBar(g);

        super.render(g, mx, my, partialTick);

        g.drawCenteredString(font, title, width / 2, 6, C_ACCENT);
        g.drawCenteredString(font, footerHint(), (int) frameCenterX(), frameBottom() + 10, C_TEXT);

        String syncText = SharedExplorationClient.activityText();
        if (syncText != null) {
            g.drawString(font, syncText, frameRight() - 6 - font.width(syncText), frameTop() + 6, C_ACCENT, false);
        }

        String hoveredButton = renderIconButtons(g, mx, my);

        Component tooltip = null;
        if (hoveredVeinName != null) {
            tooltip = Component.literal(hoveredVeinName);
        } else if (hoveredPlayerName != null) {
            tooltip = Component.literal(hoveredPlayerName);
        } else if (hoveredMobName != null) {
            tooltip = Component.literal(hoveredMobName);
        } else if (hoveredButton != null) {
            tooltip = Component.literal(hoveredButton);
        } else if (mode == ViewMode.FLAT && !dragging && toolMode == ToolMode.NAVIGATE &&
                SolarisConfig.SHOW_BLOCK_TOOLTIP.get()) {
                    tooltip = hoveredBlockName(mx, my);
                }
        if (tooltip != null) {
            g.renderTooltip(font, tooltip, mx, my);
        }
    }

    private void drawScaleBar(GuiGraphics g) {
        float zoom = viewport.getZoom();
        if (zoom <= 0) return;

        int targetPx = 60;
        double blocks = niceScaleNumber(targetPx / zoom);
        int barPx = Math.max(1, (int) Math.round(blocks * zoom));

        int x = frameLeft() + 10;
        int y = frameTop() + 12;
        g.fill(x, y, x + barPx, y + 2, C_TEXT);
        g.fill(x, y - 2, x + 1, y + 5, C_TEXT);
        g.fill(x + barPx - 1, y - 2, x + barPx, y + 5, C_TEXT);

        String label = blocks >= 1000 ? Math.round(blocks / 1000) + "k blocks" : Math.round(blocks) + " blocks";
        g.drawString(font, label, x, y + 7, C_TEXT, true);
    }

    private static final float CHUNK_GRID_MIN_ZOOM = 60f / 200f;

    private void drawChunkGridWorld(GuiGraphics g) {
        if (viewport.getZoom() < CHUNK_GRID_MIN_ZOOM) return;

        int frameLeft = frameLeft();
        int frameRight = frameRight();
        int frameTop = frameTop();
        int frameBottom = frameBottom();
        int gridColor = 0x80000000;

        int chunkMinX = ((int) Math.floor(viewport.toWorldX(frameLeft, 0))) >> 4;
        int chunkMaxX = ((int) Math.floor(viewport.toWorldX(frameRight, 0))) >> 4;
        for (int cx = chunkMinX; cx <= chunkMaxX + 1; cx++) {
            int sx = (int) Math.round(viewport.toScreenX(cx << 4, 0));
            if (sx < frameLeft || sx > frameRight) continue;
            g.fill(sx, frameTop, sx + 1, frameBottom, gridColor);
        }

        int chunkMinZ = ((int) Math.floor(viewport.toWorldZ(frameTop, 0))) >> 4;
        int chunkMaxZ = ((int) Math.floor(viewport.toWorldZ(frameBottom, 0))) >> 4;
        for (int cz = chunkMinZ; cz <= chunkMaxZ + 1; cz++) {
            int sy = (int) Math.round(viewport.toScreenY(cz << 4, 0));
            if (sy < frameTop || sy > frameBottom) continue;
            g.fill(frameLeft, sy, frameRight, sy + 1, gridColor);
        }
    }

    private void drawVignetteOverlay(GuiGraphics g) {
        double strength = SolarisConfig.VIGNETTE_STRENGTH.get();
        if (strength <= 0) return;

        int left = frameLeft();
        int top = frameTop();
        int right = frameRight();
        int bottom = frameBottom();

        int maxAlpha = (int) Mth.clamp(strength * 180, 0, 200);
        int steps = 24;
        int fadeWidth = Math.max(steps, Math.min(right - left, bottom - top) / 4);
        int stepPx = Math.max(1, fadeWidth / steps);

        for (int i = 0; i < steps; i++) {
            int alpha = maxAlpha * (steps - i) / steps;
            if (alpha <= 0) continue;
            int color = alpha << 24;
            int inset = i * stepPx;
            g.fill(left, top + inset, right, top + inset + stepPx, color);
            g.fill(left, bottom - inset - stepPx, right, bottom - inset, color);
            g.fill(left + inset, top, left + inset + stepPx, bottom, color);
            g.fill(right - inset - stepPx, top, right - inset, bottom, color);
        }
    }

    private boolean insideMapShape(int screenX, int screenY) {
        MinimapShape shape = SolarisConfig.MAP_SHAPE.get();
        if (shape == MinimapShape.SQUARE) return true;
        float nx = (screenX - frameLeft()) / (float) (frameRight() - frameLeft());
        float ny = (screenY - frameTop()) / (float) (frameBottom() - frameTop());
        return shape.containsPoint(nx, ny);
    }

    private static double niceScaleNumber(double raw) {
        if (raw <= 0) return 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double fraction = raw / magnitude;
        double niceFraction;
        if (fraction < 1.5) niceFraction = 1;
        else if (fraction < 3.5) niceFraction = 2;
        else if (fraction < 7.5) niceFraction = 5;
        else niceFraction = 10;
        return niceFraction * magnitude;
    }

    private String footerHint() {
        return switch (toolMode) {
            case NAVIGATE -> "Right-click: new waypoint (or delete one)";
            case DRAW_RECTANGLE -> "Rectangle: drag to draw · R/C/L: switch tool · Esc: cancel";
            case DRAW_CIRCLE -> "Circle: drag to draw · R/C/L: switch tool · Esc: cancel";
            case DRAW_LINE -> "Line: click to add points · Enter/right-click: finish · Esc: cancel";
        };
    }

    private void renderFlatMap(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();

        if (isUnderground()) {
            renderCaveMapTiles(g);
        } else {
            renderFlatMapTiles(g);
        }
        if (SolarisConfig.SHOW_CHUNK_GRID.get()) drawChunkGridWorld(g);
        if (SolarisConfig.VIGNETTE.get()) drawVignetteOverlay(g);

        int texScreenMinX = frameLeft();
        int texScreenMaxX = frameRight();
        int texScreenMinY = frameTop();
        int texScreenMaxY = frameBottom();

        if (SolarisConfig.SHOW_RAIL_NETWORK.get() && mc.level != null && viewport.getZoom() >= 0.15f) {
            ResourceLocation dimension = mc.level.dimension().location();
            SolarisProfiler.time("railNetworkRender", () -> drawRailNetwork(g, dimension));
        }

        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(Mth.clamp(3f * viewport.getZoom(), 2f, 10f) * iconScale);
        int playerR = Math.max(4, Math.min(12, Math.round(3.5f * viewport.getZoom())));

        int mobR = Math.max(2, Math.round(iconR * 0.65f));

        LabelSide labelSide = SolarisConfig.LABEL_SIDE.get();

        if (mc.level != null) {
            List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
            for (Waypoint w : waypoints) {
                int wx = (int) viewport.toScreenX(w.x, 0);
                int wy = (int) viewport.toScreenY(w.z, 0);
                if (wx < texScreenMinX || wx > texScreenMaxX || wy < texScreenMinY || wy > texScreenMaxY ||
                        !insideMapShape(wx, wy)) {
                    continue;
                }
                WaypointIconManager.draw(g, w.icon, wx, wy, iconR, w.colorArgb());
                int lx = labelSide.drawX(wx, iconR, font.width(w.name));
                int ly = labelSide.drawY(wy, iconR, font.lineHeight);
                g.drawString(font, w.name, lx, ly, w.labelColorArgb(C_TEXT), true);
            }
        }

        hoveredVeinName = null;

        if (SolarisConfig.SHOW_GT_ORE_VEINS.get()) {
            for (GtceuIntegration.GtOreVein vein : cachedVeins) {
                int vx = (int) viewport.toScreenX(vein.center().getX(), 0);
                int vy = (int) viewport.toScreenY(vein.center().getZ(), 0);
                if (vx < texScreenMinX || vx > texScreenMaxX || vy < texScreenMinY || vy > texScreenMaxY ||
                        !insideMapShape(vx, vy)) {
                    continue;
                }
                drawVeinMarker(g, vein, vx, vy, iconR);
                if (mx >= vx - iconR && mx <= vx + iconR && my >= vy - iconR && my <= vy + iconR) {
                    hoveredVeinName = vein.name();
                }
            }
        }

        hoveredPlayerName = null;
        if (mc.level != null && mc.player != null) {
            for (RemotePlayerTracker.MapPlayer other : RemotePlayerTracker.collect(mc)) {
                int ox = (int) viewport.toScreenX(other.x(), 0);
                int oy = (int) viewport.toScreenY(other.z(), 0);

                boolean plEdge = ox < texScreenMinX || ox > texScreenMaxX || oy < texScreenMinY || oy > texScreenMaxY;
                int plDrawX = ox;
                int plDrawY = oy;
                String plLabel = other.name();
                if (plEdge) {
                    int pad = playerR + 3;
                    plDrawX = Math.max(texScreenMinX + pad, Math.min(texScreenMaxX - pad, ox));
                    plDrawY = Math.max(texScreenMinY + pad, Math.min(texScreenMaxY - pad, oy));
                    double plDistance = Math.hypot(other.x() - mc.player.getX(), other.z() - mc.player.getZ());
                    plLabel = other.name() + " (" + Math.round(plDistance) + "m)";
                }
                if (!insideMapShape(plDrawX, plDrawY)) continue;

                PlayerArrow.draw(g, plDrawX, plDrawY, plEdge ? Math.max(3, playerR - 2) : playerR, other.yaw(),
                        0xFFAAAAAA, other.skin());
                if (mx >= plDrawX - playerR && mx <= plDrawX + playerR && my >= plDrawY - playerR &&
                        my <= plDrawY + playerR) {
                    hoveredPlayerName = plLabel;
                }
            }
        }

        for (PingTracker.Ping ping : PingTracker.collect(mc)) {
            int pgX = (int) viewport.toScreenX(ping.x() + 0.5, 0);
            int pgY = (int) viewport.toScreenY(ping.z() + 0.5, 0);
            if (pgX < texScreenMinX || pgX > texScreenMaxX || pgY < texScreenMinY || pgY > texScreenMaxY ||
                    !insideMapShape(pgX, pgY)) {
                continue;
            }
            PingMarker.draw(g, pgX, pgY, playerR + 2, ping);
            if (mx >= pgX - playerR && mx <= pgX + playerR && my >= pgY - playerR && my <= pgY + playerR) {
                hoveredPlayerName = ping.sender() + " (ping)";
            }
        }

        hoveredMobName = null;
        if (SolarisConfig.SHOW_MOBS.get()) {
            for (Mob mob : cachedMobs) {
                if (!mob.isAlive()) continue;
                int mobX = (int) viewport.toScreenX(mob.getX(), 0);
                int mobY = (int) viewport.toScreenY(mob.getZ(), 0);
                if (mobX < texScreenMinX || mobX > texScreenMaxX || mobY < texScreenMinY || mobY > texScreenMaxY ||
                        !insideMapShape(mobX, mobY)) {
                    continue;
                }
                drawMobIcon(g, mob, mobX, mobY, mobR);
                if (mx >= mobX - mobR && mx <= mobX + mobR && my >= mobY - mobR && my <= mobY + mobR) {
                    hoveredMobName = mob.getName().getString();
                }
            }
        }

        if (hasPlayerMarker && mc.player != null) {
            int cx = (int) viewport.toScreenX(mc.player.getX(), 0);
            int cy = (int) viewport.toScreenY(mc.player.getZ(), 0);
            if (cx >= texScreenMinX && cx <= texScreenMaxX && cy >= texScreenMinY && cy <= texScreenMaxY &&
                    insideMapShape(cx, cy)) {
                PlayerArrow.draw(g, cx, cy, playerR, mc.player.getYRot(), C_ACCENT, mc.player.getSkin().texture());
            }
        }

        if (mc.level != null) {
            for (PlanShape shape : PlanShapeManager.getVisibleForDimension(mc.level.dimension().location())) {
                drawShapeFlat(g, shape, shape.colorArgb(), 2);
            }
        }
        drawShapePreview(g, mx, my);
    }

    private int renderYBucket = Integer.MIN_VALUE;

    private int stableCaveYBucket(int playerY) {
        int raw = PersistentCaveStore.yBucket(playerY);
        if (renderYBucket == Integer.MIN_VALUE ||
                Math.abs(playerY - renderYBucket * PersistentCaveStore.Y_BUCKET_SIZE) >
                        PersistentCaveStore.Y_BUCKET_SIZE) {
            renderYBucket = raw;
        }
        return renderYBucket;
    }

    private void renderCaveMapTiles(GuiGraphics g) {
        SolarisProfiler.time("renderCaveMapTiles", () -> renderCaveMapTilesImpl(g));
    }

    private void renderCaveMapTilesImpl(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ResourceLocation dimension = mc.level.dimension().location();
        int yBucket = stableCaveYBucket(mc.player != null ? Mth.floor(mc.player.getY()) : 0);

        float zoom = viewport.getZoom();
        if (zoom < 0.05f) return;

        double worldMinX = viewport.toWorldX(frameLeft(), 0);
        double worldMaxX = viewport.toWorldX(frameRight(), 0);
        double worldMinZ = viewport.toWorldZ(frameTop(), 0);
        double worldMaxZ = viewport.toWorldZ(frameBottom(), 0);

        int lod = MapTileCache.lodForZoom(zoom);
        int chunksPerTile = CaveTileCache.TILE_CHUNKS << lod;
        int tileWorldSize = CaveTileCache.TILE_PIXELS << lod;

        int tileMinX = Math.floorDiv((int) Math.floor(worldMinX) >> 4, chunksPerTile);
        int tileMaxX = Math.floorDiv((int) Math.floor(worldMaxX) >> 4, chunksPerTile);
        int tileMinZ = Math.floorDiv((int) Math.floor(worldMinZ) >> 4, chunksPerTile);
        int tileMaxZ = Math.floorDiv((int) Math.floor(worldMaxZ) >> 4, chunksPerTile);

        CaveTileCache.ensureCapacity((tileMaxX - tileMinX + 1) * (tileMaxZ - tileMinZ + 1));

        int[] buildBudget = { MAX_TILE_BUILDS_PER_FRAME };
        for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                int tileWorldX = tx * tileWorldSize;
                int tileWorldZ = tz * tileWorldSize;

                int destX = (int) Math.round(viewport.toScreenX(tileWorldX, 0));
                int destY = (int) Math.round(viewport.toScreenY(tileWorldZ, 0));
                int destSizeX = (int) Math.round(viewport.toScreenX(tileWorldX + tileWorldSize, 0)) - destX;
                int destSizeZ = (int) Math.round(viewport.toScreenY(tileWorldZ + tileWorldSize, 0)) - destY;

                if (destX + destSizeX < frameLeft() || destX > frameRight() ||
                        destY + destSizeZ < frameTop() || destY > frameBottom()) {
                    continue;
                }

                CaveTileCache.TileKey key = new CaveTileCache.TileKey(dimension, tx, tz, yBucket, lod);
                CaveTileCache.CaveTile tile = CaveTileCache.getOrBuildTile(key, mc.level, mc.player, buildBudget);

                if (tile == null) continue;

                g.blit(tile.textureId(), destX, destY, destSizeX, destSizeZ, 0, 0, CaveTileCache.TILE_PIXELS,
                        CaveTileCache.TILE_PIXELS, CaveTileCache.TILE_PIXELS, CaveTileCache.TILE_PIXELS);
            }
        }
    }

    private void renderFlatMapTiles(GuiGraphics g) {
        SolarisProfiler.time("renderFlatMapTiles", () -> renderFlatMapTilesImpl(g));
    }

    private void renderFlatMapTilesImpl(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ResourceLocation dimension = mc.level.dimension().location();

        float zoom = viewport.getZoom();
        if (zoom < 0.05f) return;

        double worldMinX = viewport.toWorldX(frameLeft(), 0);
        double worldMaxX = viewport.toWorldX(frameRight(), 0);
        double worldMinZ = viewport.toWorldZ(frameTop(), 0);
        double worldMaxZ = viewport.toWorldZ(frameBottom(), 0);

        int lod = MapTileCache.lodForZoom(zoom);
        int chunksPerTile = MapTileCache.TILE_CHUNKS << lod;
        int tileWorldSize = MapTileCache.TILE_PIXELS << lod;

        int tileMinX = Math.floorDiv((int) Math.floor(worldMinX) >> 4, chunksPerTile);
        int tileMaxX = Math.floorDiv((int) Math.floor(worldMaxX) >> 4, chunksPerTile);
        int tileMinZ = Math.floorDiv((int) Math.floor(worldMinZ) >> 4, chunksPerTile);
        int tileMaxZ = Math.floorDiv((int) Math.floor(worldMaxZ) >> 4, chunksPerTile);

        SharedExplorationClient.requestVisible((int) Math.floor(worldMinX) >> 4, (int) Math.floor(worldMinZ) >> 4,
                (int) Math.floor(worldMaxX) >> 4, (int) Math.floor(worldMaxZ) >> 4);

        MapTileCache.ensureCapacity((tileMaxX - tileMinX + 1) * (tileMaxZ - tileMinZ + 1));

        int[] buildBudget = { MAX_TILE_BUILDS_PER_FRAME };
        for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                int tileWorldX = tx * tileWorldSize;
                int tileWorldZ = tz * tileWorldSize;

                int destX = (int) Math.round(viewport.toScreenX(tileWorldX, 0));
                int destY = (int) Math.round(viewport.toScreenY(tileWorldZ, 0));
                int destSizeX = (int) Math.round(viewport.toScreenX(tileWorldX + tileWorldSize, 0)) - destX;
                int destSizeZ = (int) Math.round(viewport.toScreenY(tileWorldZ + tileWorldSize, 0)) - destY;

                if (destX + destSizeX < frameLeft() || destX > frameRight() ||
                        destY + destSizeZ < frameTop() || destY > frameBottom()) {
                    continue;
                }

                MapTileCache.TileKey key = new MapTileCache.TileKey(dimension, tx, tz, lod);
                MapTileCache.MapTile tile = MapTileCache.getOrBuildTile(key, buildBudget);

                if (tile == null) continue;

                g.blit(tile.textureId(), destX, destY, destSizeX, destSizeZ, 0, 0, MapTileCache.TILE_PIXELS,
                        MapTileCache.TILE_PIXELS, MapTileCache.TILE_PIXELS, MapTileCache.TILE_PIXELS);
            }
        }
    }

    private static final int RAIL_LINE_COLOR = 0xFFB6B6B6;

    private void drawRailNetwork(GuiGraphics g, ResourceLocation dimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int range = SolarisConfig.RAIL_NETWORK_RANGE.get();
        int centerChunkX = mc.player.getBlockX() >> 4;
        int centerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (range >> 4) + 1;
        int thickness = Math.max(1, Math.round(1.5f * viewport.getZoom()));

        for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
            for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
                boolean[] rails = ChunkRailCache.get(new ChunkKey(dimension, cx, cz));
                if (rails == null) continue;
                boolean[] eastRails = ChunkRailCache.get(new ChunkKey(dimension, cx + 1, cz));
                boolean[] southRails = ChunkRailCache.get(new ChunkKey(dimension, cx, cz + 1));

                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (!rails[lz * 16 + lx]) continue;
                        int wx = (cx << 4) + lx;
                        int wz = (cz << 4) + lz;

                        boolean eastRail = lx < 15 ? rails[lz * 16 + lx + 1] : eastRails != null && eastRails[lz * 16];
                        if (eastRail) drawRailSegment(g, wx, wz, wx + 1, wz, thickness);

                        boolean southRail = lz < 15 ? rails[(lz + 1) * 16 + lx] : southRails != null && southRails[lx];
                        if (southRail) drawRailSegment(g, wx, wz, wx, wz + 1, thickness);
                    }
                }
            }
        }
    }

    private void drawRailSegment(GuiGraphics g, int wx1, int wz1, int wx2, int wz2, int thickness) {
        int x1 = (int) viewport.toScreenX(wx1, 0);
        int y1 = (int) viewport.toScreenY(wz1, 0);
        int x2 = (int) viewport.toScreenX(wx2, 0);
        int y2 = (int) viewport.toScreenY(wz2, 0);
        if ((x1 < frameLeft() && x2 < frameLeft()) || (x1 > frameRight() && x2 > frameRight())) return;
        if ((y1 < frameTop() && y2 < frameTop()) || (y1 > frameBottom() && y2 > frameBottom())) return;
        LineRenderer.drawLine(g, x1, y1, x2, y2, thickness, RAIL_LINE_COLOR);
    }

    private void drawShapeFlat(GuiGraphics g, PlanShape shape, int color, int thickness) {
        switch (shape.type) {
            case RECTANGLE -> {
                int[] p1 = shape.points.get(0);
                int[] p2 = shape.points.get(1);
                int x1 = (int) viewport.toScreenX(p1[0], 0);
                int z1 = (int) viewport.toScreenY(p1[1], 0);
                int x2 = (int) viewport.toScreenX(p2[0], 0);
                int z2 = (int) viewport.toScreenY(p2[1], 0);
                LineRenderer.drawLine(g, x1, z1, x2, z1, thickness, color);
                LineRenderer.drawLine(g, x2, z1, x2, z2, thickness, color);
                LineRenderer.drawLine(g, x2, z2, x1, z2, thickness, color);
                LineRenderer.drawLine(g, x1, z2, x1, z1, thickness, color);
            }
            case CIRCLE -> {
                int[] c = shape.points.get(0);
                int cx = (int) viewport.toScreenX(c[0], 0);
                int cz = (int) viewport.toScreenY(c[1], 0);
                int screenRadius = Math.max(2, Math.round(shape.radius * viewport.getZoom()));
                SmoothShapes.drawRing(g, cx, cz, screenRadius, color);
            }
            case LINE -> drawPolylineFlat(g, shape.points, color, thickness);
        }
    }

    private void drawPolylineFlat(GuiGraphics g, List<int[]> points, int color, int thickness) {
        int[] prevScreen = null;
        for (int[] p : points) {
            int sx = (int) viewport.toScreenX(p[0], 0);
            int sz = (int) viewport.toScreenY(p[1], 0);
            if (prevScreen != null) LineRenderer.drawLine(g, prevScreen[0], prevScreen[1], sx, sz, thickness, color);
            prevScreen = new int[] { sx, sz };
        }
    }

    private void drawShapePreview(GuiGraphics g, int mx, int my) {
        int previewColor = 0xFFFFFFFF;
        if ((toolMode == ToolMode.DRAW_RECTANGLE || toolMode == ToolMode.DRAW_CIRCLE) && drawAnchor != null &&
                drawCurrent != null) {
            if (toolMode == ToolMode.DRAW_RECTANGLE) {
                PlanShape preview = new PlanShape();
                preview.type = PlanShape.Type.RECTANGLE;
                preview.points = List.of(drawAnchor, drawCurrent);
                drawShapeFlat(g, preview, previewColor, 1);
            } else {
                int cx = (int) viewport.toScreenX(drawAnchor[0], 0);
                int cz = (int) viewport.toScreenY(drawAnchor[1], 0);
                double dx = drawCurrent[0] - drawAnchor[0];
                double dz = drawCurrent[1] - drawAnchor[1];
                int screenRadius = Math.max(2, (int) Math.round(Math.sqrt(dx * dx + dz * dz) * viewport.getZoom()));
                SmoothShapes.drawRing(g, cx, cz, screenRadius, previewColor);
            }
        } else if (toolMode == ToolMode.DRAW_LINE && !lineDrawPoints.isEmpty()) {
            drawPolylineFlat(g, lineDrawPoints, previewColor, 1);
            int[] last = lineDrawPoints.get(lineDrawPoints.size() - 1);
            int sx = (int) viewport.toScreenX(last[0], 0);
            int sz = (int) viewport.toScreenY(last[1], 0);
            LineRenderer.drawLine(g, sx, sz, mx, my, 1, previewColor);
        }
    }

    private void renderGlobeMap(GuiGraphics g, int mx, int my) {
        recenterGlobeIfNeeded();
        SolarisTexture tex = texture();
        if (centerKey != null) tex.maybeRebuild(centerKey);
        int size = tex.getSizePixels();

        int cx = globeCx();
        int cy = globeCy();
        float screenRadius = globeCamera.getScale();
        Minecraft mc = Minecraft.getInstance();
        int seaLevel = mc.level != null ? mc.level.getSeaLevel() : 63;

        ResourceLocation globeTextureId = tex.ensureGlobeTexture();
        globeMesh.rebuild(tex.getGlobeHeights(), size, seaLevel);
        SolarisGlobeRenderer.renderSphere(g, cx, cy, screenRadius, globeCamera.rotationQuaternion(),
                globeTextureId, globeMesh);

        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(6f * iconScale);
        int playerR = 7;
        int mobR = Math.max(2, Math.round(iconR * 0.65f));

        LabelSide labelSide = SolarisConfig.LABEL_SIDE.get();

        if (mc.level != null) {
            List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
            for (Waypoint w : waypoints) {
                GlobeCamera.Projection p = globeCamera.sphereToScreen(
                        (float) (globePixelX(w.x) / size), (float) (globePixelZ(w.z) / size), cx, cy);
                if (!p.frontFacing) continue;
                WaypointIconManager.draw(g, w.icon, p.screenX, p.screenY, iconR, w.colorArgb());
                int lx = labelSide.drawX(p.screenX, iconR, font.width(w.name));
                int ly = labelSide.drawY(p.screenY, iconR, font.lineHeight);
                g.drawString(font, w.name, lx, ly, w.labelColorArgb(C_TEXT), true);
            }
        }

        hoveredVeinName = null;
        if (SolarisConfig.SHOW_GT_ORE_VEINS.get()) {
            for (GtceuIntegration.GtOreVein vein : cachedVeins) {
                double vpx = radiusPixels + (vein.center().getX() - (anchorChunkX << 4));
                double vpz = radiusPixels + (vein.center().getZ() - (anchorChunkZ << 4));
                GlobeCamera.Projection p = globeCamera.sphereToScreen((float) (vpx / size), (float) (vpz / size), cx,
                        cy);
                if (!p.frontFacing) continue;
                drawVeinMarker(g, vein, p.screenX, p.screenY, iconR);
                if (mx >= p.screenX - iconR && mx <= p.screenX + iconR && my >= p.screenY - iconR &&
                        my <= p.screenY + iconR) {
                    hoveredVeinName = vein.name();
                }
            }
        }

        hoveredPlayerName = null;
        if (mc.level != null) {
            for (RemotePlayerTracker.MapPlayer other : RemotePlayerTracker.collect(mc)) {
                double opx = radiusPixels + (other.x() - (anchorChunkX << 4));
                double opz = radiusPixels + (other.z() - (anchorChunkZ << 4));
                GlobeCamera.Projection p = globeCamera.sphereToScreen((float) (opx / size), (float) (opz / size), cx,
                        cy);
                if (!p.frontFacing) continue;
                PlayerArrow.draw(g, p.screenX, p.screenY, playerR, other.yaw(), 0xFFAAAAAA,
                        other.skin());
                if (mx >= p.screenX - playerR && mx <= p.screenX + playerR && my >= p.screenY - playerR &&
                        my <= p.screenY + playerR) {
                    hoveredPlayerName = other.name();
                }
            }
        }

        hoveredMobName = null;
        if (SolarisConfig.SHOW_MOBS.get()) {
            for (Mob mob : cachedMobs) {
                if (!mob.isAlive()) continue;
                double mpx = radiusPixels + (mob.getX() - (anchorChunkX << 4));
                double mpz = radiusPixels + (mob.getZ() - (anchorChunkZ << 4));
                GlobeCamera.Projection p = globeCamera.sphereToScreen((float) (mpx / size), (float) (mpz / size), cx,
                        cy);
                if (!p.frontFacing) continue;
                drawMobIcon(g, mob, p.screenX, p.screenY, mobR);
                if (mx >= p.screenX - mobR && mx <= p.screenX + mobR && my >= p.screenY - mobR &&
                        my <= p.screenY + mobR) {
                    hoveredMobName = mob.getName().getString();
                }
            }
        }

        if (hasPlayerMarker && mc.player != null) {
            GlobeCamera.Projection p = globeCamera.sphereToScreen(
                    (float) (globePixelX(mc.player.getX()) / size), (float) (globePixelZ(mc.player.getZ()) / size),
                    cx, cy);
            if (p.frontFacing) {
                PlayerArrow.draw(g, p.screenX, p.screenY, playerR, mc.player.getYRot(), C_ACCENT,
                        mc.player.getSkin().texture());
            }
        }
    }

    private String renderIconButtons(GuiGraphics g, int mx, int my) {
        String hoveredLabel = null;
        for (IconButton b : iconButtons) {
            boolean hover = b.hit(mx, my);
            if (hover) hoveredLabel = b.label();
            int r = b.r();
            g.fill(b.cx() - r - 1, b.cy() - r - 1, b.cx() + r + 1, b.cy() + r + 1, C_BORDER);
            g.fill(b.cx() - r, b.cy() - r, b.cx() + r, b.cy() + r, hover ? C_HEADER : C_PANEL);
            b.draw().draw(g, b.cx(), b.cy(), hover);
        }
        return hoveredLabel;
    }

    private Component hoveredBlockName(int mx, int my) {
        if (!onMap(mx, my)) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        int blockX = (int) Math.floor(viewport.toWorldX(mx, 0));
        int blockZ = (int) Math.floor(viewport.toWorldZ(my, 0));
        int blockY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;

        BlockPos pos = new BlockPos(blockX, blockY, blockZ);
        Component name = mc.level.getBlockState(pos).getBlock().getName();

        ResourceLocation dimension = mc.level.dimension().location();
        if (!SolarisAPI.isFeatureEnabled(SolarisAPI.FEATURE_SHOW_COORDINATES, dimension)) return name;
        return name.copy().append(Component.literal(" (" + blockX + ", " + blockY + ", " + blockZ + ")")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (button == 0) {
            for (IconButton b : iconButtons) {
                if (b.hit(mx, my)) {
                    b.action().run();
                    return true;
                }
            }
            if (toolMode == ToolMode.DRAW_LINE) {
                if (onMap(mx, my)) lineDrawPoints.add(worldPointAt(mx, my));
                return true;
            }
            if (toolMode == ToolMode.DRAW_RECTANGLE || toolMode == ToolMode.DRAW_CIRCLE) {
                if (onMap(mx, my)) {
                    drawAnchor = worldPointAt(mx, my);
                    drawCurrent = drawAnchor;
                    shapeDragging = true;
                }
                return true;
            }
            dragging = true;
            return true;
        }
        if (button == 1) {
            if (toolMode == ToolMode.DRAW_LINE) {
                finishLine();
                return true;
            }
            if (toolMode == ToolMode.DRAW_RECTANGLE || toolMode == ToolMode.DRAW_CIRCLE) {
                cancelDrawing();
                return true;
            }
            handleRightClick(mx, my);
            return true;
        }
        return false;
    }

    private int[] worldPointAt(double mx, double my) {
        int blockX = (int) Math.floor(viewport.toWorldX(mx, 0));
        int blockZ = (int) Math.floor(viewport.toWorldZ(my, 0));
        return new int[] { blockX, blockZ };
    }

    private void switchDrawTool(ToolMode newTool) {
        toolMode = newTool;
        shapeDragging = false;
        drawAnchor = null;
        drawCurrent = null;
        lineDrawPoints.clear();
    }

    private void cancelDrawing() {
        switchDrawTool(ToolMode.NAVIGATE);
    }

    private void finishRectOrCircle() {
        if (drawAnchor == null || drawCurrent == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            drawAnchor = null;
            drawCurrent = null;
            return;
        }

        if (drawAnchor[0] == drawCurrent[0] && drawAnchor[1] == drawCurrent[1]) {
            drawAnchor = null;
            drawCurrent = null;
            return;
        }

        PlanShape.Type type = toolMode == ToolMode.DRAW_RECTANGLE ? PlanShape.Type.RECTANGLE : PlanShape.Type.CIRCLE;
        List<int[]> points = new ArrayList<>();
        int radius = 0;
        int centroidX;
        int centroidZ;
        if (type == PlanShape.Type.RECTANGLE) {
            points.add(drawAnchor);
            points.add(drawCurrent);
            centroidX = (drawAnchor[0] + drawCurrent[0]) / 2;
            centroidZ = (drawAnchor[1] + drawCurrent[1]) / 2;
        } else {
            points.add(drawAnchor);
            double dx = drawCurrent[0] - drawAnchor[0];
            double dz = drawCurrent[1] - drawAnchor[1];
            radius = Math.max(1, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
            centroidX = drawAnchor[0];
            centroidZ = drawAnchor[1];
        }
        int baseY = WaypointPlacement.groundY(mc.level, centroidX, centroidZ, playerYHint(mc));

        drawAnchor = null;
        drawCurrent = null;
        Minecraft.getInstance().setScreen(
                new QuickPlanShapeScreen(this, mc.level.dimension().location(), type, points, radius, baseY));
    }

    private void finishLine() {
        if (lineDrawPoints.size() < 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<int[]> points = new ArrayList<>(lineDrawPoints);
        int sumX = 0;
        int sumZ = 0;
        for (int[] p : points) {
            sumX += p[0];
            sumZ += p[1];
        }
        int centroidX = sumX / points.size();
        int centroidZ = sumZ / points.size();
        int baseY = WaypointPlacement.groundY(mc.level, centroidX, centroidZ, playerYHint(mc));

        lineDrawPoints.clear();
        Minecraft.getInstance().setScreen(new QuickPlanShapeScreen(this, mc.level.dimension().location(),
                PlanShape.Type.LINE, points, 0, baseY));
    }

    private static int playerYHint(Minecraft mc) {
        return mc.player != null ? mc.player.getBlockY() : 64;
    }

    private void handleRightClick(double mx, double my) {
        if (mode == ViewMode.GLOBE) {
            handleRightClickGlobe(mx, my);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Waypoint hit = hitTestWaypoint(mx, my);
        if (hit != null) {
            if (hit.locked) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("That waypoint is locked: " + hit.name), true);
                }
                return;
            }
            WaypointManager.remove(hit.id);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Waypoint removed: " + hit.name), true);
            }
            return;
        }

        PlanShape shapeHit = hitTestShape(mx, my);
        if (shapeHit != null) {
            PlanShapeManager.remove(shapeHit.id);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Shape removed: " + shapeHit.name), true);
            }
            return;
        }

        if (!onMap(mx, my) || mc.player == null || mc.level == null) return;

        int blockX = (int) Math.floor(viewport.toWorldX(mx, 0));
        int blockZ = (int) Math.floor(viewport.toWorldZ(my, 0));

        int blockY = WaypointPlacement.groundY(mc.level, blockX, blockZ, playerYHint(mc));

        Minecraft.getInstance().setScreen(
                new QuickWaypointScreen(this, mc.level.dimension().location(), blockX, blockY, blockZ));
    }

    private static final double SHAPE_HIT_TOLERANCE = 6.0;

    private PlanShape hitTestShape(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        List<PlanShape> shapes = PlanShapeManager.getVisibleForDimension(mc.level.dimension().location());
        for (int i = shapes.size() - 1; i >= 0; i--) {
            PlanShape shape = shapes.get(i);
            if (shapeOutlineDistance(shape, mx, my) <= SHAPE_HIT_TOLERANCE) return shape;
        }
        return null;
    }

    private double shapeOutlineDistance(PlanShape shape, double mx, double my) {
        switch (shape.type) {
            case RECTANGLE -> {
                if (shape.points.size() < 2) return Double.MAX_VALUE;
                double x1 = viewport.toScreenX(shape.points.get(0)[0], 0);
                double y1 = viewport.toScreenY(shape.points.get(0)[1], 0);
                double x2 = viewport.toScreenX(shape.points.get(1)[0], 0);
                double y2 = viewport.toScreenY(shape.points.get(1)[1], 0);
                return Math.min(
                        Math.min(distToSegment(mx, my, x1, y1, x2, y1), distToSegment(mx, my, x2, y1, x2, y2)),
                        Math.min(distToSegment(mx, my, x2, y2, x1, y2), distToSegment(mx, my, x1, y2, x1, y1)));
            }
            case CIRCLE -> {
                if (shape.points.isEmpty()) return Double.MAX_VALUE;
                double cx = viewport.toScreenX(shape.points.get(0)[0], 0);
                double cy = viewport.toScreenY(shape.points.get(0)[1], 0);
                double screenRadius = Math.max(2, Math.round(shape.radius * viewport.getZoom()));
                return Math.abs(Math.hypot(mx - cx, my - cy) - screenRadius);
            }
            case LINE -> {
                double best = Double.MAX_VALUE;
                for (int i = 1; i < shape.points.size(); i++) {
                    int[] a = shape.points.get(i - 1);
                    int[] b = shape.points.get(i);
                    best = Math.min(best, distToSegment(mx, my, viewport.toScreenX(a[0], 0),
                            viewport.toScreenY(a[1], 0), viewport.toScreenX(b[0], 0), viewport.toScreenY(b[1], 0)));
                }
                return best;
            }
        }
        return Double.MAX_VALUE;
    }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0 ? 0 : Mth.clamp(((px - x1) * dx + (py - y1) * dy) / lengthSq, 0, 1);
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private Waypoint hitTestWaypoint(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(Mth.clamp(3f * viewport.getZoom(), 2f, 10f) * iconScale);

        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        for (Waypoint w : waypoints) {
            double wx = viewport.toScreenX(w.x, 0);
            double wy = viewport.toScreenY(w.z, 0);
            if (mx >= wx - iconR && mx <= wx + iconR && my >= wy - iconR && my <= wy + iconR) return w;
        }
        return null;
    }

    private void handleRightClickGlobe(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        Waypoint hit = hitTestWaypointGlobe(mx, my);
        if (hit != null) {
            if (hit.locked) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("That waypoint is locked: " + hit.name), true);
                }
                return;
            }
            WaypointManager.remove(hit.id);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Waypoint removed: " + hit.name), true);
            }
            return;
        }

        if (mc.player == null || mc.level == null) return;

        SolarisTexture tex = texture();
        int size = tex.getSizePixels();
        GlobeCamera.SpherePoint sp = globeCamera.screenToSpherePoint(mx, my, globeCx(), globeCy());
        if (sp == null) return;

        int blockX = (int) Math.floor(sp.u * size - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(sp.v * size - radiusPixels + (anchorChunkZ << 4));
        int blockY = WaypointPlacement.groundY(mc.level, blockX, blockZ, playerYHint(mc));

        Minecraft.getInstance().setScreen(
                new QuickWaypointScreen(this, mc.level.dimension().location(), blockX, blockY, blockZ));
    }

    private Waypoint hitTestWaypointGlobe(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        SolarisTexture tex = texture();
        int size = tex.getSizePixels();
        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(6f * iconScale);
        int cx = globeCx();
        int cy = globeCy();

        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        for (Waypoint w : waypoints) {
            GlobeCamera.Projection p = globeCamera.sphereToScreen(
                    (float) (globePixelX(w.x) / size), (float) (globePixelZ(w.z) / size), cx, cy);
            if (!p.frontFacing) continue;
            if (mx >= p.screenX - iconR && mx <= p.screenX + iconR && my >= p.screenY - iconR &&
                    my <= p.screenY + iconR) {
                return w;
            }
        }
        return null;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            dragging = false;
            if (shapeDragging) {
                shapeDragging = false;
                finishRectOrCircle();
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (shapeDragging) {
            drawCurrent = worldPointAt(mx, my);
            return true;
        }
        if (dragging) {
            if (mode == ViewMode.GLOBE) {
                globeCamera.rotate(dx, dy);
            } else {
                viewport.pan(dx, dy);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mode == ViewMode.GLOBE) {
            globeCamera.adjustZoom(scrollY);
            return true;
        }
        syncZoomLimits();
        if (viewport.adjustZoomToAnchor(scrollY, mx, my, 0, 0)) {
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (toolMode != ToolMode.NAVIGATE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelDrawing();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_R) {
                switchDrawTool(ToolMode.DRAW_RECTANGLE);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C) {
                switchDrawTool(ToolMode.DRAW_CIRCLE);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_L) {
                switchDrawTool(ToolMode.DRAW_LINE);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER && toolMode == ToolMode.DRAW_LINE) {
                finishLine();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
