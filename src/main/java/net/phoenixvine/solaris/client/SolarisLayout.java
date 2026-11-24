package net.phoenixvine.solaris.client;

import net.phoenixvine.solaris.config.SolarisConfig;

/** Where the map's drawing area starts: right at the window's edge when fullscreen, a framed margin in otherwise. */
public final class SolarisLayout {

    private static final int FRAMED_MARGIN = 20;

    private SolarisLayout() {}

    public static int mapMargin() {
        return SolarisConfig.FULLSCREEN_MAP.get() ? 0 : FRAMED_MARGIN;
    }
}
