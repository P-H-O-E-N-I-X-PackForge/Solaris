package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class WaypointImporter {

    private static final String JM_CATEGORY = "JourneyMap";
    private static final String XAERO_CATEGORY = "Xaero's Minimap";
    private static final String DEATH_CATEGORY = "Deaths";

    private static final int[] XAERO_COLORS = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    private WaypointImporter() {}

    public record Result(int journeyMap, int xaero, int duplicates) {

        public int total() {
            return journeyMap + xaero;
        }
    }

    public static Result importForCurrentWorld() {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        Set<String> worldNames = currentWorldNames(mc);

        List<Waypoint> found = new ArrayList<>();
        found.addAll(readJourneyMap(gameDir, mc, worldNames));
        found.addAll(readXaero(gameDir, mc, worldNames));

        int jm = 0;
        int xaero = 0;
        int duplicates = 0;
        for (Waypoint w : found) {
            if (isDuplicate(w)) {
                duplicates++;
                continue;
            }
            WaypointManager.add(w);
            if (w.category.startsWith(JM_CATEGORY)) jm++;
            else xaero++;
        }
        return new Result(jm, xaero, duplicates);
    }

    public static String describe(Result result) {
        if (result.total() == 0 && result.duplicates() == 0) {
            return "No JourneyMap or Xaero's waypoints found for this world.";
        }
        StringBuilder sb = new StringBuilder("Imported ").append(result.total()).append(" waypoint(s)");
        if (result.total() > 0) {
            sb.append(" (JourneyMap: ").append(result.journeyMap()).append(", Xaero's: ").append(result.xaero())
                    .append(")");
        }
        if (result.duplicates() > 0) sb.append(", skipped ").append(result.duplicates()).append(" already present");
        return sb.toString();
    }

    private static boolean isDuplicate(Waypoint candidate) {
        for (Waypoint existing : WaypointManager.getAll()) {
            if (existing.x == candidate.x && existing.y == candidate.y && existing.z == candidate.z &&
                    existing.dimension.equals(candidate.dimension) && existing.name.equals(candidate.name)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> currentWorldNames(Minecraft mc) {
        Set<String> names = new LinkedHashSet<>();
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            addNameVariants(names, server.ip);
            int colon = server.ip.indexOf(':');
            if (colon > 0) addNameVariants(names, server.ip.substring(0, colon));
            addNameVariants(names, server.name);
        } else if (mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            if (root.getFileName() != null) addNameVariants(names, root.getFileName().toString());
            addNameVariants(names, mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        return names;
    }

    private static void addNameVariants(Set<String> names, String raw) {
        if (raw == null || raw.isBlank()) return;
        names.add(raw);
        names.add(raw.replaceAll("[^a-zA-Z0-9._ -]", "_"));
        names.add(raw.replace(':', '_'));
        names.add(raw.replace(':', '§'));
    }

    private static Path findChild(Path parent, Set<String> candidates, String requiredPrefix) {
        if (!Files.isDirectory(parent)) return null;
        try (Stream<Path> children = Files.list(parent)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (!Files.isDirectory(child)) continue;
                String name = child.getFileName().toString();
                if (!requiredPrefix.isEmpty()) {
                    if (!name.startsWith(requiredPrefix)) continue;
                    name = name.substring(requiredPrefix.length());
                }
                for (String candidate : candidates) {
                    if (name.equalsIgnoreCase(candidate)) return child;
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static List<Waypoint> readJourneyMap(Path gameDir, Minecraft mc, Set<String> worldNames) {
        List<Waypoint> out = new ArrayList<>();
        String side = mc.getCurrentServer() != null ? "mp" : "sp";
        Path worldDir = findChild(gameDir.resolve("journeymap").resolve("data").resolve(side), worldNames, "");
        if (worldDir == null) return out;

        Path waypointDir = worldDir.resolve("waypoints");
        if (!Files.isDirectory(waypointDir)) return out;

        try (Stream<Path> files = Files.list(waypointDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) continue;
                try {
                    out.addAll(parseJourneyMap(JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
        return out;
    }

    private static List<Waypoint> parseJourneyMap(JsonObject json) {
        List<Waypoint> out = new ArrayList<>();
        String name = json.get("name").getAsString();
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        int z = json.get("z").getAsInt();
        int r = json.has("r") ? json.get("r").getAsInt() : 255;
        int g = json.has("g") ? json.get("g").getAsInt() : 255;
        int b = json.has("b") ? json.get("b").getAsInt() : 255;
        boolean enabled = !json.has("enable") || json.get("enable").getAsBoolean();
        boolean death = json.has("type") && "Death".equalsIgnoreCase(json.get("type").getAsString());
        String color = String.format("%02X%02X%02X", r & 0xFF, g & 0xFF, b & 0xFF);

        JsonArray dims = json.has("dimensions") && json.get("dimensions").isJsonArray() ?
                json.getAsJsonArray("dimensions") : new JsonArray();
        if (dims.isEmpty()) dims.add("minecraft:overworld");

        for (JsonElement dim : dims) {
            ResourceLocation dimension = parseJmDimension(dim);
            if (dimension == null) continue;
            Waypoint w = new Waypoint(name, dimension, x, y, z, color);
            w.visible = enabled;
            w.category = death ? JM_CATEGORY + " " + DEATH_CATEGORY : JM_CATEGORY;
            out.add(w);
        }
        return out;
    }

    private static ResourceLocation parseJmDimension(JsonElement dim) {
        try {
            if (dim.getAsJsonPrimitive().isNumber()) {
                return legacyDimension(dim.getAsInt());
            }
            return new ResourceLocation(dim.getAsString());
        } catch (Exception e) {
            return null;
        }
    }

    private static ResourceLocation legacyDimension(int id) {
        return switch (id) {
            case -1 -> new ResourceLocation("minecraft:the_nether");
            case 1 -> new ResourceLocation("minecraft:the_end");
            default -> new ResourceLocation("minecraft:overworld");
        };
    }

    private static List<Waypoint> readXaero(Path gameDir, Minecraft mc, Set<String> worldNames) {
        List<Waypoint> out = new ArrayList<>();
        String prefix = mc.getCurrentServer() != null ? "Multiplayer_" : "Singleplayer_";
        Path minimapRoot = gameDir.resolve("xaero").resolve("minimap");
        // Older versions name world folders "Singleplayer_<name>"; 1.21.1 uses the bare name.
        Path worldDir = findChild(minimapRoot, worldNames, prefix);
        if (worldDir == null) worldDir = findChild(minimapRoot, worldNames, "");
        if (worldDir == null) return out;

        try (Stream<Path> dimDirs = Files.list(worldDir)) {
            for (Path dimDir : (Iterable<Path>) dimDirs::iterator) {
                String dimName = dimDir.getFileName().toString();
                if (!Files.isDirectory(dimDir) || !dimName.startsWith("dim%")) continue;
                ResourceLocation dimension = parseXaeroDimension(dimName.substring(4));
                if (dimension == null) continue;
                readXaeroDimension(dimDir, dimension, out);
            }
        } catch (IOException ignored) {}
        return out;
    }

    private static void readXaeroDimension(Path dimDir, ResourceLocation dimension, List<Waypoint> out) {
        try (Stream<Path> files = Files.list(dimDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String fileName = file.getFileName().toString();
                boolean legacyName = fileName.startsWith("mw$") && fileName.endsWith(".txt");
                if (!legacyName && !fileName.equals("waypoints.txt")) continue;
                for (String line : Files.readAllLines(file)) {
                    Waypoint w = parseXaeroLine(line, dimension);
                    if (w != null) out.add(w);
                }
            }
        } catch (IOException ignored) {}
    }

    private static Waypoint parseXaeroLine(String line, ResourceLocation dimension) {
        if (!line.startsWith("waypoint:")) return null;
        String[] parts = line.split(":", -1);
        if (parts.length < 9) return null;
        try {
            String name = parts[1].replace("§§", ":");
            int x = Integer.parseInt(parts[3]);
            int y = "~".equals(parts[4]) ? 64 : Integer.parseInt(parts[4]);
            int z = Integer.parseInt(parts[5]);
            int colorIndex = Math.floorMod(Integer.parseInt(parts[6]), XAERO_COLORS.length);
            boolean disabled = Boolean.parseBoolean(parts[7]);
            int type = Integer.parseInt(parts[8]);

            Waypoint w = new Waypoint(name, dimension, x, y, z, String.format("%06X", XAERO_COLORS[colorIndex]));
            w.visible = !disabled;
            w.category = type == 0 ? XAERO_CATEGORY : XAERO_CATEGORY + " " + DEATH_CATEGORY;
            return w;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ResourceLocation parseXaeroDimension(String folderSuffix) {
        try {
            return switch (folderSuffix) {
                case "0" -> new ResourceLocation("minecraft:overworld");
                case "-1" -> new ResourceLocation("minecraft:the_nether");
                case "1" -> new ResourceLocation("minecraft:the_end");
                default -> new ResourceLocation(folderSuffix.replace('$', ':'));
            };
        } catch (Exception e) {
            return null;
        }
    }
}
