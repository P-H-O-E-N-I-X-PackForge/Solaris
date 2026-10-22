package net.phoenixvine.solaris.client;

import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.List;
import java.util.Locale;

public final class DimensionModes {

    private DimensionModes() {}

    public static boolean isCaveDimension(Level level) {
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        if (matches(SolarisConfig.SURFACE_MODE_DIMENSIONS.get(), id)) return false;
        if (matches(SolarisConfig.CAVE_MODE_DIMENSIONS.get(), id)) return true;
        return level.dimensionType().hasCeiling();
    }

    private static boolean matches(List<? extends String> entries, String dimensionId) {
        for (String entry : entries) {
            String pattern = entry.trim().toLowerCase(Locale.ROOT);
            if (pattern.endsWith(":*")) {
                if (dimensionId.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
            } else if (pattern.equals(dimensionId)) {
                return true;
            }
        }
        return false;
    }
}
