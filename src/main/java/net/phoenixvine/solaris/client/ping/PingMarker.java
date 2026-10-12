package net.phoenixvine.solaris.client.ping;

import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.solaris.client.render.SmoothShapes;

public final class PingMarker {

    private static final int RGB = 0xFFD84D;

    private PingMarker() {}

    public static void draw(GuiGraphics g, int cx, int cy, int baseRadius, PingTracker.Ping ping) {
        long now = System.currentTimeMillis();
        if (ping.expiresAtMillis() - now < 3000L && (now / 250L) % 2L == 0L) return;

        float phase = ((now - ping.createdAtMillis()) % 1000L) / 1000f;
        int radius = baseRadius + Math.round(phase * baseRadius);
        int alpha = Math.round(255f * (1f - phase * 0.8f));
        SmoothShapes.drawRing(g, cx, cy, radius, (alpha << 24) | RGB);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000 | RGB);
    }
}
