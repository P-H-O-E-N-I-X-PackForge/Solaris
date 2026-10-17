package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.phoenixvine.solaris.client.render.MinimapShape;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies the settings that have a Solaris equivalent out of Xaero's Minimap client profile
 * (config/xaero/minimap/profiles/&lt;profile&gt;.cfg and its info display file). Anything without a
 * counterpart is ignored.
 */
public final class XaeroSettingsImporter {

    private XaeroSettingsImporter() {}

    /** Returns the names of the settings applied, or null when no Xaero minimap profile exists. */
    public static List<String> apply() {
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("xaero")
                .resolve("minimap");
        String profile = readCfg(root.resolve("client.cfg")).getOrDefault("current_profile", "default");
        Path profileFile = root.resolve("profiles").resolve(profile + ".cfg");
        if (!Files.isRegularFile(profileFile)) return null;

        Map<String, String> values = readCfg(profileFile);
        List<String> applied = new ArrayList<>();

        String shape = values.get("minimap_shape");
        if (shape != null) {
            SolarisConfig.MINIMAP_SHAPE.set("1".equals(shape) ? MinimapShape.CIRCLE : MinimapShape.SQUARE);
            SolarisConfig.MINIMAP_SHAPE.save();
            applied.add("minimap shape");
        }

        Boolean northLocked = bool(values, "minimap_north_locked");
        if (northLocked != null) {
            SolarisConfig.MINIMAP_ROTATE.set(!northLocked);
            SolarisConfig.MINIMAP_ROTATE.save();
            applied.add("minimap rotation");
        }

        Boolean minimapWaypoints = bool(values, "waypoints_on_minimap");
        if (minimapWaypoints != null) {
            SolarisConfig.MINIMAP_SHOW_WAYPOINTS.set(minimapWaypoints);
            SolarisConfig.MINIMAP_SHOW_WAYPOINTS.save();
            applied.add("minimap waypoints");
        }

        Boolean worldWaypoints = bool(values, "waypoints_in_world");
        if (worldWaypoints != null) {
            SolarisConfig.WAYPOINT_BEAMS.set(worldWaypoints);
            SolarisConfig.WAYPOINT_BEAMS.save();
            applied.add("waypoint beams");
        }

        Boolean deathpoints = bool(values, "deathpoints");
        if (deathpoints != null) {
            SolarisConfig.DEATH_MARKERS.set(deathpoints);
            SolarisConfig.DEATH_MARKERS.save();
            applied.add("death markers");
        }

        String chunkGrid = values.get("minimap_chunk_grid");
        if (chunkGrid != null) {
            try {
                SolarisConfig.SHOW_CHUNK_GRID.set(Integer.parseInt(chunkGrid.trim()) >= 0);
                SolarisConfig.SHOW_CHUNK_GRID.save();
                applied.add("chunk grid");
            } catch (NumberFormatException ignored) {}
        }

        Boolean radar = bool(values, "display_radar");
        if (radar != null) {
            SolarisConfig.SHOW_MOBS.set(radar);
            SolarisConfig.SHOW_MOBS.save();
            applied.add("show mobs");
        }

        Boolean trackedPlayers = bool(values, "tracked_players_on_minimap");
        if (trackedPlayers != null) {
            SolarisConfig.SHOW_PLAYERS.set(trackedPlayers);
            SolarisConfig.SHOW_PLAYERS.save();
            applied.add("show players");
        }

        Boolean lighting = bool(values, "minimap_lighting");
        if (lighting != null) {
            // Lighting off means no night darkening; on keeps whatever darkness is already set
            // (restoring Solaris's default if it had been zeroed).
            double current = SolarisConfig.NIGHT_MODE_STRENGTH.get();
            SolarisConfig.NIGHT_MODE_STRENGTH.set(lighting ? (current == 0.0 ? 0.35 : current) : 0.0);
            SolarisConfig.NIGHT_MODE_STRENGTH.save();
            applied.add("night darkness");
        }

        String slopes = values.get("minimap_terrain_slopes");
        if (slopes != null) {
            try {
                SolarisConfig.HILLSHADING.set(Integer.parseInt(slopes.trim()) > 0);
                SolarisConfig.HILLSHADING.save();
                applied.add("relief shading");
            } catch (NumberFormatException ignored) {}
        }

        Boolean blending = bool(values, "minimap_biome_blending");
        if (blending != null) {
            int current = SolarisConfig.WATER_BLEND_RADIUS.get();
            SolarisConfig.WATER_BLEND_RADIUS.set(blending ? (current == 0 ? 8 : current) : 0);
            SolarisConfig.WATER_BLEND_RADIUS.save();
            applied.add("biome color blending");
        }

        applyInfoDisplay(root.resolve("profiles").resolve("info_display_config").resolve(profile + ".cfg.txt"),
                applied);
        return applied;
    }

    // Lines look like "infoDisplay:<id>:<enabled>:<colour>:<...>".
    private static void applyInfoDisplay(Path file, List<String> applied) {
        if (!Files.isRegularFile(file)) return;
        Map<String, Boolean> enabled = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file)) {
                String[] parts = line.split(":");
                if (parts.length >= 3 && parts[0].equals("infoDisplay")) {
                    enabled.put(parts[1], Boolean.parseBoolean(parts[2]));
                }
            }
        } catch (IOException e) {
            return;
        }
        if (enabled.isEmpty()) return;

        SolarisConfig.MINIMAP_SHOW_COORDS.set(enabled.getOrDefault("coords", false));
        SolarisConfig.MINIMAP_SHOW_COORDS.save();
        SolarisConfig.MINIMAP_SHOW_BIOME.set(enabled.getOrDefault("biome", false));
        SolarisConfig.MINIMAP_SHOW_BIOME.save();
        SolarisConfig.MINIMAP_SHOW_TIME.set(
                enabled.getOrDefault("time", false) || enabled.getOrDefault("real_time", false));
        SolarisConfig.MINIMAP_SHOW_TIME.save();
        applied.add("minimap info labels");
    }

    private static Map<String, String> readCfg(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq > 0) values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        } catch (IOException ignored) {}
        return values;
    }

    private static Boolean bool(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? null : Boolean.valueOf(value);
    }
}
