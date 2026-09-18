package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;

public final class ModernPanel {

    private static final int THICKNESS = 2;

    // How far each edge's channel shifts toward white (top/left) or black (bottom/right), giving
    // the thick flat border a subtle raised bevel instead of reading as a single flat-color block.
    public static final int SHADE_AMOUNT = 28;

    private ModernPanel() {}

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int color) {
        int t = Math.min(THICKNESS, Math.min(w, h) / 2);
        int light = shade(color, SHADE_AMOUNT);
        int dark = shade(color, -SHADE_AMOUNT);
        g.fill(x, y, x + w, y + t, light);
        g.fill(x, y + h - t, x + w, y + h, dark);
        g.fill(x, y + t, x + t, y + h - t, light);
        g.fill(x + w - t, y + t, x + w, y + h - t, dark);
    }

    public static int shade(int argb, int amount) {
        int a = argb >>> 24;
        int r = clampChannel(((argb >> 16) & 0xFF) + amount);
        int gCh = clampChannel(((argb >> 8) & 0xFF) + amount);
        int b = clampChannel((argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (gCh << 8) | b;
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
