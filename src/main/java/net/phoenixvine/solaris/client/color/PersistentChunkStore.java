package net.phoenixvine.solaris.client.color;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.common.SharedExplorationClient;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PersistentChunkStore {

    private static final int MAGIC = 0x534F4C41;

    private static final int VERSION = 4;

    private PersistentChunkStore() {}

    public static final class Entry {

        public final int[] pixels;
        public final int[] heights;
        public final boolean[] water;
        public final int[] waterTint;
        public final int[] waterDepth;
        public final boolean[] waterOcean;
        public final boolean[] foliage;
        volatile long lastAccessMillis;

        public Entry(int[] pixels, int[] heights, boolean[] water, int[] waterTint, int[] waterDepth,
                     boolean[] waterOcean, boolean[] foliage, long lastAccessMillis) {
            this.pixels = pixels;
            this.heights = heights;
            this.water = water;
            this.waterTint = waterTint;
            this.waterDepth = waterDepth;
            this.waterOcean = waterOcean;
            this.foliage = foliage;
            this.lastAccessMillis = lastAccessMillis;
        }

        public int[] pixels() {
            return pixels;
        }

        public int[] heights() {
            return heights;
        }

        public boolean[] water() {
            return water;
        }

        public int[] waterTint() {
            return waterTint;
        }

        public int[] waterDepth() {
            return waterDepth;
        }

        public boolean[] waterOcean() {
            return waterOcean;
        }

        public boolean[] foliage() {
            return foliage;
        }

        public void touch() {
            lastAccessMillis = System.currentTimeMillis();
        }
    }

    /** Chunks per region side is 1 << REGION_SHIFT (32x32 chunks per region file, same as vanilla). */
    private static final int REGION_SHIFT = 5;

    /** Don't submit routine background saves more often than this. Logout flushes ignore it. */
    private static final long MIN_SAVE_INTERVAL_MS = 30_000L;

    private static final Map<String, ConcurrentHashMap<ChunkKey, Entry>> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    /** dimension -> region keys that changed since they were last written. Guarded by itself. */
    private static final Map<String, Set<Long>> DIRTY_REGIONS = new HashMap<>();
    private static final Set<String> SAVING = ConcurrentHashMap.newKeySet();
    /** Dimensions that were loaded from the old single-file format and still need migrating. */
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

    public static Entry get(ChunkKey key) {
        ConcurrentHashMap<ChunkKey, Entry> chunks = chunksFor(key.dimension().toString());
        if (chunks == null) return null;
        Entry entry = chunks.get(key);
        if (entry != null) entry.lastAccessMillis = System.currentTimeMillis();
        return entry;
    }

    public static ConcurrentHashMap<ChunkKey, Entry> chunksFor(String dimension) {
        ensureLoading(dimension);
        return DIMENSIONS.get(dimension);
    }

    public static Map<ChunkKey, Entry> snapshot(String dimension) {
        ensureLoading(dimension);
        ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.get(dimension);
        return chunks == null ? Map.of() : Map.copyOf(chunks);
    }

    public static boolean isLoaded(String dimension) {
        ensureLoading(dimension);
        return DIMENSIONS.containsKey(dimension);
    }

    /** Returns true when the stored data was new or different, false when it was already identical. */
    public static boolean put(ChunkKey key, int[] pixels, int[] heights, boolean[] water, int[] waterTint,
                              int[] waterDepth, boolean[] waterOcean, boolean[] foliage) {
        String dimension = key.dimension().toString();
        ensureLoading(dimension);

        ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.computeIfAbsent(dimension,
                d -> new ConcurrentHashMap<>());

        // The live-refresh sweep resamples every nearby chunk on a timer, and most of the time nothing
        // changed. Don't mark anything dirty (and so don't rewrite anything) unless the data differs.
        Entry existing = chunks.get(key);
        if (existing != null && sameContent(existing, pixels, heights, water, waterTint, waterDepth,
                waterOcean, foliage)) {
            existing.touch();
            return false;
        }

        Entry previous = chunks.put(key, new Entry(pixels, heights, water, waterTint, waterDepth, waterOcean,
                foliage, System.currentTimeMillis()));
        markRegionDirty(dimension, regionKey(key.x(), key.z()));

        if (previous == null) SharedExplorationClient.onLocalChunk(key);
        return true;
    }

    private static boolean sameContent(Entry e, int[] pixels, int[] heights, boolean[] water, int[] waterTint,
                                       int[] waterDepth, boolean[] waterOcean, boolean[] foliage) {
        return Arrays.equals(e.pixels, pixels) && Arrays.equals(e.heights, heights)
                && Arrays.equals(e.water, water) && Arrays.equals(e.waterTint, waterTint)
                && Arrays.equals(e.waterDepth, waterDepth) && Arrays.equals(e.waterOcean, waterOcean)
                && Arrays.equals(e.foliage, foliage);
    }

    public static boolean putSharedIfAbsent(ChunkKey key, Entry entry) {
        String dimension = key.dimension().toString();
        ensureLoading(dimension);

        ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.computeIfAbsent(dimension,
                d -> new ConcurrentHashMap<>());
        if (chunks.putIfAbsent(key, entry) != null) return false;
        markRegionDirty(dimension, regionKey(key.x(), key.z()));
        return true;
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
            ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.get(dimension);
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
            ConcurrentHashMap<ChunkKey, Entry> loaded = readDimension(worldKey, dimension);

            DIMENSIONS.merge(dimension, loaded, (existing, justLoaded) -> {
                for (Map.Entry<ChunkKey, Entry> e : justLoaded.entrySet()) {
                    existing.putIfAbsent(e.getKey(), e.getValue());
                }
                return existing;
            });

            // Data that came from the old single-file format has no region files yet: mark every region
            // it touches dirty so the first save writes them all, then the old file gets archived.
            if (Files.exists(legacyFileFor(worldKey, dimension))) {
                LEGACY_PENDING.add(dimension);
                for (ChunkKey key : loaded.keySet()) markRegionDirty(dimension, regionKey(key.x(), key.z()));
            }
            LOADING.remove(dimension);
            if (!loaded.isEmpty()) SolarisTexture.invalidateAll();
        });
    }

    private static ConcurrentHashMap<ChunkKey, Entry> readDimension(String worldKey, String dimension) {
        ConcurrentHashMap<ChunkKey, Entry> loaded = new ConcurrentHashMap<>();

        Path dir = regionDirFor(worldKey, dimension);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> {
                    String n = f.getFileName().toString();
                    return n.startsWith("r.") && n.endsWith(".dat");
                }).forEach(f -> readFileInto(f, dimension, loaded, true));
            } catch (IOException e) {
                PhoenixSolaris.LOGGER.warn("Failed to list Solaris map regions for {}", dimension, e);
            }
        }

        // Old single-file format: anything not already in a region file.
        Path legacy = legacyFileFor(worldKey, dimension);
        if (Files.exists(legacy)) {
            ConcurrentHashMap<ChunkKey, Entry> old = new ConcurrentHashMap<>();
            readFileInto(legacy, dimension, old, true);
            for (Map.Entry<ChunkKey, Entry> e : old.entrySet()) loaded.putIfAbsent(e.getKey(), e.getValue());
        }

        int cap = SolarisConfig.MAX_PERSISTED_CHUNKS_PER_DIMENSION.get();
        if (loaded.size() > cap) {
            List<Map.Entry<ChunkKey, Entry>> entries = new ArrayList<>(loaded.entrySet());
            entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccessMillis));
            for (int i = 0; i < entries.size() - cap; i++) loaded.remove(entries.get(i).getKey());
        }

        PhoenixSolaris.LOGGER.info("[Solaris] Loaded {} persisted chunk(s) for {} from disk", loaded.size(),
                dimension);
        return loaded;
    }

    private static void readFileInto(Path file, String dimension, Map<ChunkKey, Entry> into, boolean log) {
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || version != VERSION) {
                PhoenixSolaris.LOGGER.warn(
                        "[Solaris] Discarding persisted map data {} " +
                                "Found format version {} on disk, " +
                                "expected {} (magic {})",
                        file.getFileName(), version, VERSION, magic == MAGIC ? "ok" : "MISMATCH");
                return;
            }

            int count = in.readInt();
            ResourceLocation dimLoc = new ResourceLocation(dimension);
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int z = in.readInt();
                long lastAccess = in.readLong();

                int[] pixels = new int[256];
                for (int p = 0; p < 256; p++) pixels[p] = in.readInt();
                int[] heights = new int[256];
                for (int p = 0; p < 256; p++) heights[p] = in.readInt();
                boolean[] water = readBits(in);
                int[] waterTint = new int[256];
                for (int p = 0; p < 256; p++) waterTint[p] = in.readInt();
                int[] waterDepth = new int[256];
                for (int p = 0; p < 256; p++) waterDepth[p] = in.readInt();
                boolean[] waterOcean = readBits(in);
                boolean[] foliage = readBits(in);
                into.put(new ChunkKey(dimLoc, x, z),
                        new Entry(pixels, heights, water, waterTint, waterDepth, waterOcean, foliage, lastAccess));
            }
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("Failed to load Solaris persisted map data {}", file, e);
        }
    }

    private static boolean[] readBits(DataInputStream in) throws IOException {
        boolean[] bits = new boolean[256];
        for (int p = 0; p < 256; p += 8) {
            int packed = in.readUnsignedByte();
            for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                bits[p + bit] = (packed & (1 << bit)) != 0;
            }
        }
        return bits;
    }

    private static void writeBits(DataOutputStream out, boolean[] bits) throws IOException {
        for (int p = 0; p < 256; p += 8) {
            int packed = 0;
            for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                if (bits[p + bit]) packed |= 1 << bit;
            }
            out.writeByte(packed);
        }
    }

    /** Rewrites only the given regions of a dimension. Everything else on disk is left alone. */
    private static void writeRegions(String worldKey, String dimension, Map<ChunkKey, Entry> chunks,
                                     Set<Long> regions) {
        Map<Long, List<Map.Entry<ChunkKey, Entry>>> byRegion = new HashMap<>();
        for (long r : regions) byRegion.put(r, new ArrayList<>());
        for (Map.Entry<ChunkKey, Entry> e : chunks.entrySet()) {
            List<Map.Entry<ChunkKey, Entry>> list = byRegion.get(regionKey(e.getKey().x(), e.getKey().z()));
            if (list != null) list.add(e);
        }

        int filesWritten = 0;
        int chunksWritten = 0;
        boolean allOk = true;
        for (Map.Entry<Long, List<Map.Entry<ChunkKey, Entry>>> region : byRegion.entrySet()) {
            Path file = regionFile(worldKey, dimension, regionX(region.getKey()), regionZ(region.getKey()));
            try {
                if (region.getValue().isEmpty()) {
                    Files.deleteIfExists(file);
                } else {
                    writeRegionFile(file, region.getValue());
                    filesWritten++;
                    chunksWritten += region.getValue().size();
                }
            } catch (IOException e) {
                PhoenixSolaris.LOGGER.warn("Failed to save Solaris map region {} for {}", file.getFileName(),
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
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                LEGACY_PENDING.remove(dimension);
                PhoenixSolaris.LOGGER.info("[Solaris] Migrated {} to per-region map files", dimension);
            } catch (IOException e) {
                PhoenixSolaris.LOGGER.warn("Failed to archive old Solaris map file for {}", dimension, e);
            }
        }

        PhoenixSolaris.LOGGER.info("[Solaris] Saved {} chunk(s) in {} region file(s) for {} ({} in memory)",
                chunksWritten, filesWritten, dimension, chunks.size());
    }

    private static void writeRegionFile(Path file, List<Map.Entry<ChunkKey, Entry>> entries) throws IOException {
        Path tempFile = file.resolveSibling(file.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
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
                for (Map.Entry<ChunkKey, Entry> e : entries) {
                    ChunkKey key = e.getKey();
                    Entry entry = e.getValue();
                    out.writeInt(key.x());
                    out.writeInt(key.z());
                    out.writeLong(entry.lastAccessMillis);
                    for (int p : entry.pixels) out.writeInt(p);
                    for (int h : entry.heights) out.writeInt(h);
                    writeBits(out, entry.water);
                    for (int t : entry.waterTint) out.writeInt(t);
                    for (int d : entry.waterDepth) out.writeInt(d);
                    writeBits(out, entry.waterOcean);
                    writeBits(out, entry.foliage);
                }
            }
            net.phoenixvine.solaris.util.FileReplace.replace(tempFile, file);
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
        return Paths.get("config", "solaris", "mapdata", safe(worldKey), safe(dimension) + ".dat");
    }

    private static Path regionDirFor(String worldKey, String dimension) {
        return Paths.get("config", "solaris", "mapdata", safe(worldKey), safe(dimension));
    }

    private static Path regionFile(String worldKey, String dimension, int rx, int rz) {
        return regionDirFor(worldKey, dimension).resolve("r." + rx + "." + rz + ".dat");
    }
}
