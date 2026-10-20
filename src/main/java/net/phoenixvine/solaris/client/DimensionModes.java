package net.phoenixvine.solaris.client;

import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a dimension is mapped as a layer-by-layer "cave" view (like the Nether) or as an
 * open-sky surface map. Defaults to the dimension's own ceiling flag, but the config can force
 * either mode so modded Nether-like or End-like dimensions can be grouped with the vanilla ones.
 */
public final class DimensionModes {

    private DimensionModes() {}

    public static boolean isCaveDimension(Level level) {
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        if (matches(SolarisConfig.SURFACE_MODE_DIMENSIONS.get(), id)) return false;
        if (matches(SolarisConfig.CAVE_MODE_DIMENSIONS.get(), id)) return true;
        return level.dimensionType().hasCeiling();
    }

    // Entries are dimension ids ("mod:dimension") or a whole namespace ("mod:*").
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
