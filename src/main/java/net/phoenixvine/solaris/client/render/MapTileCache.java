package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.ChunkColorCache;
import net.phoenixvine.solaris.client.color.ChunkColorSampler;
import net.phoenixvine.solaris.client.color.ChunkFoliageCache;
import net.phoenixvine.solaris.client.color.ChunkHeightCache;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.ChunkLightCache;
import net.phoenixvine.solaris.client.color.ChunkWaterCache;
import net.phoenixvine.solaris.client.color.ChunkWaterDepthCache;
import net.phoenixvine.solaris.client.color.ChunkWaterOceanCache;
import net.phoenixvine.solaris.client.color.ChunkWaterTintCache;
import net.phoenixvine.solaris.client.color.PersistentChunkStore;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.client.perf.FullMapLog;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MapTileCache {

    public static final int TILE_CHUNKS = 8;
    public static final int TILE_PIXELS = TILE_CHUNKS * 16;

    private static volatile int capacity = 512;
    private static final int HARD_CEILING = 4096;

    public static final int MAX_LOD = 14;

    public record TileKey(ResourceLocation dimension, int tileX, int tileZ, int lod) {

        public TileKey(ResourceLocation dimension, int tileX, int tileZ) {
            this(dimension, tileX, tileZ, 0);
        }

        public static void markChunkDirty(Set<TileKey> dirty, ResourceLocation dimension, int chunkX, int chunkZ) {
            for (int lod = 0; lod <= MAX_LOD; lod++) {
                int chunksPerTile = TILE_CHUNKS << lod;
                int tx = Math.floorDiv(chunkX, chunksPerTile);
                int tz = Math.floorDiv(chunkZ, chunksPerTile);
                dirty.add(new TileKey(dimension, tx, tz, lod));

                int lx = Math.floorMod(chunkX, chunksPerTile);
                int lz = Math.floorMod(chunkZ, chunksPerTile);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        boolean touchesX = dx == 0 || (dx < 0 && lx == 0) || (dx > 0 && lx == chunksPerTile - 1);
                        boolean touchesZ = dz == 0 || (dz < 0 && lz == 0) || (dz > 0 && lz == chunksPerTile - 1);
                        if (touchesX && touchesZ) dirty.add(new TileKey(dimension, tx + dx, tz + dz, lod));
                    }
                }
            }
        }
    }

    public static int blocksPerPixel(int lod) {
        return 1 << lod;
    }

    public static int tileWorldSize(int lod) {
        return TILE_PIXELS << lod;
    }

    private static final double LOD_ZOOM_BIAS = 1.32;

    public static int lodForZoom(float zoom) {
        int lod = (int) Math.ceil(-Math.log(zoom) / Math.log(2) - LOD_ZOOM_BIAS);
        if (zoom > 300) lod = Math.max(lod, 2);
        if (zoom > 500) lod = Math.max(lod, 3);
        if (zoom > 1000) lod = Math.max(lod, 4);
        return Math.max(0, Math.min(MAX_LOD, lod));
    }

    public static TileKey getTileKeyForZoom(ResourceLocation dimension, int tileX, int tileZ, float zoom) {
        int lod = lodForZoom(zoom);
        int scale = 1 << lod;
        return new TileKey(dimension, tileX / scale, tileZ / scale, lod);
    }

    public static final class MapTile implements AutoCloseable {

        private final ResourceLocation textureId;
        private final NativeImage image;
        private final DynamicTexture texture;

        private MapTile(ResourceLocation textureId) {
            this.textureId = textureId;
            this.image = new NativeImage(TILE_PIXELS, TILE_PIXELS, false);
            this.texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);

            texture.setFilter(false, false);
        }

        public ResourceLocation textureId() {
            return textureId;
        }

        @Override
        public void close() {
            texture.close();
        }
    }

    private static int nextTileId = 0;

    private static final Map<TileKey, MapTile> TILES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<TileKey, MapTile> eldest) {
                    if (size() <= capacity) return false;
                    if (FullMapLog.on()) {
                        FullMapLog.count("tile.evict");
                        FullMapLog.log("tile", "EVICT {} (size={} capacity={})", eldest.getKey(), size(), capacity);
                    }
                    LAST_BUILD_MS.remove(eldest.getKey());
                    LAST_BLANK_LOG_MS.remove(eldest.getKey());
                    eldest.getValue().close();
                    return true;
                }
            });

    private static final Set<TileKey> DIRTY = ConcurrentHashMap.newKeySet();

    /**
     * While chunks stream in, the same tile gets marked dirty many times a second. Rebuilding it every time is
     * wasted work, so an already-built tile is rebuilt at most this often; it keeps showing its previous image in
     * between and the pending dirty flag is kept, so nothing is lost.
     */
    private static final long MIN_REBUILD_INTERVAL_MS = 500L;
    private static final Map<TileKey, Long> LAST_BUILD_MS = new ConcurrentHashMap<>();

    private static final long BLANK_LOG_INTERVAL_MS = 5000L;
    private static final Map<TileKey, Long> LAST_BLANK_LOG_MS = new ConcurrentHashMap<>();

    private static double lastNightFactorBucket = -1;
    private static final double NIGHT_FACTOR_BUCKET = 0.05;

    private MapTileCache() {}

    public static void ensureCapacity(int neededTiles) {
        if (neededTiles > capacity) {
            FullMapLog.log("tile", "capacity {} -> {} (needed {})", capacity, Math.min(HARD_CEILING, neededTiles),
                    neededTiles);
            capacity = Math.min(HARD_CEILING, neededTiles);
        }
    }

    public static void checkNightFactor(Level level) {
        if (level == null) return;
        double bucket = Math.round(ChunkColorSampler.nightFactor(level.getDayTime()) / NIGHT_FACTOR_BUCKET) *
                NIGHT_FACTOR_BUCKET;
        if (bucket == lastNightFactorBucket) return;
        FullMapLog.log("tile", "night factor bucket {} -> {}: marking ALL {} tiles dirty", lastNightFactorBucket,
                bucket, TILES.size());
        lastNightFactorBucket = bucket;
        synchronized (TILES) {
            DIRTY.addAll(TILES.keySet());
        }
    }

    public static MapTile getOrBuildTile(TileKey key) {
        return getOrBuildTile(key, new int[] { Integer.MAX_VALUE });
    }

    public static MapTile getOrBuildTile(TileKey key, int[] buildBudget) {
        MapTile tile = TILES.get(key);
        boolean dirty = DIRTY.remove(key);
        if (tile != null && !dirty) return tile;

        if (tile != null) {
            Long lastBuild = LAST_BUILD_MS.get(key);
            if (lastBuild != null && System.currentTimeMillis() - lastBuild < MIN_REBUILD_INTERVAL_MS) {
                DIRTY.add(key);
                if (FullMapLog.on()) FullMapLog.count("tile.rebuildThrottled");
                return tile;
            }
        }

        if (!PersistentChunkStore.isLoaded(key.dimension().toString())) {
            if (dirty) DIRTY.add(key);
            if (FullMapLog.on()) {
                FullMapLog.count("tile.deferNoStore");
                FullMapLog.log("tile", "defer {} : persistent store for {} not loaded yet (tile exists={})", key,
                        key.dimension(), tile != null);
            }
            return tile;
        }

        if (buildBudget[0] <= 0) {
            DIRTY.add(key);
            if (FullMapLog.on()) FullMapLog.count("tile.deferBudget");
            return tile;
        }
        buildBudget[0]--;
        LAST_BUILD_MS.put(key, System.currentTimeMillis());
        long buildStartNanos = System.nanoTime();
        boolean wasNew = tile == null;

        if (tile == null) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID,
                    "dynamic/tile_" + nextTileId++ + "_" + key.tileX() + "_" + key.tileZ() + "_" + key.lod());
            tile = new MapTile(id);
            TILES.put(key, tile);
        }
        MapTile builtTile = tile;
        try {
            if (key.lod() == 0) {
                SolarisProfiler.time("mapTile:build:lod0", () -> buildTile(key, builtTile));
            } else {
                SolarisProfiler.time("mapTile:build:lodN", () -> buildTileLod(key, builtTile));
            }
        } catch (Exception e) {
            FullMapLog.count("tile.buildFailed");
            FullMapLog.log("tile", "BUILD FAILED {} : {}", key, e.toString());
            DIRTY.add(key);
            PhoenixSolaris.LOGGER.error("[Solaris] Failed to build map tile {}. " +
                    "Will retry next frame", key, e);
        }
        if (FullMapLog.on()) {
            FullMapLog.count(wasNew ? "tile.buildNew" : "tile.rebuild");
            FullMapLog.log("tile", "built {} ({}) in {}us", key, wasNew ? "new" : "rebuild after dirty",
                    (System.nanoTime() - buildStartNanos) / 1000L);
            checkSeams(key, builtTile);
        }
        return tile;
    }

    public static void markDirty(ChunkKey key) {
        TileKey.markChunkDirty(DIRTY, key.dimension(), key.x(), key.z());
        if (FullMapLog.on()) logEdgeDirty(key);
    }

    private static void logEdgeDirty(ChunkKey key) {
        int tx = Math.floorDiv(key.x(), TILE_CHUNKS);
        int tz = Math.floorDiv(key.z(), TILE_CHUNKS);
        int lx = Math.floorMod(key.x(), TILE_CHUNKS);
        int lz = Math.floorMod(key.z(), TILE_CHUNKS);
        StringBuilder stale = new StringBuilder();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                boolean touchesX = dx == 0 || (dx < 0 && lx == 0) || (dx > 0 && lx == TILE_CHUNKS - 1);
                boolean touchesZ = dz == 0 || (dz < 0 && lz == 0) || (dz > 0 && lz == TILE_CHUNKS - 1);
                if (!touchesX || !touchesZ) continue;
                TileKey neighbour = new TileKey(key.dimension(), tx + dx, tz + dz, 0);
                if (TILES.containsKey(neighbour)) {
                    stale.append(" (").append(neighbour.tileX()).append(',').append(neighbour.tileZ()).append(')');
                }
            }
        }
        FullMapLog.count("tile.markDirty");
        if (stale.length() > 0) {
            FullMapLog.count("tile.edgeNeighbourStale");
            FullMapLog.log("tile", "markDirty chunk ({},{}) in tile ({},{}) is on a tile edge; neighbour tiles also " +
                    "dirtied for halo:{}", key.x(), key.z(), tx, tz, stale);
        } else {
            FullMapLog.log("tile", "markDirty chunk ({},{}) -> tile ({},{}) all LODs", key.x(), key.z(), tx, tz);
        }
    }

    private static int luma(int abgr) {
        return (299 * FastColor.ABGR32.red(abgr) + 587 * FastColor.ABGR32.green(abgr) +
                114 * FastColor.ABGR32.blue(abgr)) / 1000;
    }

    private static void checkSeams(TileKey key, MapTile tile) {
        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        String[] names = { "W", "E", "N", "S" };
        for (int d = 0; d < dirs.length; d++) {
            TileKey nKey = new TileKey(key.dimension(), key.tileX() + dirs[d][0], key.tileZ() + dirs[d][1],
                    key.lod());
            MapTile neighbour;
            synchronized (TILES) {
                neighbour = TILES.get(nKey);
            }
            if (neighbour == null) continue;
            int suspicious = 0;
            long seamSum = 0;
            for (int i = 0; i < TILE_PIXELS; i++) {
                int ax;
                int az;
                int bx;
                int bz;
                int cx;
                int cz;
                if (dirs[d][0] != 0) {
                    ax = dirs[d][0] < 0 ? 0 : TILE_PIXELS - 1;
                    bx = dirs[d][0] < 0 ? TILE_PIXELS - 1 : 0;
                    cx = dirs[d][0] < 0 ? 1 : TILE_PIXELS - 2;
                    az = i;
                    bz = i;
                    cz = i;
                } else {
                    az = dirs[d][1] < 0 ? 0 : TILE_PIXELS - 1;
                    bz = dirs[d][1] < 0 ? TILE_PIXELS - 1 : 0;
                    cz = dirs[d][1] < 0 ? 1 : TILE_PIXELS - 2;
                    ax = i;
                    bx = i;
                    cx = i;
                }
                int a = luma(tile.image.getPixelRGBA(ax, az));
                int b = luma(neighbour.image.getPixelRGBA(bx, bz));
                int c = luma(tile.image.getPixelRGBA(cx, cz));
                int seam = Math.abs(a - b);
                seamSum += seam;
                if (seam >= 20 && Math.abs(a - c) <= 6) suspicious++;
            }
            FullMapLog.count("seam.checked");
            if (suspicious > 0) {
                FullMapLog.count("seam.suspicious");
                FullMapLog.log("seam", "SEAM {} {} edge vs neighbour ({},{}): suspiciousPx={}/{} meanStep={} " +
                        "neighbourDirty={} selfDirty={}", key, names[d], nKey.tileX(), nKey.tileZ(), suspicious,
                        TILE_PIXELS, seamSum / TILE_PIXELS, DIRTY.contains(nKey), DIRTY.contains(key));
            }
        }
    }

    public static String debugState() {
        return "tiles=" + TILES.size() + "/" + capacity + " dirtyPending=" + DIRTY.size();
    }

    public static boolean isDirty(TileKey key) {
        return DIRTY.contains(key);
    }

    public static boolean isBuilt(TileKey key) {
        return TILES.containsKey(key);
    }

    public static void clearAll() {
        FullMapLog.log("tile", "clearAll: dropping {} tiles, {} pending dirty", TILES.size(), DIRTY.size());
        synchronized (TILES) {
            for (MapTile tile : TILES.values()) tile.close();
            TILES.clear();
        }
        DIRTY.clear();
        LAST_BUILD_MS.clear();
        LAST_BLANK_LOG_MS.clear();
    }

    private static final int HALO = TILE_PIXELS + 2;

    private static int haloIdx(int x, int z) {
        return (z + 1) * HALO + (x + 1);
    }

    static int themeToAbgr(int argb) {
        return FastColor.ABGR32.color(FastColor.ARGB32.alpha(argb), FastColor.ARGB32.blue(argb),
                FastColor.ARGB32.green(argb), FastColor.ARGB32.red(argb));
    }

    static int starfieldPixel(int worldX, int worldZ, double density, double brightness,
                              int accentAbgr, int dimAbgr, int spaceAbgr) {
        long h = (long) worldX * 0x9E3779B97F4A7C15L + (long) worldZ * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;

        int bucket = (int) (h & 0x3FF);
        int brightThreshold = (int) Math.round(3 * density);
        int dimThreshold = (int) Math.round(12 * density);
        if (bucket < brightThreshold) {
            double variance = 0.85 + ((h >>> 40) & 0x2D) / 180.0;
            return SolarisTexture.scaleBrightness(accentAbgr, brightness * variance);
        }
        if (bucket < dimThreshold) {
            double variance = 0.45 + ((h >>> 40) & 0x4F) / 160.0;
            return SolarisTexture.scaleBrightness(dimAbgr, brightness * variance);
        }
        return spaceAbgr;
    }

    private static final int EMBER_SPACE_COLOR = FastColor.ABGR32.color(255, 3, 4, 18);

    static int phoenixPixel(int worldX, int worldZ, double density, double brightness) {
        long h = (long) worldX * 0x9E3779B97F4A7C15L + (long) worldZ * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;

        int bucket = (int) (h & 0x3FF);
        int brightThreshold = (int) Math.round(3 * density);
        int dimThreshold = (int) Math.round(14 * density);
        if (bucket < brightThreshold) {
            int r = 255;
            int g = 130 + (int) ((h >>> 40) & 0x3F);
            int b = 20 + (int) ((h >>> 48) & 0x1F);
            return SolarisTexture.scaleBrightness(FastColor.ABGR32.color(255, b, g, r), brightness);
        }
        if (bucket < dimThreshold) {
            int r = 140 + (int) ((h >>> 40) & 0x3F);
            int g = 40 + (int) ((h >>> 48) & 0x2F);
            int b = 8;
            return SolarisTexture.scaleBrightness(FastColor.ABGR32.color(255, b, g, r), brightness * 0.8);
        }
        return EMBER_SPACE_COLOR;
    }

    private static double blobNoise(int worldX, int worldZ, int scale, long seed) {
        int bx = Math.floorDiv(worldX, scale);
        int bz = Math.floorDiv(worldZ, scale);
        long h = (long) bx * 0x9E3779B97F4A7C15L + (long) bz * 0xBF58476D1CE4E5B9L + seed;
        h ^= h >>> 31;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    static int cloudPixel(int worldX, int worldZ, double density, double brightness) {
        double coarse = blobNoise(worldX, worldZ, 20, 0x1L);
        double fine = blobNoise(worldX, worldZ, 6, 0x9E3779B9L);
        double combined = coarse * 0.7 + fine * 0.3;

        double coverage = Mth.clamp(0.35 * density, 0.05, 0.9);
        double t = Mth.clamp((combined - (1.0 - coverage)) / coverage, 0.0, 1.0);
        if (t <= 0.0) return 0;

        int alpha = (int) Math.round(Mth.clamp(60 + t * 150 * brightness, 0, 235));
        int gray = Math.min(240, (int) Math.round(190 + t * 40));
        return FastColor.ABGR32.color(alpha, gray, gray, gray);
    }

    private record UnexploredStyleContext(UnexploredStyle style, double density, double brightness,
                                          boolean imageCover, int fogColor, int starAccent, int starDim,
                                          int starSpace) {

        static UnexploredStyleContext forDimension(ResourceLocation dimension, int fogColor) {
            return new UnexploredStyleContext(SolarisAPI.getUnexploredStyle(dimension),
                    SolarisConfig.UNEXPLORED_DENSITY.get(), SolarisConfig.UNEXPLORED_BRIGHTNESS.get(),
                    SolarisConfig.UNEXPLORED_IMAGE_COVER.get(), fogColor, themeToAbgr(SolarisThemeUtils.C_ACCENT),
                    themeToAbgr(SolarisThemeUtils.C_DIM), SolarisTexture.scaleBrightness(fogColor, 0.3));
        }
    }

    private static int unexploredColor(UnexploredStyleContext ctx, int worldPx, int worldPz) {
        return switch (ctx.style()) {
            case FOG -> ctx.fogColor();
            case STARFIELD -> starfieldPixel(worldPx, worldPz, ctx.density(), ctx.brightness(), ctx.starAccent(),
                    ctx.starDim(), ctx.starSpace());
            case PHOENIX -> phoenixPixel(worldPx, worldPz, ctx.density(), ctx.brightness());
            case CLOUD -> cloudPixel(worldPx, worldPz, ctx.density(), ctx.brightness());
            case BACKDROP -> 0;
            case IMAGE -> {
                if (ctx.imageCover()) yield 0;
                int imgColor = UnexploredImageStyle.getPixel(worldPx, worldPz);
                yield imgColor != 0 ? imgColor : ctx.fogColor();
            }
        };
    }

    private static void finishTile(TileKey key, MapTile tile, NativeImage image, int[] heights, boolean[] water,
                                   int[] waterDepth, boolean[] lightEmitting, boolean[] haloHasData) {
        boolean anyWater = false;
        for (boolean w : water) {
            if (w) {
                anyWater = true;
                break;
            }
        }
        if (anyWater) {
            blurWater(key, image, water);
            applyWaterRelief(image, water, waterDepth);
        }
        if (SolarisConfig.HILLSHADING.get()) {
            applyHillshading(image, heights, haloHasData);
        }

        Level level = Minecraft.getInstance().level;
        if (level != null) {
            applyNightDarkening(image, lightEmitting, level);
        }
        if (SolarisConfig.BLACK_AND_WHITE.get()) {
            applyBlackAndWhite(image);
        }

        tile.texture.bind();
        tile.texture.upload();

        tile.texture.setFilter(false, false);
    }

    private static void buildTileLod(TileKey key, MapTile tile) {
        NativeImage image = tile.image;
        int[] heights = new int[HALO * HALO];
        boolean[] water = new boolean[HALO * HALO];
        int[] waterDepth = new int[HALO * HALO];
        boolean[] lightEmitting = new boolean[TILE_PIXELS * TILE_PIXELS];

        boolean[] haloHasData = new boolean[HALO * HALO];

        int blocksPerPixel = blocksPerPixel(key.lod());
        int worldOriginX = key.tileX() * tileWorldSize(key.lod());
        int worldOriginZ = key.tileZ() * tileWorldSize(key.lod());

        int fogColor = themeToAbgr(SolarisThemeUtils.C_FAINT);
        UnexploredStyleContext unexplored = UnexploredStyleContext.forDimension(key.dimension(), fogColor);

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double foliageBrightness = SolarisConfig.FOLIAGE_BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;
        boolean showClaims = SolarisConfig.SHOW_CLAIMS_MAP.get();

        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();
        ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks = PersistentChunkStore
                .chunksFor(key.dimension().toString());

        int lodMissingPx = 0;
        for (int pz = -1; pz <= TILE_PIXELS; pz++) {
            int worldZ = worldOriginZ + pz * blocksPerPixel;
            int chunkZ = Math.floorDiv(worldZ, 16);
            int localZ = Math.floorMod(worldZ, 16);

            for (int px = -1; px <= TILE_PIXELS; px++) {
                int worldX = worldOriginX + px * blocksPerPixel;
                int chunkX = Math.floorDiv(worldX, 16);
                int localX = Math.floorMod(worldX, 16);
                int local = localZ * 16 + localX;

                ChunkKey ckey = new ChunkKey(key.dimension(), chunkX, chunkZ);
                int[] pixels = ChunkColorCache.get(ckey);
                int[] chunkHeights = ChunkHeightCache.get(ckey);
                boolean[] chunkWater = ChunkWaterCache.get(ckey);
                boolean[] chunkLight = ChunkLightCache.get(ckey);
                int[] chunkWaterTint = ChunkWaterTintCache.get(ckey);
                int[] chunkWaterDepth = ChunkWaterDepthCache.get(ckey);
                boolean[] chunkWaterOcean = ChunkWaterOceanCache.get(ckey);
                boolean[] chunkFoliage = ChunkFoliageCache.get(ckey);
                if (pixels == null) {
                    PersistentChunkStore.Entry persisted = persistedChunks != null ? persistedChunks.get(ckey) : null;
                    if (persisted != null) {
                        pixels = persisted.pixels();
                        chunkHeights = persisted.heights();
                        chunkWater = persisted.water();
                        chunkWaterTint = persisted.waterTint();
                        chunkWaterDepth = persisted.waterDepth();
                        chunkWaterOcean = persisted.waterOcean();
                        chunkFoliage = persisted.foliage();
                    }
                }

                int height = chunkHeights != null ? chunkHeights[local] : SolarisTexture.DEFAULT_HEIGHT;
                int color = pixels != null ? pixels[local] : 0;
                if (color == 0) {
                    if (px >= 0 && px < TILE_PIXELS && pz >= 0 && pz < TILE_PIXELS) lodMissingPx++;
                    color = unexploredColor(unexplored, worldX, worldZ);
                } else {
                    if (chunkWaterTint != null && chunkWaterTint[local] != 0) {
                        color = ChunkColorSampler.compositeWaterTint(chunkWaterTint[local], chunkWaterDepth[local],
                                chunkWaterOcean[local], height);
                    }
                    if (saturation != 1.0) color = SolarisTexture.applySaturation(color, saturation);
                    if (contrast != 1.0) color = SolarisTexture.applyContrast(color, contrast);
                    if (brightness != 1.0) color = SolarisTexture.scaleBrightness(color, brightness);
                    if (chunkFoliage != null && chunkFoliage[local] && foliageBrightness != 1.0) {
                        color = SolarisTexture.scaleBrightness(color, foliageBrightness);
                    }
                    if (hasColorTint) {
                        color = SolarisTexture.scaleChannels(color, tintRed, tintGreen, tintBlue);
                    }
                }

                if (showClaims && px >= 0 && px < TILE_PIXELS && pz >= 0 && pz < TILE_PIXELS) {
                    for (SolarisOverlay overlay : overlays) {
                        Optional<Integer> tint = overlay.colorAt(key.dimension(), chunkX, chunkZ);
                        if (tint.isPresent()) color = SolarisTexture.blend(color, tint.get());
                    }
                }

                int idx = haloIdx(px, pz);
                heights[idx] = height;
                water[idx] = chunkWater != null && chunkWater[local];
                waterDepth[idx] = chunkWaterDepth != null ? chunkWaterDepth[local] : 0;
                haloHasData[idx] = chunkHeights != null;

                if (px >= 0 && px < TILE_PIXELS && pz >= 0 && pz < TILE_PIXELS) {
                    image.setPixelRGBA(px, pz, color);
                    lightEmitting[pz * TILE_PIXELS + px] = chunkLight != null && chunkLight[local];
                }
            }
        }

        for (int pz = -1; pz <= TILE_PIXELS; pz++) {
            boolean ringRow = pz < 0 || pz >= TILE_PIXELS;
            for (int px = -1; px <= TILE_PIXELS; px++) {
                if (!ringRow && px >= 0 && px < TILE_PIXELS) continue;
                int idx = haloIdx(px, pz);
                if (haloHasData[idx]) continue;
                int fallbackIdx = haloIdx(Math.max(0, Math.min(TILE_PIXELS - 1, px)),
                        Math.max(0, Math.min(TILE_PIXELS - 1, pz)));
                heights[idx] = heights[fallbackIdx];
                water[idx] = water[fallbackIdx];
                waterDepth[idx] = waterDepth[fallbackIdx];
            }
        }

        if (FullMapLog.on()) {
            int waterPx = 0;
            for (int pz = 0; pz < TILE_PIXELS; pz++) {
                for (int px = 0; px < TILE_PIXELS; px++) if (water[haloIdx(px, pz)]) waterPx++;
            }
            FullMapLog.log("tile", "lod{} {} unexploredPx={}/{} waterPx={}", key.lod(), key, lodMissingPx,
                    TILE_PIXELS * TILE_PIXELS, waterPx);
        }
        finishTile(key, tile, image, heights, water, waterDepth, lightEmitting, haloHasData);
    }

    private static void buildTile(TileKey key, MapTile tile) {
        NativeImage image = tile.image;
        int[] heights = new int[HALO * HALO];
        boolean[] water = new boolean[HALO * HALO];
        int[] waterDepth = new int[HALO * HALO];
        boolean[] lightEmitting = new boolean[TILE_PIXELS * TILE_PIXELS];
        boolean[] haloHasData = new boolean[HALO * HALO];

        int fogColor = themeToAbgr(SolarisThemeUtils.C_FAINT);
        UnexploredStyle unexploredStyle = SolarisAPI.getUnexploredStyle(key.dimension());
        double unexploredDensity = SolarisConfig.UNEXPLORED_DENSITY.get();
        double unexploredBrightness = SolarisConfig.UNEXPLORED_BRIGHTNESS.get();
        boolean unexploredImageCover = SolarisConfig.UNEXPLORED_IMAGE_COVER.get();
        int starAccent = themeToAbgr(SolarisThemeUtils.C_ACCENT);
        int starDim = themeToAbgr(SolarisThemeUtils.C_DIM);
        int starSpace = SolarisTexture.scaleBrightness(fogColor, 0.3);

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double foliageBrightness = SolarisConfig.FOLIAGE_BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;

        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();
        ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks = PersistentChunkStore
                .chunksFor(key.dimension().toString());

        int cacheHits = 0;
        int persistedHits = 0;
        int blankChunks = 0;

        for (int cz = 0; cz < TILE_CHUNKS; cz++) {
            for (int cx = 0; cx < TILE_CHUNKS; cx++) {
                int chunkX = key.tileX() * TILE_CHUNKS + cx;
                int chunkZ = key.tileZ() * TILE_CHUNKS + cz;
                ChunkKey ckey = new ChunkKey(key.dimension(), chunkX, chunkZ);
                int baseX = cx * 16;
                int baseZ = cz * 16;

                int[] pixels = ChunkColorCache.get(ckey);
                int[] chunkHeights = ChunkHeightCache.get(ckey);
                boolean[] chunkWater = ChunkWaterCache.get(ckey);
                boolean[] chunkLight = ChunkLightCache.get(ckey);
                int[] chunkWaterTint = ChunkWaterTintCache.get(ckey);
                int[] chunkWaterDepth = ChunkWaterDepthCache.get(ckey);
                boolean[] chunkWaterOcean = ChunkWaterOceanCache.get(ckey);
                boolean[] chunkFoliage = ChunkFoliageCache.get(ckey);
                if (pixels != null) {
                    cacheHits++;
                } else {
                    PersistentChunkStore.Entry persisted = persistedChunks != null ? persistedChunks.get(ckey) : null;
                    if (persisted != null) {
                        persistedHits++;
                        persisted.touch();
                        pixels = persisted.pixels();
                        chunkHeights = persisted.heights();
                        chunkWater = persisted.water();
                        chunkWaterTint = persisted.waterTint();
                        chunkWaterDepth = persisted.waterDepth();
                        chunkWaterOcean = persisted.waterOcean();
                        chunkFoliage = persisted.foliage();
                        ChunkColorCache.put(ckey, pixels);
                        ChunkHeightCache.put(ckey, chunkHeights);
                        ChunkWaterCache.put(ckey, chunkWater);
                        ChunkWaterTintCache.put(ckey, chunkWaterTint);
                        ChunkWaterDepthCache.put(ckey, chunkWaterDepth);
                        ChunkWaterOceanCache.put(ckey, chunkWaterOcean);
                        ChunkFoliageCache.put(ckey, chunkFoliage);
                    } else {
                        blankChunks++;
                    }
                }

                int tint = 0;
                boolean hasTint = false;
                if (SolarisConfig.SHOW_CLAIMS_MAP.get()) {
                    for (SolarisOverlay overlay : overlays) {
                        Optional<Integer> color = overlay.colorAt(key.dimension(), chunkX, chunkZ);
                        if (color.isPresent()) {
                            tint = color.get();
                            hasTint = true;
                        }
                    }
                }

                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int local = lz * 16 + lx;
                        int height = chunkHeights != null ? chunkHeights[local] : SolarisTexture.DEFAULT_HEIGHT;

                        int color = pixels != null ? pixels[local] : 0;
                        if (color == 0) {
                            int worldPx = chunkX * 16 + lx;
                            int worldPz = chunkZ * 16 + lz;
                            color = switch (unexploredStyle) {
                                case FOG -> fogColor;
                                case STARFIELD -> starfieldPixel(worldPx, worldPz, unexploredDensity,
                                        unexploredBrightness, starAccent, starDim, starSpace);
                                case PHOENIX -> phoenixPixel(worldPx, worldPz, unexploredDensity,
                                        unexploredBrightness);
                                case CLOUD -> cloudPixel(worldPx, worldPz, unexploredDensity, unexploredBrightness);
                                case BACKDROP -> 0;
                                case IMAGE -> {
                                    if (unexploredImageCover) yield 0;
                                    int imgColor = UnexploredImageStyle.getPixel(worldPx, worldPz);
                                    yield imgColor != 0 ? imgColor : fogColor;
                                }
                            };
                        } else {
                            if (chunkWaterTint != null && chunkWaterTint[local] != 0) {
                                color = ChunkColorSampler.compositeWaterTint(chunkWaterTint[local],
                                        chunkWaterDepth[local], chunkWaterOcean[local], height);
                            }
                            if (saturation != 1.0) color = SolarisTexture.applySaturation(color, saturation);
                            if (contrast != 1.0) color = SolarisTexture.applyContrast(color, contrast);
                            if (brightness != 1.0) color = SolarisTexture.scaleBrightness(color, brightness);
                            if (chunkFoliage != null && chunkFoliage[local] && foliageBrightness != 1.0) {
                                color = SolarisTexture.scaleBrightness(color, foliageBrightness);
                            }
                            if (hasColorTint) {
                                color = SolarisTexture.scaleChannels(color, tintRed, tintGreen, tintBlue);
                            }
                        }
                        if (hasTint) color = SolarisTexture.blend(color, tint);

                        int px = baseX + lx;
                        int pz = baseZ + lz;
                        image.setPixelRGBA(px, pz, color);
                        int idx = haloIdx(px, pz);
                        heights[idx] = height;
                        water[idx] = chunkWater != null && chunkWater[local];
                        lightEmitting[pz * TILE_PIXELS + px] = chunkLight != null && chunkLight[local];
                        waterDepth[idx] = chunkWaterDepth != null ? chunkWaterDepth[local] : 0;
                        haloHasData[idx] = chunkHeights != null;
                    }
                }
            }
        }

        fillHalo(key, heights, water, waterDepth, persistedChunks, haloHasData);

        if (FullMapLog.on()) {
            int waterPx = 0;
            for (int pz = 0; pz < TILE_PIXELS; pz++) {
                for (int px = 0; px < TILE_PIXELS; px++) if (water[haloIdx(px, pz)]) waterPx++;
            }
            int ringMissing = 0;
            int ringTotal = 0;
            for (int i = 0; i < TILE_PIXELS; i++) {
                ringTotal += 4;
                if (!haloHasData[haloIdx(-1, i)]) ringMissing++;
                if (!haloHasData[haloIdx(TILE_PIXELS, i)]) ringMissing++;
                if (!haloHasData[haloIdx(i, -1)]) ringMissing++;
                if (!haloHasData[haloIdx(i, TILE_PIXELS)]) ringMissing++;
            }
            FullMapLog.log("tile",
                    "lod0 {} chunks: cacheHits={} persistedHits={} blank={} waterPx={} haloEdgeMissing={}/{}", key,
                    cacheHits, persistedHits, blankChunks, waterPx, ringMissing, ringTotal);
            if (blankChunks > 0) FullMapLog.count("tile.builtWithBlankChunks");
            if (ringMissing > 0 && ringMissing < ringTotal) FullMapLog.count("tile.builtPartialHalo");
        }

        if (SolarisConfig.PERF_LOGGING.get() && blankChunks > 0 && shouldLogBlank(key)) {
            PhoenixSolaris.LOGGER.info(
                    "[Solaris tile] {} chunkHits={} persistedHits={} blank={} (blank means neither cache " +
                            "nor disk had data for that chunk when this tile was built)",
                    key, cacheHits, persistedHits, blankChunks);
        }

        boolean anyWater = false;
        for (boolean w : water) {
            if (w) {
                anyWater = true;
                break;
            }
        }
        if (anyWater) {
            blurWater(key, image, water);
            applyWaterRelief(image, water, waterDepth);
        }
        if (SolarisConfig.HILLSHADING.get()) {
            applyHillshading(image, heights, haloHasData);
        }

        Level level = Minecraft.getInstance().level;
        if (level != null) {
            applyNightDarkening(image, lightEmitting, level);
        }
        if (SolarisConfig.BLACK_AND_WHITE.get()) {
            applyBlackAndWhite(image);
        }

        tile.texture.bind();
        tile.texture.upload();

        tile.texture.setFilter(false, false);
    }

    private static boolean shouldLogBlank(TileKey key) {
        long now = System.currentTimeMillis();
        Long last = LAST_BLANK_LOG_MS.get(key);
        if (last != null && now - last < BLANK_LOG_INTERVAL_MS) return false;
        LAST_BLANK_LOG_MS.put(key, now);
        return true;
    }

    private static void fillHalo(TileKey key, int[] heights, boolean[] water, int[] waterDepth,
                                 ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks,
                                 boolean[] haloHasData) {
        int tileChunkMinX = key.tileX() * TILE_CHUNKS;
        int tileChunkMinZ = key.tileZ() * TILE_CHUNKS;

        for (int cz = 0; cz < TILE_CHUNKS; cz++) {
            int chunkZ = tileChunkMinZ + cz;
            fillHaloEdge(key.dimension(), tileChunkMinX - 1, chunkZ, 15, persistedChunks, heights, water, waterDepth,
                    haloHasData, true, -1, cz * 16);
            fillHaloEdge(key.dimension(), tileChunkMinX + TILE_CHUNKS, chunkZ, 0, persistedChunks, heights, water,
                    waterDepth, haloHasData, true, TILE_PIXELS, cz * 16);
        }
        for (int cx = 0; cx < TILE_CHUNKS; cx++) {
            int chunkX = tileChunkMinX + cx;
            fillHaloEdge(key.dimension(), chunkX, tileChunkMinZ - 1, 15, persistedChunks, heights, water, waterDepth,
                    haloHasData, false, cx * 16, -1);
            fillHaloEdge(key.dimension(), chunkX, tileChunkMinZ + TILE_CHUNKS, 0, persistedChunks, heights, water,
                    waterDepth, haloHasData, false, cx * 16, TILE_PIXELS);
        }
    }

    private static void fillHaloEdge(ResourceLocation dimension, int neighborChunkX, int neighborChunkZ,
                                     int neighborLocalEdge,
                                     ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks,
                                     int[] heights, boolean[] water, int[] waterDepth, boolean[] haloHasData,
                                     boolean vertical, int haloX, int haloZ) {
        ChunkKey neighborKey = new ChunkKey(dimension, neighborChunkX, neighborChunkZ);
        int[] nHeights = ChunkHeightCache.get(neighborKey);
        boolean[] nWater = ChunkWaterCache.get(neighborKey);
        int[] nWaterDepth = ChunkWaterDepthCache.get(neighborKey);
        if (nHeights == null) {
            PersistentChunkStore.Entry persisted = persistedChunks != null ? persistedChunks.get(neighborKey) : null;
            if (persisted != null) {
                nHeights = persisted.heights();
                nWater = persisted.water();
                nWaterDepth = persisted.waterDepth();
            }
        }

        for (int i = 0; i < 16; i++) {
            int x = vertical ? haloX : haloX + i;
            int z = vertical ? haloZ + i : haloZ;
            int idx = haloIdx(x, z);
            if (nHeights != null) {
                int local = vertical ? i * 16 + neighborLocalEdge : neighborLocalEdge * 16 + i;
                heights[idx] = nHeights[local];
                water[idx] = nWater != null && nWater[local];
                waterDepth[idx] = nWaterDepth != null ? nWaterDepth[local] : 0;
                haloHasData[idx] = true;
            } else {
                int fallbackX = vertical ? (haloX < 0 ? 0 : TILE_PIXELS - 1) : x;
                int fallbackZ = vertical ? z : (haloZ < 0 ? 0 : TILE_PIXELS - 1);
                int fallbackIdx = haloIdx(fallbackX, fallbackZ);
                heights[idx] = heights[fallbackIdx];
                water[idx] = water[fallbackIdx];
                waterDepth[idx] = waterDepth[fallbackIdx];
                haloHasData[idx] = false;
            }
        }
    }

    private static void blurWater(TileKey key, NativeImage image, boolean[] water) {
        final int radius = SolarisTexture.WATER_BLUR_RADIUS;
        final int pad = TILE_PIXELS + 2 * radius;
        int[] colorPad = new int[pad * pad];
        boolean[] waterPad = new boolean[pad * pad];

        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (!water[haloIdx(x, z)]) continue;
                int i = (z + radius) * pad + (x + radius);
                colorPad[i] = image.getPixelRGBA(x, z);
                waterPad[i] = true;
            }
        }
        collectWaterRing(key, radius, pad, colorPad, waterPad);

        int stride = pad + 1;
        long[] sumR = new long[stride * stride];
        long[] sumG = new long[stride * stride];
        long[] sumB = new long[stride * stride];
        int[] cnt = new int[stride * stride];
        for (int z = 0; z < pad; z++) {
            long rowR = 0;
            long rowG = 0;
            long rowB = 0;
            int rowN = 0;
            for (int x = 0; x < pad; x++) {
                int i = z * pad + x;
                if (waterPad[i]) {
                    int abgr = colorPad[i];
                    rowR += FastColor.ABGR32.red(abgr);
                    rowG += FastColor.ABGR32.green(abgr);
                    rowB += FastColor.ABGR32.blue(abgr);
                    rowN++;
                }
                int o = (z + 1) * stride + (x + 1);
                int up = z * stride + (x + 1);
                sumR[o] = sumR[up] + rowR;
                sumG[o] = sumG[up] + rowG;
                sumB[o] = sumB[up] + rowB;
                cnt[o] = cnt[up] + rowN;
            }
        }

        int[] blurred = new int[TILE_PIXELS * TILE_PIXELS];
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (!water[haloIdx(x, z)]) continue;
                int x0 = x;
                int z0 = z;
                int x1 = x + 2 * radius + 1;
                int z1 = z + 2 * radius + 1;
                int n = cnt[z1 * stride + x1] - cnt[z0 * stride + x1] - cnt[z1 * stride + x0] +
                        cnt[z0 * stride + x0];
                if (n <= 0) continue;
                long r = sumR[z1 * stride + x1] - sumR[z0 * stride + x1] - sumR[z1 * stride + x0] +
                        sumR[z0 * stride + x0];
                long g = sumG[z1 * stride + x1] - sumG[z0 * stride + x1] - sumG[z1 * stride + x0] +
                        sumG[z0 * stride + x0];
                long b = sumB[z1 * stride + x1] - sumB[z0 * stride + x1] - sumB[z1 * stride + x0] +
                        sumB[z0 * stride + x0];
                blurred[z * TILE_PIXELS + x] = FastColor.ABGR32.color(255, (int) (b / n), (int) (g / n),
                        (int) (r / n));
            }
        }

        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (water[haloIdx(x, z)] && blurred[z * TILE_PIXELS + x] != 0) {
                    image.setPixelRGBA(x, z, blurred[z * TILE_PIXELS + x]);
                }
            }
        }
    }

    private static void collectWaterRing(TileKey key, int radius, int pad, int[] colorPad, boolean[] waterPad) {
        int blocksPerPixel = blocksPerPixel(key.lod());
        int worldOriginX = key.tileX() * tileWorldSize(key.lod());
        int worldOriginZ = key.tileZ() * tileWorldSize(key.lod());
        ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks = PersistentChunkStore
                .chunksFor(key.dimension().toString());

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;

        for (int pz = -radius; pz < TILE_PIXELS + radius; pz++) {
            boolean zInside = pz >= 0 && pz < TILE_PIXELS;
            for (int px = -radius; px < TILE_PIXELS + radius; px++) {
                if (zInside && px >= 0 && px < TILE_PIXELS) continue;
                int worldX = worldOriginX + px * blocksPerPixel;
                int worldZ = worldOriginZ + pz * blocksPerPixel;
                ChunkKey ckey = new ChunkKey(key.dimension(), Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
                int local = Math.floorMod(worldZ, 16) * 16 + Math.floorMod(worldX, 16);

                int[] pixels = ChunkColorCache.get(ckey);
                boolean[] chunkWater = ChunkWaterCache.get(ckey);
                int[] chunkHeights = ChunkHeightCache.get(ckey);
                int[] chunkWaterTint = ChunkWaterTintCache.get(ckey);
                int[] chunkWaterDepth = ChunkWaterDepthCache.get(ckey);
                boolean[] chunkWaterOcean = ChunkWaterOceanCache.get(ckey);
                if (pixels == null) {
                    PersistentChunkStore.Entry persisted = persistedChunks != null ? persistedChunks.get(ckey) : null;
                    if (persisted == null) continue;
                    pixels = persisted.pixels();
                    chunkWater = persisted.water();
                    chunkHeights = persisted.heights();
                    chunkWaterTint = persisted.waterTint();
                    chunkWaterDepth = persisted.waterDepth();
                    chunkWaterOcean = persisted.waterOcean();
                }
                if (chunkWater == null || !chunkWater[local]) continue;
                int color = pixels[local];
                if (color == 0) continue;

                int height = chunkHeights != null ? chunkHeights[local] : SolarisTexture.DEFAULT_HEIGHT;
                if (chunkWaterTint != null && chunkWaterTint[local] != 0 && chunkWaterDepth != null &&
                        chunkWaterOcean != null) {
                    color = ChunkColorSampler.compositeWaterTint(chunkWaterTint[local], chunkWaterDepth[local],
                            chunkWaterOcean[local], height);
                }
                if (saturation != 1.0) color = SolarisTexture.applySaturation(color, saturation);
                if (contrast != 1.0) color = SolarisTexture.applyContrast(color, contrast);
                if (brightness != 1.0) color = SolarisTexture.scaleBrightness(color, brightness);
                if (hasColorTint) color = SolarisTexture.scaleChannels(color, tintRed, tintGreen, tintBlue);

                int i = (pz + radius) * pad + (px + radius);
                colorPad[i] = color;
                waterPad[i] = true;
            }
        }
    }

    private static void applyWaterRelief(NativeImage image, boolean[] water, int[] waterDepth) {
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int idx = haloIdx(x, z);
                if (!water[idx]) continue;

                int idxWest = haloIdx(x - 1, z);
                int idxEast = haloIdx(x + 1, z);
                int idxNorth = haloIdx(x, z - 1);
                int idxSouth = haloIdx(x, z + 1);

                int depthHere = waterDepth[idx];
                int depthWest = water[idxWest] ? waterDepth[idxWest] : depthHere;
                int depthEast = water[idxEast] ? waterDepth[idxEast] : depthHere;
                int depthNorth = water[idxNorth] ? waterDepth[idxNorth] : depthHere;
                int depthSouth = water[idxSouth] ? waterDepth[idxSouth] : depthHere;

                float dzdx = -(depthEast - depthWest) * 0.5f * SolarisTexture.WATER_RELIEF_NEIGHBOR_SCALE;
                float dzdy = -(depthSouth - depthNorth) * 0.5f * SolarisTexture.WATER_RELIEF_NEIGHBOR_SCALE;
                float factor = shadeFactor(dzdx, dzdy, SolarisTexture.WATER_RELIEF_GAIN);
                factor = Mth.clamp(factor, 1f - SolarisTexture.WATER_RELIEF_CLAMP,
                        1f + SolarisTexture.WATER_RELIEF_CLAMP);
                if (factor == 1f) continue;

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, SolarisTexture.scaleBrightness(abgr, factor));
            }
        }
    }

    private static void applyHillshading(NativeImage image, int[] heights, boolean[] haloHasData) {
        double strength = SolarisConfig.HILLSHADING_STRENGTH.get();
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int idx = haloIdx(x, z);
                int idxW = haloIdx(x - 1, z);
                int idxE = haloIdx(x + 1, z);
                int idxN = haloIdx(x, z - 1);
                int idxS = haloIdx(x, z + 1);

                if (!haloHasData[idx] || !haloHasData[idxW] || !haloHasData[idxE] ||
                        !haloHasData[idxN] || !haloHasData[idxS])
                    continue;

                float dzdx = (heights[idxE] - heights[idxW]) * 0.5f;
                float dzdy = (heights[idxS] - heights[idxN]) * 0.5f;
                float factor = shadeFactor(dzdx, dzdy, (float) strength * SolarisTexture.HILLSHADE_GAIN);
                factor = Mth.clamp(factor, 0.3f, 1.8f);

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, SolarisTexture.scaleBrightness(abgr, factor));
            }
        }
    }

    private static float shadeFactor(float dzdx, float dzdy, float gain) {
        float nx = -dzdx;
        float ny = -dzdy;
        float nz = 1f;
        float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float shade = (nx * SolarisTexture.LIGHT_X + ny * SolarisTexture.LIGHT_Y + nz * SolarisTexture.LIGHT_Z) /
                (nLen * SolarisTexture.LIGHT_LEN);
        return 1f + gain * (shade - SolarisTexture.FLAT_SHADE);
    }

    private static void applyNightDarkening(NativeImage image, boolean[] lightEmitting, Level level) {
        double factor = ChunkColorSampler.nightFactor(level.getDayTime());
        if (factor <= 0.0) return;

        double strength = SolarisConfig.NIGHT_MODE_STRENGTH.get();
        double multiplier = Mth.lerp(factor, 1.0, 1.0 - strength);

        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int idx = z * TILE_PIXELS + x;
                if (lightEmitting[idx]) continue;
                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, SolarisTexture.scaleBrightness(abgr, multiplier));
            }
        }
    }

    private static void applyBlackAndWhite(NativeImage image) {
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int abgr = image.getPixelRGBA(x, z);
                int a = FastColor.ABGR32.alpha(abgr);
                int r = FastColor.ABGR32.red(abgr);
                int g = FastColor.ABGR32.green(abgr);
                int b = FastColor.ABGR32.blue(abgr);
                int gray = SolarisTexture.clampChannel(Math.round(0.299f * r + 0.587f * g + 0.114f * b));
                image.setPixelRGBA(x, z, FastColor.ABGR32.color(a, gray, gray, gray));
            }
        }
    }
}
