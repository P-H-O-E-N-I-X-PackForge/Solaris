package net.phoenixvine.solaris.client;

import net.minecraft.util.Mth;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public final class SolarisThemeUtils {

    private SolarisThemeUtils() {}

    public static int C_BG;
    public static int C_PANEL;
    public static int C_HEADER;
    public static int C_BORDER;
    public static int C_BORDER2;
    public static int C_ACCENT;
    public static int C_TEXT;
    public static int C_DIM;
    public static int C_FAINT;

    private static Integer accentOverride;

    /**
     * Lets another mod tint Solaris's accent colour (for example per dimension or per faction)
     * without replacing the whole shared theme. Pass null to go back to the theme's own accent.
     */
    public static void setAccentOverride(Integer argb) {
        if (java.util.Objects.equals(accentOverride, argb)) return;
        accentOverride = argb;
        refreshCache();
    }

    public static void refreshCache() {
        PhoenixTheme current = PhoenixTheme.current();
        if (current == null) return;

        C_BG = current.bg.getColor();
        C_PANEL = current.panel.getColor();
        C_HEADER = current.header.getColor();
        C_BORDER = current.border.getColor();
        C_ACCENT = accentOverride != null ? accentOverride : current.accent.getColor();
        C_TEXT = current.text.getColor();
        C_DIM = current.textDim.getColor();
        C_FAINT = current.textFaint.getColor();

        int a = (C_BORDER >> 24) & 0xFF;
        int r = Mth.clamp(((C_BORDER >> 16) & 0xFF) - 12, 0, 255);
        int g = Mth.clamp(((C_BORDER >> 8) & 0xFF) - 12, 0, 255);
        int b = Mth.clamp((C_BORDER & 0xFF) - 12, 0, 255);
        C_BORDER2 = (a << 24) | (r << 16) | (g << 8) | b;
    }

    static {
        refreshCache();
    }
}
