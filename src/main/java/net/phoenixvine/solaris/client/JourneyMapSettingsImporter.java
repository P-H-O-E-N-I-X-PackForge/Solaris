package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.phoenixvine.solaris.client.render.MinimapShape;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies the settings that have a Solaris equivalent out of JourneyMap's config files in the game
 * directory (journeymap/config/&lt;version&gt;/*.config). Anything without a counterpart is ignored.
 */
public final class JourneyMapSettingsImporter {

    private JourneyMapSettingsImporter() {}

    /** Returns the names of the settings applied, or null when no JourneyMap config exists. */
    public static List<String> apply() {
        Minecraft mc = Minecraft.getInstance();
        Path configDir = newestConfigDir(mc.gameDirectory.toPath().resolve("journeymap").resolve("config"));
        if (configDir == null) return null;

        List<String> applied = new ArrayList<>();
        JsonObject minimap = activeMinimap(configDir);
        if (minimap != null) applyMinimap(mc, minimap, applied);
        applyFullmap(read(configDir.resolve("journeymap.fullmap.config")), applied);
        applyWaypoints(read(configDir.resolve("journeymap.waypoint.config")), applied);

        return applied;
    }

    private static void applyMinimap(Minecraft mc, JsonObject minimap, List<String> applied) {
        String shape = string(minimap, "shape");
        if (shape != null) {
            SolarisConfig.MINIMAP_SHAPE
                    .set("Circle".equalsIgnoreCase(shape) ? MinimapShape.CIRCLE : MinimapShape.SQUARE);
            SolarisConfig.MINIMAP_SHAPE.save();
            applied.add("minimap shape");
        }

        Double sizePercent = number(minimap, "sizePercent");
        if (sizePercent != null) {
            int size = Mth.clamp((int) Math.round(sizePercent / 100.0 * mc.getWindow().getGuiScaledHeight()), 32, 512);
            SolarisConfig.MINIMAP_SIZE.set(size);
            SolarisConfig.MINIMAP_SIZE.save();
            applied.add("minimap size");
        }

        String orientation = string(minimap, "orientation");
        if (orientation != null) {
            SolarisConfig.MINIMAP_ROTATE.set("PlayerHeading".equalsIgnoreCase(orientation));
            SolarisConfig.MINIMAP_ROTATE.save();
            applied.add("minimap rotation");
        }

        Boolean showWaypoints = bool(minimap, "showWaypoints");
        if (showWaypoints != null) {
            SolarisConfig.MINIMAP_SHOW_WAYPOINTS.set(showWaypoints);
            SolarisConfig.MINIMAP_SHOW_WAYPOINTS.save();
            applied.add("minimap waypoints");
        }

        Double iconScale = number(minimap, "waypointIconScale");
        if (iconScale != null) {
            SolarisConfig.WAYPOINT_ICON_SCALE.set(Mth.clamp(iconScale, 0.5, 3.0));
            SolarisConfig.WAYPOINT_ICON_SCALE.save();
            applied.add("waypoint icon scale");
        }

        Boolean showMobs = bool(minimap, "showMobs");
        if (showMobs != null) {
            SolarisConfig.SHOW_MOBS.set(showMobs);
            SolarisConfig.SHOW_MOBS.save();
            applied.add("show mobs");
        }

        Boolean showPlayers = bool(minimap, "showPlayers");
        if (showPlayers != null) {
            SolarisConfig.SHOW_PLAYERS.set(showPlayers);
            SolarisConfig.SHOW_PLAYERS.save();
            applied.add("show players");
        }

        boolean time = false;
        boolean coords = false;
        boolean biome = false;
        boolean anyLabel = false;
        for (int i = 1; i <= 4; i++) {
            String label = string(minimap, "info" + i + "Label");
            if (label == null) continue;
            anyLabel = true;
            String lower = label.toLowerCase();
            time |= lower.contains("time");
            coords |= lower.contains("location");
            biome |= lower.contains("biome");
        }
        if (anyLabel) {
            SolarisConfig.MINIMAP_SHOW_TIME.set(time);
            SolarisConfig.MINIMAP_SHOW_TIME.save();
            SolarisConfig.MINIMAP_SHOW_COORDS.set(coords);
            SolarisConfig.MINIMAP_SHOW_COORDS.save();
            SolarisConfig.MINIMAP_SHOW_BIOME.set(biome);
            SolarisConfig.MINIMAP_SHOW_BIOME.save();
            applied.add("minimap info labels");
        }
    }

    private static void applyFullmap(JsonObject fullmap, List<String> applied) {
        if (fullmap == null) return;
        Boolean showGrid = bool(fullmap, "showGrid");
        if (showGrid != null) {
            SolarisConfig.SHOW_CHUNK_GRID.set(showGrid);
            SolarisConfig.SHOW_CHUNK_GRID.save();
            applied.add("chunk grid");
        }
    }

    private static void applyWaypoints(JsonObject waypoints, List<String> applied) {
        if (waypoints == null) return;

        Boolean beacons = bool(waypoints, "beaconEnabled");
        if (beacons != null) {
            SolarisConfig.WAYPOINT_BEAMS.set(beacons);
            SolarisConfig.WAYPOINT_BEAMS.save();
            applied.add("waypoint beams");
        }

        Boolean deathpoints = bool(waypoints, "createDeathpoints");
        if (deathpoints != null) {
            SolarisConfig.DEATH_MARKERS.set(deathpoints);
            SolarisConfig.DEATH_MARKERS.save();
            applied.add("death markers");
        }
    }

    private static JsonObject activeMinimap(Path configDir) {
        JsonObject first = read(configDir.resolve("journeymap.minimap.config"));
        JsonObject second = read(configDir.resolve("journeymap.minimap2.config"));
        if (first != null && (second == null || !Boolean.FALSE.equals(bool(first, "active")))) return first;
        return second;
    }

    private static Path newestConfigDir(Path configRoot) {
        if (!Files.isDirectory(configRoot)) return null;
        try (Stream<Path> children = Files.list(configRoot)) {
            return children.filter(Files::isDirectory)
                    .max(JourneyMapSettingsImporter::compareVersions)
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // "5.10" must sort above "5.9", so compare numerically per segment.
    private static int compareVersions(Path a, Path b) {
        List<Integer> left = versionKey(a);
        List<Integer> right = versionKey(b);
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int cmp = Integer.compare(left.get(i), right.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(left.size(), right.size());
    }

    private static List<Integer> versionKey(Path dir) {
        List<Integer> key = new ArrayList<>();
        for (String part : dir.getFileName().toString().split("\\.")) {
            try {
                key.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                key.add(-1);
            }
        }
        return key;
    }

    private static JsonObject read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            StringBuilder json = new StringBuilder();
            for (String line : Files.readAllLines(file)) {
                if (!line.trim().startsWith("//")) json.append(line).append('\n');
            }
            return JsonParser.parseString(json.toString()).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static Boolean bool(JsonObject obj, String key) {
        String value = string(obj, key);
        return value == null ? null : Boolean.valueOf(value);
    }

    private static Double number(JsonObject obj, String key) {
        String value = string(obj, key);
        if (value == null) return null;
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
