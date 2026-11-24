package net.phoenixvine.solaris.client;

import net.phoenixvine.solaris.PhoenixSolaris;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SolarisStorage {

    public static final String MAPDATA = "mapdata";
    public static final String WAYPOINTS = "waypoints.json";
    public static final String SHAPES = "shapes.json";
    public static final String GT_VEINS = "gt_veins.json";
    public static final String PENDING_IMPORT = "pending_import.zip";
    public static final String PENDING_MODE = "pending_import.txt";

    private static final Map<String, Path> ROOTS = new ConcurrentHashMap<>();

    private SolarisStorage() {}

    public static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static void register(String worldKey, @Nullable Path worldFolder) {
        Path root = worldFolder != null ? worldFolder.resolve("solaris") :
                Paths.get("solaris", "servers", safe(worldKey));
        ROOTS.put(worldKey, root);
        prepare(worldKey, root);
    }

    public static Path rootFor(String worldKey) {
        Path root = ROOTS.get(worldKey);
        return root != null ? root : Paths.get("solaris", "worlds", safe(worldKey));
    }

    public static Path mapdata(String worldKey) {
        return rootFor(worldKey).resolve(MAPDATA);
    }

    public static Path waypointsFile(String worldKey) {
        return rootFor(worldKey).resolve(WAYPOINTS);
    }

    public static Path shapesFile(String worldKey) {
        return rootFor(worldKey).resolve(SHAPES);
    }

    public static Path gtVeinsFile(String worldKey) {
        return rootFor(worldKey).resolve(GT_VEINS);
    }

    public static Path exportsDir() {
        return Paths.get("solaris", "exports");
    }

    private static void prepare(String worldKey, Path root) {
        String safe = safe(worldKey);
        Path config = Paths.get("config", "solaris");
        moveIfAbsent(config.resolve("mapdata").resolve(safe), root.resolve(MAPDATA));
        moveIfAbsent(config.resolve("waypoints").resolve(safe + ".json"), root.resolve(WAYPOINTS));
        moveIfAbsent(config.resolve("shapes").resolve(safe + ".json"), root.resolve(SHAPES));
        moveIfAbsent(config.resolve("gt_veins").resolve(safe + ".json"), root.resolve(GT_VEINS));
        applyPendingImport(root);
    }

    private static void moveIfAbsent(Path from, Path to) {
        try {
            if (!Files.exists(from) || Files.exists(to)) return;
            Files.createDirectories(to.getParent());
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                copyTree(from, to);
                deleteTree(from);
            }
            PhoenixSolaris.LOGGER.info("[Solaris] Moved map data from {} to {}", from, to);
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("[Solaris] Could not move map data from {} to {}", from, to, e);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (Files.isDirectory(from)) {
            try (Stream<Path> walk = Files.walk(from)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    Path target = to.resolve(from.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } else {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static Path export(String worldKey, String name) throws IOException {
        Path root = rootFor(worldKey);
        Path dir = exportsDir();
        Files.createDirectories(dir);
        String file = safe(name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name) + ".zip";
        Path zip = dir.resolve(file);
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
            for (String item : new String[] { MAPDATA, WAYPOINTS, SHAPES, GT_VEINS }) {
                Path p = root.resolve(item);
                if (!Files.exists(p)) continue;
                if (Files.isDirectory(p)) {
                    try (Stream<Path> walk = Files.walk(p)) {
                        for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                            addEntry(z, root, f);
                        }
                    }
                } else {
                    addEntry(z, root, p);
                }
            }
        }
        return zip;
    }

    private static void addEntry(ZipOutputStream z, Path root, Path file) throws IOException {
        z.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\', '/')));
        Files.copy(file, z);
        z.closeEntry();
    }

    public static void queueImport(String worldKey, Path zip, boolean replace) throws IOException {
        Path root = rootFor(worldKey);
        Files.createDirectories(root);
        Files.copy(zip, root.resolve(PENDING_IMPORT), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(root.resolve(PENDING_MODE), replace ? "replace" : "merge");
    }

    private static boolean mergeWaypoints(byte[] imported, Path target) {
        try {
            com.google.gson.JsonObject have = com.google.gson.JsonParser.parseString(Files.readString(target))
                    .getAsJsonObject();
            com.google.gson.JsonObject incoming = com.google.gson.JsonParser
                    .parseString(new String(imported, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            com.google.gson.JsonArray mine = have.has("waypoints") ? have.getAsJsonArray("waypoints") :
                    new com.google.gson.JsonArray();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (var e : mine) {
                var o = e.getAsJsonObject();
                if (o.has("id")) ids.add(o.get("id").getAsString());
            }
            boolean changed = false;
            if (incoming.has("waypoints")) {
                for (var e : incoming.getAsJsonArray("waypoints")) {
                    var o = e.getAsJsonObject();
                    if (o.has("id") && ids.add(o.get("id").getAsString())) {
                        mine.add(o);
                        changed = true;
                    }
                }
            }
            if (changed) {
                have.add("waypoints", mine);
                Files.writeString(target, new com.google.gson.Gson().toJson(have));
            }
            return changed;
        } catch (Exception e) {
            PhoenixSolaris.LOGGER.warn("[Solaris] Could not merge the imported waypoints into {}", target, e);
            return false;
        }
    }

    private static void applyPendingImport(Path root) {
        Path zip = root.resolve(PENDING_IMPORT);
        if (!Files.exists(zip)) return;
        boolean replace = false;
        try {
            Path mode = root.resolve(PENDING_MODE);
            if (Files.exists(mode)) replace = "replace".equals(Files.readString(mode).trim());
        } catch (IOException ignored) {}

        int written = 0, skipped = 0;
        try (InputStream in = Files.newInputStream(zip); ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry entry;
            Path normalRoot = root.toAbsolutePath().normalize();
            while ((entry = z.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                boolean known = name.startsWith(MAPDATA + "/") || name.equals(WAYPOINTS) || name.equals(SHAPES) ||
                        name.equals(GT_VEINS);
                Path target = root.resolve(name).toAbsolutePath().normalize();
                if (!known || !target.startsWith(normalRoot)) {
                    skipped++;
                    continue;
                }
                if (Files.exists(target) && !replace) {
                    if (name.equals(WAYPOINTS) && mergeWaypoints(z.readAllBytes(), target)) written++;
                    else skipped++;
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(z, target, StandardCopyOption.REPLACE_EXISTING);
                written++;
            }
            PhoenixSolaris.LOGGER.info("[Solaris] Imported map data into {}: {} file(s) written, {} left as they were",
                    root, written, skipped);
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("[Solaris] Could not import map data into {}", root, e);
            return;
        }
        try {
            Files.deleteIfExists(zip);
            Files.deleteIfExists(root.resolve(PENDING_MODE));
        } catch (IOException ignored) {}
    }
}
