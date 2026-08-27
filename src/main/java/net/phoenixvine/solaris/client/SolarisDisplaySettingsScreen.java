package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.CaveTileCache;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BG;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER2;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;

@OnlyIn(Dist.CLIENT)
public class SolarisDisplaySettingsScreen extends Screen {

    private static final int DISPLAY_GRID_ROWS = 11;

    private static final int ROW_H = 24;
    private static final int HEADER_H = 24;
    private static final int HEADING_H = 12;
    private static final int GROUP_COUNT = 4;
    private static final String[] GROUP_HEADINGS = { "TERRAIN & WATER", "ICONS & LABELS", "EFFECTS", "MINIMAP & GRID" };

    private static final int MIN_W = 460;
    private static final int MIN_H = 520;
    private float uiScale = 1f;
    private int vw, vh;

    private int boxW;
    private int boxH;
    private int boxX;
    private int boxY;
    private final int[] headingY = new int[GROUP_COUNT];

    private enum Tab {

        DISPLAY("Display"),
        WAYPOINTS("Waypoints"),
        INTEGRATIONS("Integrations");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private Tab activeTab = Tab.DISPLAY;

    public SolarisDisplaySettingsScreen(Screen parent) {
        super(Component.literal("Solaris Settings"));
        this.parent = parent;
    }

    private int placeWidgetsFluidly(int startX, int startY, int availableWidth, int[] currentY, int gap,
                                    AbstractWidget... widgets) {
        int xCursor = startX;
        int maxRowH = ROW_H;

        for (AbstractWidget widget : widgets) {

            if (xCursor + widget.getWidth() > startX + availableWidth && xCursor != startX) {
                xCursor = startX;
                currentY[0] += maxRowH;
            }

            widget.setPosition(xCursor, currentY[0]);
            this.addRenderableWidget(widget);

            xCursor += widget.getWidth() + gap;
        }

        currentY[0] += maxRowH;
        return currentY[0];
    }

