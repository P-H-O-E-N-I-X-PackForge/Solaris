package net.phoenixvine.solaris.client.color;

import net.minecraft.Util;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PersistentCaveStore {

    private static final int MAGIC = 0x534F4C42;
    private static final int VERSION = 1;

    public static final int Y_BUCKET_SIZE = 4;

    private PersistentCaveStore() {}

    public record CaveKey(String dimension, int x, int z, int yBucket) {}

    private static final class Entry {

        final int[] pixels;
        volatile long lastAccessMillis;

        Entry(int[] pixels, long lastAccessMillis) {
            this.pixels = pixels;
            this.lastAccessMillis = lastAccessMillis;
        }
    }

    private static final int REGION_SHIFT = 5;
    private static final long MIN_SAVE_INTERVAL_MS = 30_000L;

    private static final Map<String, ConcurrentHashMap<CaveKey, Entry>> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Set<Long>> DIRTY_REGIONS = new HashMap<>();
    private static final Set<String> SAVING = ConcurrentHashMap.newKeySet();
    private static final Set<String> LEGACY_PENDING = ConcurrentHashMap.newKeySet();
    private static volatile long lastSubmitMillis = 0L;
    private static String loadedWorldKey = null;

    private static long regionKey(int chunkX, int chunkZ) {
        return (((long) (chunkX >> REGION_SHIFT)) << 32) | ((chunkZ >> REGION_SHIFT) & 0xFFFFFFFFL);
    }

    private static int regionX(long regionKey) {
        return (int) (regionKey >> 32);
    }

    private static int regionZ(long regionKey) {
        return (int) regionKey;
    }

    private static void markRegionDirty(String dimension, long regionKey) {
        synchronized (DIRTY_REGIONS) {
            DIRTY_REGIONS.computeIfAbsent(dimension, d -> new HashSet<>()).add(regionKey);
        }
    }

    private static boolean hasDirty() {
        synchronized (DIRTY_REGIONS) {
            return !DIRTY_REGIONS.isEmpty();
        }
    }

    public static int yBucket(int y) {
        return Math.floorDiv(y, Y_BUCKET_SIZE);
    }

    public static int[] get(String dimension, int x, int z, int yBucket) {
        ensureLoading(dimension);
        ConcurrentHashMap<CaveKey, Entry> chunks = DIMENSIONS.get(dimension);
        if (chunks == null) return null;
        Entry entry = chunks.get(new CaveKey(dimension, x, z, yBucket));
        if (entry == null) return null;
        entry.lastAccessMillis = System.currentTimeMillis();
        return entry.pixels;
    }

    public static void put(String dimension, int x, int z, int yBucket, int[] pixels) {
        ensureLoading(dimension);
        ConcurrentHashMap<CaveKey, Entry> chunks = DIMENSIONS.computeIfAbsent(dimension,
                d -> new ConcurrentHashMap<>());
        CaveKey key = new CaveKey(dimension, x, z, yBucket);

        Entry existing = chunks.get(key);
        if (existing != null && Arrays.equals(existing.pixels, pixels)) {
            existing.lastAccessMillis = System.currentTimeMillis();
            return;
        }

        chunks.put(key, new Entry(pixels, System.currentTimeMillis()));
        markRegionDirty(dimension, regionKey(x, z));
    }

    public static void saveDirtyAsync() {
        String worldKey = WaypointManager.currentWorldKey();
        if (worldKey == null || !hasDirty()) return;

        if (!worldKey.equals(loadedWorldKey)) return;

        if (System.currentTimeMillis() - lastSubmitMillis < MIN_SAVE_INTERVAL_MS) return;

        if (submitDirtyWrites(worldKey)) lastSubmitMillis = System.currentTimeMillis();
    }

    public static void flushOnLogout() {
        String worldKey = loadedWorldKey;
        if (worldKey == null || !hasDirty()) return;
        submitDirtyWrites(worldKey);
    }

    private static boolean submitDirtyWrites(String worldKey) {
        List<String> dimensions;
        synchronized (DIRTY_REGIONS) {
            dimensions = new ArrayList<>(DIRTY_REGIONS.keySet());
        }
        boolean submitted = false;
        for (String dimension : dimensions) {

            if (LOADING.contains(dimension)) continue;
            if (!SAVING.add(dimension)) continue;

            Set<Long> regions;
            synchronized (DIRTY_REGIONS) {
                Set<Long> dirty = DIRTY_REGIONS.remove(dimension);
                regions = dirty == null ? Set.of() : new HashSet<>(dirty);
            }
            ConcurrentHashMap<CaveKey, Entry> chunks = DIMENSIONS.get(dimension);
            if (chunks == null || regions.isEmpty()) {
                SAVING.remove(dimension);
                continue;
            }
            submitted = true;
            Util.ioPool().execute(() -> {
                try {
                    writeRegions(worldKey, dimension, chunks, regions);
                } finally {
                    SAVING.remove(dimension);
                }
            });
        }
        return submitted;
    }

    private static void ensureLoading(String dimension) {
        String worldKey = WaypointManager.currentWorldKey();
        if (worldKey == null) return;

        if (!worldKey.equals(loadedWorldKey)) {
            DIMENSIONS.clear();
            LOADING.clear();
            synchronized (DIRTY_REGIONS) {
                DIRTY_REGIONS.clear();
            }
            SAVING.clear();
            LEGACY_PENDING.clear();
            lastSubmitMillis = 0L;
            loadedWorldKey = worldKey;
        }

        if (DIMENSIONS.containsKey(dimension) || !LOADING.add(dimension)) return;

        Util.ioPool().execute(() -> {
            ConcurrentHashMap<CaveKey, Entry> loaded = readDimension(worldKey, dimension);

            DIMENSIONS.merge(dimension, loaded, (existing, justLoaded) -> {
                for (Map.Entry<CaveKey, Entry> e : justLoaded.entrySet()) {
                    existing.putIfAbsent(e.getKey(), e.getValue());
                }
                return existing;
            });

            if (Files.exists(legacyFileFor(worldKey, dimension))) {
                LEGACY_PENDING.add(dimension);
                for (CaveKey key : loaded.keySet()) markRegionDirty(dimension, regionKey(key.x(), key.z()));
            }
            LOADING.remove(dimension);
        });
    }

    private static ConcurrentHashMap<CaveKey, Entry> readDimension(String worldKey, String dimension) {
        ConcurrentHashMap<CaveKey, Entry> loaded = new ConcurrentHashMap<>();

        Path dir = regionDirFor(worldKey, dimension);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> {
                    String n = f.getFileName().toString();
                    return n.startsWith("r.") && n.endsWith(".dat");
                }).forEach(f -> readFileInto(f, dimension, loaded));
            } catch (IOException e) {
                PhoenixSolaris.LOGGER.warn("Failed to list Solaris cave regions for {}", dimension, e);
            }
        }

        Path legacy = legacyFileFor(worldKey, dimension);
        if (Files.exists(legacy)) {
            ConcurrentHashMap<CaveKey, Entry> old = new ConcurrentHashMap<>();
            readFileInto(legacy, dimension, old);
            for (Map.Entry<CaveKey, Entry> e : old.entrySet()) loaded.putIfAbsent(e.getKey(), e.getValue());
        }

        int cap = SolarisConfig.MAX_PERSISTED_CHUNKS_PER_DIMENSION.get() * 4;
        if (loaded.size() > cap) {
            List<Map.Entry<CaveKey, Entry>> entries = new ArrayList<>(loaded.entrySet());
            entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccessMillis));
            for (int i = 0; i < entries.size() - cap; i++) loaded.remove(entries.get(i).getKey());
        }

        PhoenixSolaris.LOGGER.info("[Solaris] Loaded {} persisted cave slice(s) for {} from disk", loaded.size(),
                dimension);
        return loaded;
    }

    private static void readFileInto(Path file, String dimension, Map<CaveKey, Entry> into) {
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || version != VERSION) {
                PhoenixSolaris.LOGGER.warn(
                        "[Solaris] Discarding persisted cave data {}. Found format version {} on disk, " +
                                "expected {} (magic {})",
                        file.getFileName(), version, VERSION, magic == MAGIC ? "ok" : "MISMATCH");
                return;
            }

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int z = in.readInt();
                int yBucket = in.readInt();
                long lastAccess = in.readLong();
                int[] pixels = new int[256];
                for (int p = 0; p < 256; p++) pixels[p] = in.readInt();
                into.put(new CaveKey(dimension, x, z, yBucket), new Entry(pixels, lastAccess));
            }
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("Failed to load Solaris persisted cave data {}", file, e);
        }
    }

    private static void writeRegions(String worldKey, String dimension, Map<CaveKey, Entry> chunks,
                                     Set<Long> regions) {
        Map<Long, List<Map.Entry<CaveKey, Entry>>> byRegion = new HashMap<>();
        for (long r : regions) byRegion.put(r, new ArrayList<>());
        for (Map.Entry<CaveKey, Entry> e : chunks.entrySet()) {
            List<Map.Entry<CaveKey, Entry>> list = byRegion.get(regionKey(e.getKey().x(), e.getKey().z()));
            if (list != null) list.add(e);
        }

        int filesWritten = 0;
        int slicesWritten = 0;
        boolean allOk = true;
        for (Map.Entry<Long, List<Map.Entry<CaveKey, Entry>>> region : byRegion.entrySet()) {
            Path file = regionFile(worldKey, dimension, regionX(region.getKey()), regionZ(region.getKey()));
            try {
                if (region.getValue().isEmpty()) {
                    Files.deleteIfExists(file);
                } else {
                    writeRegionFile(file, region.getValue());
                    filesWritten++;
                    slicesWritten += region.getValue().size();
                }
            } catch (IOException e) {
                PhoenixSolaris.LOGGER.warn("Failed to save Solaris cave region {} for {}", file.getFileName(),
                        dimension, e);
                markRegionDirty(dimension, region.getKey());
                allOk = false;
            }
        }

        if (allOk && LEGACY_PENDING.contains(dimension)) {
            Path legacy = legacyFileFor(worldKey, dimension);
            try {
                if (Files.exists(legacy)) {
                    Files.move(legacy, legacy.resolveSibling(legacy.getFileName() + ".migrated"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                LEGACY_PENDING.remove(dimension);
                PhoenixSolaris.LOGGER.info("[Solaris] Migrated cave data for {} to per-region files", dimension);
            } catch (IOException e) {
                PhoenixSolaris.LOGGER.warn("Failed to archive old Solaris cave file for {}", dimension, e);
            }
        }

        PhoenixSolaris.LOGGER.info("[Solaris] Saved {} cave slice(s) in {} region file(s) for {} ({} in memory)",
                slicesWritten, filesWritten, dimension, chunks.size());
    }

    private static void writeRegionFile(Path file, List<Map.Entry<CaveKey, Entry>> entries) throws IOException {
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempFile))) {

                {
                    def.setLevel(java.util.zip.Deflater.BEST_SPEED);
                }
            })) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(entries.size());
                for (Map.Entry<CaveKey, Entry> e : entries) {
                    CaveKey key = e.getKey();
                    Entry entry = e.getValue();
                    out.writeInt(key.x());
                    out.writeInt(key.z());
                    out.writeInt(key.yBucket());
                    out.writeLong(entry.lastAccessMillis);
                    for (int p : entry.pixels) out.writeInt(p);
                }
            }
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
            throw e;
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path legacyFileFor(String worldKey, String dimension) {
        return Paths.get("config", "solaris", "mapdata", safe(worldKey), safe(dimension) + "_caves.dat");
    }

    private static Path regionDirFor(String worldKey, String dimension) {
        return Paths.get("config", "solaris", "mapdata", safe(worldKey), safe(dimension) + "_caves");
    }

    private static Path regionFile(String worldKey, String dimension, int rx, int rz) {
        return regionDirFor(worldKey, dimension).resolve("r." + rx + "." + rz + ".dat");
    }
}
