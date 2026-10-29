package net.phoenixvine.solaris.client.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.MapPage;
import net.phoenixvine.solaris.client.render.MapProjection;
import net.phoenixvine.solaris.client.render.MapViewport;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.SmoothShapes;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BG;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_TEXT;

@OnlyIn(Dist.CLIENT)
public class SolarisPageScreen extends Screen {

    private static final int MARGIN = 20;
    private static final int TOP = 24;

    private final MapPage page;
    private final @Nullable Screen parent;
    private final MapViewport viewport = new MapViewport(0.05f, 24.0f);
    private boolean dragging;
    private boolean fitted;
    private int fittedVariant = -1;

    public SolarisPageScreen(MapPage page, @Nullable Screen parent) {
        super(page.title());
        this.page = page;
        this.parent = parent;
    }

    private int left() {
        return MARGIN;
    }

    private int top() {
        return TOP;
    }

    private int right() {
        return width - MARGIN;
    }

    private int bottom() {
        return height - MARGIN;
    }

    private double centreX() {
        return left() + (right() - left()) / 2.0;
    }

    private double centreY() {
        return top() + (bottom() - top()) / 2.0;
    }

    private void fit() {
        double fw = right() - left();
        double fh = bottom() - top();
        float target = (float) Math.max(0.05, Math.min(fw / page.width(), fh / page.height()) * 0.96);
        viewport.setOffset(0, 0);

        double steps = Math.log(target / viewport.getZoom()) / Math.log(1.1);
        viewport.adjustZoomToAnchor(steps, (int) centreX(), (int) centreY(), (int) centreX(), (int) centreY());
        viewport.setOffset(0, 0);
        fitted = true;
        fittedVariant = page.selectedVariant();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, C_BG);
    }

    private MapProjection projection() {
        int ox = (int) centreX();
        int oy = (int) centreY();
        return new MapProjection() {

            @Override
            public double screenX(double x) {
                return viewport.toScreenX(x, ox);
            }

            @Override
            public double screenY(double y) {
                return viewport.toScreenY(y, oy);
            }

            @Override
            public float zoom() {
                return viewport.getZoom();
            }
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!fitted || fittedVariant != page.selectedVariant()) fit();

        renderBackground(g);
        g.fill(left() - 4, top() - 4, right() + 4, bottom() + 4, C_PANEL);
        ModernPanel.draw(g, left() - 4, top() - 4, right() - left() + 8, bottom() - top() + 8, C_BORDER);

        MapProjection projection = projection();
        g.enableScissor(left(), top(), right(), bottom());
        page.render(g, left(), top(), right() - left(), bottom() - top(), projection, partialTick);

        MapPage.Marker hovered = null;
        List<MapPage.Marker> markers = new ArrayList<>(page.markers());
        for (MapPage.Marker marker : markers) {
            int sx = (int) projection.screenX(marker.x());
            int sy = (int) projection.screenY(marker.y());
            if (sx < left() - 8 || sx > right() + 8 || sy < top() - 8 || sy > bottom() + 8) continue;
            SmoothShapes.drawCircle(g, sx, sy, 5, 0xCC000000);
            SmoothShapes.drawCircle(g, sx, sy, 4, 0xFF000000 | marker.color());
            if (!marker.icon().isEmpty()) g.renderItem(marker.icon(), sx - 8, sy - 20);
            if (Math.abs(mx - sx) <= 7 && Math.abs(my - sy) <= 7) hovered = marker;
        }
        g.disableScissor();

        g.drawCenteredString(font, title, width / 2, 8, C_ACCENT);
        List<Component> variants = page.variants();
        if (variants.size() > 1) {
            Component label = variants.get(Math.floorMod(page.selectedVariant(), variants.size()));
            String text = "<  " + label.getString() + "  >";
            g.drawCenteredString(font, text, width / 2, 8 + 11, C_TEXT);
        }
        Component footer = page.footer();
        String hint = footer != null ? footer.getString() :
                "drag: pan   scroll: zoom" + (variants.size() > 1 ? "   Q / E: switch" : "") + "   Esc: back";
        g.drawCenteredString(font, hint, width / 2, bottom() + 8, C_TEXT);

        PageTabs.render(g, font, mx, my, page);
        page.renderOverlay(g, left(), top(), right() - left(), bottom() - top(), mx, my);

        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(hovered.label());
            lines.addAll(hovered.tooltip());
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && PageTabs.click(mx, my, font, page, parent)) return true;
        if (page.mouseClicked(mx, my, button)) return true;
        if (button == 0 && variantClick(mx, my)) return true;
        if (button == 0 && mx >= left() && mx <= right() && my >= top() && my <= bottom()) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean variantClick(double mx, double my) {
        List<Component> variants = page.variants();
        if (variants.size() < 2 || my < 17 || my > 29) return false;
        if (mx < width / 2.0 - 60 || mx > width / 2.0 + 60) return false;
        shift(mx < width / 2.0 ? -1 : 1);
        return true;
    }

    private void shift(int by) {
        int count = page.variants().size();
        if (count < 2) return;
        page.selectVariant(Math.floorMod(page.selectedVariant() + by, count));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            viewport.pan(dx, dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        viewport.adjustZoomToAnchor(scrollY, mx, my, (int) centreX(), (int) centreY());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (page.keyPressed(key, scan, mods)) return true;
        if (key == GLFW.GLFW_KEY_Q) {
            shift(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_E) {
            shift(1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
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