    @Override
    protected void init() {
        clearWidgets();

        if (parent != null) {
            Minecraft mc = Minecraft.getInstance();
            parent.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        uiScale = (width < MIN_W || height < MIN_H) ? Math.min(width / (float) MIN_W, height / (float) MIN_H) : 1f;
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        boxW = Math.min(450, vw - 40);
        boxX = (vw - boxW) / 2;

        int contentStartY = HEADER_H;
        int[] cursorY = new int[] { contentStartY };

        switch (activeTab) {
            case DISPLAY -> initDisplayTab(boxX, cursorY);
            case WAYPOINTS -> initWaypointsTab(boxX, cursorY);
            case INTEGRATIONS -> initIntegrationsTab(boxX, cursorY);
        }

        boolean showIntegrationsTab = GtceuIntegration.isAvailable();
        int tabBarYOffset = cursorY[0] + 6;

        boxH = tabBarYOffset + 18 + 24 + 18 + 8;
        boxY = Math.max(10, (vh - boxH) / 2);

        for (net.minecraft.client.gui.components.events.GuiEventListener widget : this.children()) {
            if (widget instanceof AbstractWidget aw) aw.setY(aw.getY() + boxY);
        }
        for (int i = 0; i < headingY.length; i++) {
            headingY[i] += boxY;
        }

        int absoluteTabBarY = boxY + tabBarYOffset;

        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.DISPLAY);
        tabs.add(Tab.WAYPOINTS);
        if (showIntegrationsTab) tabs.add(Tab.INTEGRATIONS);
        else if (activeTab == Tab.INTEGRATIONS) activeTab = Tab.DISPLAY;

        int tabW = (boxW - 20 - ((tabs.size() - 1) * 6)) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            String label = (tab == activeTab ? "» " : "") + tab.label;
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                activeTab = tab;
                init();
            }).bounds(boxX + 10 + i * (tabW + 6), absoluteTabBarY, tabW, 18).build());
        }

        int presetHalfW = (boxW - 20 - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("Save Preset"),
                b -> Minecraft.getInstance().setScreen(new SolarisPresetSaveScreen(this, () -> {})))
                .bounds(boxX + 10, absoluteTabBarY + 24, presetHalfW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Load Preset"),
                b -> Minecraft.getInstance()
                        .setScreen(new SolarisPresetLoadScreen(this, SolarisTexture::invalidateAll)))
                .bounds(boxX + 10 + presetHalfW + 6, absoluteTabBarY + 24, presetHalfW, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(boxX + 10, absoluteTabBarY + 48, boxW - 20, 18).build());
    }

    private void initDisplayTab(int x, int[] cursorY) {
        int padding = 10;
        int gap = 6;
        int availableW = boxW - (padding * 2);
        int startX = x + padding;

        int colW = availableW > 380 ? (availableW - gap * 2) / 3 :
                (availableW > 250 ? (availableW - gap) / 2 : availableW);

        headingY[0] = cursorY[0];
        cursorY[0] += HEADING_H;

        placeWidgetsFluidly(startX, cursorY[0], availableW, cursorY, gap,
                new SaturationSlider(0, 0, colW, 20),
                new WaterOpacitySlider(0, 0, colW, 20),
                new BiomeBlendSlider(0, 0, colW, 20),
                Button.builder(deepOnlyLabel(), b -> {
                    SolarisConfig.WATER_DEEP_ONLY.set(!SolarisConfig.WATER_DEEP_ONLY.get());
                    SolarisConfig.WATER_DEEP_ONLY.save();
                    b.setMessage(deepOnlyLabel());
                }).size(colW, 18).build(),
                new ContrastSlider(0, 0, colW, 20),
                new BrightnessSlider(0, 0, colW, 20),
                new FoliageBrightnessSlider(0, 0, colW, 20));

        headingY[1] = cursorY[0];
        cursorY[0] += HEADING_H;

        placeWidgetsFluidly(startX, cursorY[0], availableW, cursorY, gap,
                new IconScaleSlider(0, 0, colW, 20),
                Button.builder(labelSideLabel(), b -> {
                    SolarisConfig.LABEL_SIDE.set(SolarisConfig.LABEL_SIDE.get().next());
                    SolarisConfig.LABEL_SIDE.save();
                    b.setMessage(labelSideLabel());
                }).size(colW, 18).build(),
                Button.builder(tooltipLabel(), b -> {
                    SolarisConfig.SHOW_BLOCK_TOOLTIP.set(!SolarisConfig.SHOW_BLOCK_TOOLTIP.get());
                    SolarisConfig.SHOW_BLOCK_TOOLTIP.save();
                    b.setMessage(tooltipLabel());
                }).size(colW, 18).build(),
                Button.builder(railNetworkLabel(), b -> {
                    SolarisConfig.SHOW_RAIL_NETWORK.set(!SolarisConfig.SHOW_RAIL_NETWORK.get());
                    SolarisConfig.SHOW_RAIL_NETWORK.save();
                    b.setMessage(railNetworkLabel());
                }).size(colW, 18).build());

        headingY[2] = cursorY[0];
        cursorY[0] += HEADING_H;

        placeWidgetsFluidly(startX, cursorY[0], availableW, cursorY, gap,
                new HillshadingStrengthSlider(0, 0, colW, 20),
                new VignetteStrengthSlider(0, 0, colW, 20),
                new UnexploredDensitySlider(0, 0, colW, 20),
                new UnexploredBrightnessSlider(0, 0, colW, 20));

        headingY[3] = cursorY[0];
        cursorY[0] += HEADING_H;

        placeWidgetsFluidly(startX, cursorY[0], availableW, cursorY, gap,
                new MinimapZoomSlider(0, 0, colW, 20),
                Button.builder(mapShapeLabel(), b -> {
                    SolarisConfig.MAP_SHAPE.set(SolarisConfig.MAP_SHAPE.get().next());
                    SolarisConfig.MAP_SHAPE.save();
                    b.setMessage(mapShapeLabel());
                }).size(colW, 18).build(),
                Button.builder(minimapTimeLabel(), b -> {
                    SolarisConfig.MINIMAP_SHOW_TIME.set(!SolarisConfig.MINIMAP_SHOW_TIME.get());
                    SolarisConfig.MINIMAP_SHOW_TIME.save();
                    b.setMessage(minimapTimeLabel());
                }).size(colW, 18).build(),
                Button.builder(minimapCoordsLabel(), b -> {
                    SolarisConfig.MINIMAP_SHOW_COORDS.set(!SolarisConfig.MINIMAP_SHOW_COORDS.get());
                    SolarisConfig.MINIMAP_SHOW_COORDS.save();
                    b.setMessage(minimapCoordsLabel());
                }).size(colW, 18).build(),
                Button.builder(minimapRotateLabel(), b -> {
                    SolarisConfig.MINIMAP_ROTATE.set(!SolarisConfig.MINIMAP_ROTATE.get());
                    SolarisConfig.MINIMAP_ROTATE.save();
                    b.setMessage(minimapRotateLabel());
                }).size(colW, 18).build(),
                Button.builder(claimsMapLabel(), b -> {
                    SolarisConfig.SHOW_CLAIMS_MAP.set(!SolarisConfig.SHOW_CLAIMS_MAP.get());
                    SolarisConfig.SHOW_CLAIMS_MAP.save();
                    b.setMessage(claimsMapLabel());
                }).size(colW, 18).build(),
                Button.builder(claimsMinimapLabel(), b -> {
                    SolarisConfig.SHOW_CLAIMS_MINIMAP.set(!SolarisConfig.SHOW_CLAIMS_MINIMAP.get());
                    SolarisConfig.SHOW_CLAIMS_MINIMAP.save();
                    b.setMessage(claimsMinimapLabel());
                }).size(colW, 18).build(),
                new MapZoomMinSlider(0, 0, colW, 20),
                new MapZoomMaxSlider(0, 0, colW, 20));
    }

    private void initIntegrationsTab(int x, int[] cursorY) {
        int padding = 10;
        int gap = 6;
        int availableW = boxW - (padding * 2);
        int startX = x + padding;

        int colW = availableW > 250 ? (availableW - gap) / 2 : availableW;

        placeWidgetsFluidly(startX, cursorY[0], availableW, cursorY, gap,
                Button.builder(gtVeinsLabel(), b -> {
                    SolarisConfig.SHOW_GT_ORE_VEINS.set(!SolarisConfig.SHOW_GT_ORE_VEINS.get());
                    SolarisConfig.SHOW_GT_ORE_VEINS.save();
                    b.setMessage(gtVeinsLabel());
                }).size(colW, 18).build());
    }

    private void initWaypointsTab(int x, int[] cursorY) {
        int padding = 10;
        int gap = 6;
        int availableW = boxW - (padding * 2);
        int startX = x + padding;

        int colW = availableW > 250 ? (availableW - gap) / 2 : availableW;

        placeWidgetsFluidly(startX, cursorY[0], availableW, cursorY, gap,
                Button.builder(beamsLabel(), b -> {
                    SolarisConfig.WAYPOINT_BEAMS.set(!SolarisConfig.WAYPOINT_BEAMS.get());
                    SolarisConfig.WAYPOINT_BEAMS.save();
                    b.setMessage(beamsLabel());
                }).size(colW, 18).build(),

                Button.builder(compassLabel(), b -> {
                    SolarisConfig.WAYPOINT_COMPASS.set(!SolarisConfig.WAYPOINT_COMPASS.get());
                    SolarisConfig.WAYPOINT_COMPASS.save();
                    b.setMessage(compassLabel());
                }).size(colW, 18).build(),

                Button.builder(deathMarkersLabel(), b -> {
                    SolarisConfig.DEATH_MARKERS.set(!SolarisConfig.DEATH_MARKERS.get());
                    SolarisConfig.DEATH_MARKERS.save();
                    b.setMessage(deathMarkersLabel());
                }).size(colW, 18).build(),

                Button.builder(planShapesLabel(), b -> {
                    SolarisConfig.SHOW_PLAN_SHAPES.set(!SolarisConfig.SHOW_PLAN_SHAPES.get());
                    SolarisConfig.SHOW_PLAN_SHAPES.save();
                    b.setMessage(planShapesLabel());
                }).size(colW, 18).build());
    }

    private Component tooltipLabel() {
        boolean on = SolarisConfig.SHOW_BLOCK_TOOLTIP.get();
        return Component.literal("Tooltip: " + (on ? "ON" : "OFF"));
    }

    private Component planShapesLabel() {
        boolean on = SolarisConfig.SHOW_PLAN_SHAPES.get();
        return Component.literal("Plan Shapes: " + (on ? "ON" : "OFF"));
    }

    private Component gtVeinsLabel() {
        boolean on = SolarisConfig.SHOW_GT_ORE_VEINS.get();
        return Component.literal("GT Ore Veins: " + (on ? "ON" : "OFF"));
    }

    private Component deepOnlyLabel() {
        boolean on = SolarisConfig.WATER_DEEP_ONLY.get();
        return Component.literal(
                "Deep Water Only: " + (on ? "ON (below Y" + SolarisConfig.WATER_DEEP_Y_THRESHOLD.get() + ")" : "OFF"));
    }

    private Component labelSideLabel() {
        return Component.literal("Label Position: " + SolarisConfig.LABEL_SIDE.get().label());
    }

    private Component mapShapeLabel() {
        return Component.literal("Map Shape: " + SolarisConfig.MAP_SHAPE.get().label());
    }

    private Component minimapRotateLabel() {
        boolean on = SolarisConfig.MINIMAP_ROTATE.get();
        return Component.literal("Minimap Rotate: " + (on ? "ON" : "OFF"));
    }

    private Component railNetworkLabel() {
        boolean on = SolarisConfig.SHOW_RAIL_NETWORK.get();
        return Component.literal("Rail Lines: " + (on ? "ON" : "OFF"));
    }

    private Component minimapTimeLabel() {
        boolean on = SolarisConfig.MINIMAP_SHOW_TIME.get();
        return Component.literal("Minimap Time: " + (on ? "ON" : "OFF"));
    }

    private Component minimapCoordsLabel() {
        boolean on = SolarisConfig.MINIMAP_SHOW_COORDS.get();
        return Component.literal("Minimap Coords: " + (on ? "ON" : "OFF"));
    }

    private Component claimsMapLabel() {
        boolean on = SolarisConfig.SHOW_CLAIMS_MAP.get();
        return Component.literal("Claims (Map): " + (on ? "ON" : "OFF"));
    }

    private Component claimsMinimapLabel() {
        boolean on = SolarisConfig.SHOW_CLAIMS_MINIMAP.get();
        return Component.literal("Claims (Minimap): " + (on ? "ON" : "OFF"));
    }

    private Component beamsLabel() {
        boolean on = SolarisConfig.WAYPOINT_BEAMS.get();
        return Component.literal("Waypoint Beams: " + (on ? "ON" : "OFF"));
    }

    private Component compassLabel() {
        boolean on = SolarisConfig.WAYPOINT_COMPASS.get();
        return Component.literal("Waypoint Compass: " + (on ? "ON" : "OFF"));
    }

    private Component deathMarkersLabel() {
        boolean on = SolarisConfig.DEATH_MARKERS.get();
        return Component.literal("Death Markers: " + (on ? "ON" : "OFF"));
    }

    @Override
    public void render(GuiGraphics g, int rawMx, int rawMy, float pt) {
        int mx = Math.round(rawMx / uiScale);
        int my = Math.round(rawMy / uiScale);

        if (parent instanceof SolarisMapScreen mapScreen) {
            mapScreen.renderMapBackground(g);
            g.fill(0, 0, width, height, 0xE0101014);
        } else {
            renderBackground(g);
        }

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        int x = boxX;
        int y = boxY;
        g.fill(x, y, x + boxW, y + boxH, C_BG);
        ModernPanel.draw(g, x - 8, y - 8, boxW + 16, boxH + 16, C_BORDER);

        g.fill(x, y, x + boxW, y + HEADER_H, C_HEADER);
        g.fill(x, y + HEADER_H, x + boxW, y + HEADER_H + 1, C_BORDER2);
        g.drawCenteredString(font, title, x + boxW / 2, y + 6, C_ACCENT);

        if (activeTab == Tab.DISPLAY) {
            for (int i = 0; i < GROUP_COUNT; i++) {
                int hy = headingY[i];
                g.drawString(font, GROUP_HEADINGS[i], x + 10, hy + 2, C_DIM, false);
                g.fill(x + 10 + font.width(GROUP_HEADINGS[i]) + 6, hy + 6, x + boxW - 10, hy + 7, C_BORDER2);
            }
        }

        super.render(g, mx, my, pt);

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int btn) {
        return super.mouseClicked(rawMx / uiScale, rawMy / uiScale, btn);
    }

    @Override
    public boolean mouseDragged(double rawMx, double rawMy, int btn, double dragX, double dragY) {
        return super.mouseDragged(rawMx / uiScale, rawMy / uiScale, btn, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseReleased(double rawMx, double rawMy, int btn) {
        return super.mouseReleased(rawMx / uiScale, rawMy / uiScale, btn);
    }

    @Override
    public boolean mouseScrolled(double rawMx, double rawMy, double delta) {
        return super.mouseScrolled(rawMx / uiScale, rawMy / uiScale, delta);
    }

    @Override
    public void onClose() {
        SolarisConfig.SATURATION.save();
        SolarisConfig.CONTRAST.save();
        SolarisConfig.BRIGHTNESS.save();
        SolarisConfig.FOLIAGE_BRIGHTNESS.save();
        SolarisConfig.WATER_OPACITY.save();
        SolarisConfig.WATER_BLEND_RADIUS.save();
        SolarisConfig.WATER_DEEP_ONLY.save();
        SolarisConfig.WAYPOINT_ICON_SCALE.save();
        SolarisConfig.SHOW_BLOCK_TOOLTIP.save();
        SolarisConfig.SHOW_MOBS.save();
        SolarisConfig.SHOW_GT_ORE_VEINS.save();
        SolarisConfig.LABEL_SIDE.save();
        SolarisConfig.MAP_SHAPE.save();
        SolarisConfig.WAYPOINT_BEAMS.save();
        SolarisConfig.WAYPOINT_COMPASS.save();
        SolarisConfig.DEATH_MARKERS.save();
        SolarisConfig.SHOW_PLAN_SHAPES.save();
        SolarisConfig.HILLSHADING.save();
        SolarisConfig.HILLSHADING_STRENGTH.save();
        SolarisConfig.VIGNETTE.save();
        SolarisConfig.VIGNETTE_STRENGTH.save();
        SolarisConfig.MINIMAP_ROTATE.save();
        SolarisConfig.SHOW_RAIL_NETWORK.save();
        SolarisConfig.MINIMAP_ZOOM.save();
        SolarisConfig.SHOW_CHUNK_GRID.save();
        SolarisConfig.MINIMAP_SHOW_TIME.save();
        SolarisConfig.MINIMAP_SHOW_COORDS.save();
        SolarisConfig.UNEXPLORED_STYLE.save();
        SolarisConfig.UNEXPLORED_DENSITY.save();
        SolarisConfig.UNEXPLORED_BRIGHTNESS.save();
        SolarisConfig.SHOW_CLAIMS_MAP.save();
        SolarisConfig.SHOW_CLAIMS_MINIMAP.save();
        SolarisConfig.ZOOM_MIN.save();
        SolarisConfig.ZOOM_MAX.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class SaturationSlider extends AbstractSliderButton {

        SaturationSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.SATURATION.get() / 2.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Saturation: " + Math.round(value * 200) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.SATURATION.set(value * 2.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class ContrastSlider extends AbstractSliderButton {

        ContrastSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.CONTRAST.get() / 3.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Contrast: " + Math.round(value * 300) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.CONTRAST.set(value * 3.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class BrightnessSlider extends AbstractSliderButton {

        BrightnessSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.BRIGHTNESS.get() / 2.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Brightness: " + Math.round(value * 200) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.BRIGHTNESS.set(value * 2.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class FoliageBrightnessSlider extends AbstractSliderButton {

        FoliageBrightnessSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.FOLIAGE_BRIGHTNESS.get() / 2.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Foliage Brightness: " + Math.round(value * 200) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.FOLIAGE_BRIGHTNESS.set(value * 2.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class UnexploredDensitySlider extends AbstractSliderButton {

        UnexploredDensitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.UNEXPLORED_DENSITY.get() - 0.25) / 3.75);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Unexplored Density: " + Math.round((0.25 + value * 3.75) * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.UNEXPLORED_DENSITY.set(0.25 + value * 3.75);
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class UnexploredBrightnessSlider extends AbstractSliderButton {

        UnexploredBrightnessSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.UNEXPLORED_BRIGHTNESS.get() - 0.25) / 2.25);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Unexplored Bright: " + Math.round((0.25 + value * 2.25) * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.UNEXPLORED_BRIGHTNESS.set(0.25 + value * 2.25);
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class WaterOpacitySlider extends AbstractSliderButton {

        WaterOpacitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.WATER_OPACITY.get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Water Opacity: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.WATER_OPACITY.set(value);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class BiomeBlendSlider extends AbstractSliderButton {

        private static final int STEP = 4;
        private static final int MAX_STEPS = 4;

        BiomeBlendSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.WATER_BLEND_RADIUS.get() / STEP) / (double) MAX_STEPS);
            updateMessage();
        }

        private int radius() {
            return (int) Math.round(value * MAX_STEPS) * STEP;
        }

        @Override
        protected void updateMessage() {
            int radius = radius();
            setMessage(Component.literal("Biome Blending: " + (radius == 0 ? "OFF (sharper)" : radius + " blocks")));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.WATER_BLEND_RADIUS.set(radius());
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class IconScaleSlider extends AbstractSliderButton {

        IconScaleSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.WAYPOINT_ICON_SCALE.get() - 0.5) / 2.5);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double scale = 0.5 + value * 2.5;
            setMessage(Component.literal("Waypoint Icon Size: " + Math.round(scale * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.WAYPOINT_ICON_SCALE.set(0.5 + value * 2.5);
        }
    }

    private static class MinimapZoomSlider extends AbstractSliderButton {

        MinimapZoomSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.MINIMAP_ZOOM.get() - 1.0) / 7.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double zoom = 1.0 + value * 7.0;
            setMessage(Component.literal("Minimap Zoom: " + String.format("%.1f", zoom) + "x"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.MINIMAP_ZOOM.set(1.0 + value * 7.0);
        }
    }

    private static class MapZoomMinSlider extends AbstractSliderButton {

        MapZoomMinSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.ZOOM_MIN.get() - 0.05) / 0.95);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double zoomMin = 0.05 + value * 0.95;
            setMessage(Component.literal("Map Zoom Out Limit: " + String.format("%.2f", zoomMin) + "x"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.ZOOM_MIN.set(0.05 + value * 0.95);
        }
    }

    private static class MapZoomMaxSlider extends AbstractSliderButton {

        MapZoomMaxSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.ZOOM_MAX.get() - 1.0) / 47.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double zoomMax = 1.0 + value * 47.0;
            setMessage(Component.literal("Map Zoom In Limit: " + String.format("%.0f", zoomMax) + "x"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.ZOOM_MAX.set(1.0 + value * 47.0);
        }
    }

    private static class HillshadingStrengthSlider extends AbstractSliderButton {

        HillshadingStrengthSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.HILLSHADING_STRENGTH.get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Hillshading Strength: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.HILLSHADING_STRENGTH.set(value);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class VignetteStrengthSlider extends AbstractSliderButton {

        VignetteStrengthSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.VIGNETTE_STRENGTH.get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Vignette Strength: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.VIGNETTE_STRENGTH.set(value);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }
}
