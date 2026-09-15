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
                dirty.add(new TileKey(dimension, Math.floorDiv(chunkX, chunksPerTile),
                        Math.floorDiv(chunkZ, chunksPerTile), lod));
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
                    eldest.getValue().close();
                    return true;
                }
            });

    private static final Set<TileKey> DIRTY = ConcurrentHashMap.newKeySet();

    private static double lastNightFactorBucket = -1;
    private static final double NIGHT_FACTOR_BUCKET = 0.05;

    private MapTileCache() {}

    public static void ensureCapacity(int neededTiles) {
        if (neededTiles > capacity) {
            capacity = Math.min(HARD_CEILING, neededTiles);
        }
    }

    public static void checkNightFactor(Level level) {
        if (level == null) return;
        double bucket = Math.round(ChunkColorSampler.nightFactor(level.getDayTime()) / NIGHT_FACTOR_BUCKET) *
                NIGHT_FACTOR_BUCKET;
        if (bucket == lastNightFactorBucket) return;
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

        if (!PersistentChunkStore.isLoaded(key.dimension().toString())) {
            if (dirty) DIRTY.add(key);
            return tile;
        }

        if (buildBudget[0] <= 0) {
            DIRTY.add(key);
            return tile;
        }
        buildBudget[0]--;

        if (tile == null) {
            ResourceLocation id = new ResourceLocation(PhoenixSolaris.MOD_ID,
                    "dynamic/tile_" + nextTileId++ + "_" + key.tileX() + "_" + key.tileZ() + "_" + key.lod());
            tile = new MapTile(id);
            TILES.put(key, tile);
        }
        try {
            if (key.lod() == 0) {
                buildTile(key, tile);
            } else {
                buildTileLod(key, tile);
            }
        } catch (Exception e) {

            DIRTY.add(key);
            PhoenixSolaris.LOGGER.error("[Solaris] Failed to build map tile {}. " +
                    "Will retry next frame", key, e);
        }
        return tile;
    }

    public static void markDirty(ChunkKey key) {
        TileKey.markChunkDirty(DIRTY, key.dimension(), key.x(), key.z());
    }

    public static void clearAll() {
        synchronized (TILES) {
            for (MapTile tile : TILES.values()) tile.close();
            TILES.clear();
        }
        DIRTY.clear();
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
            case IMAGE -> {
                if (ctx.imageCover()) yield 0;
                int imgColor = UnexploredImageStyle.getPixel(worldPx, worldPz);
                yield imgColor != 0 ? imgColor : ctx.fogColor();
            }
        };
    }

    private static void finishTile(MapTile tile, NativeImage image, int[] heights, boolean[] water,
                                   int[] waterDepth, boolean[] lightEmitting, boolean[] haloHasData) {
        boolean anyWater = false;
        for (boolean w : water) {
            if (w) {
                anyWater = true;
                break;
            }
        }
        if (anyWater) {
            blurWater(image, water);
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

        finishTile(tile, image, heights, water, waterDepth, lightEmitting, haloHasData);
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

        if (SolarisConfig.PERF_LOGGING.get() && blankChunks > 0) {
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
            blurWater(image, water);
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

    // Two-pass separable box blur (horizontal sliding-window sum, then vertical) instead of the
    // previous full (2*radius+1)^2 convolution per water pixel — at radius 3 that was 49 samples
    // (each a NativeImage read) per water pixel, and since an LOD change during zoom invalidates
    // every visible tile's TileKey at once, every ocean-heavy tile among them paid that quadratic
    // cost simultaneously on the render thread — read as "a lot of lag" while zooming. Same
    // rolling-sum technique SolarisTexture.blurWater already uses for its own (larger) buffer;
    // this just brings the per-tile version up to the same complexity. A 2D box sum decomposes
    // exactly into a horizontal window-sum pass followed by a vertical window-sum pass over those
    // row sums, so this produces the same averages as the old convolution (edge handling differs
    // slightly: out-of-tile neighbors are now excluded from the count instead of clamp-sampling
    // the boundary pixel repeatedly, which only affects the outermost `radius` pixels of a tile
    // and was never anything but a same-tile clamp to begin with — neither version ever blurred
    // using a neighboring tile's actual water color).
    private static void blurWater(NativeImage image, boolean[] water) {
        int radius = SolarisTexture.WATER_BLUR_RADIUS;

        int[] hSumR = new int[TILE_PIXELS * TILE_PIXELS];
        int[] hSumG = new int[TILE_PIXELS * TILE_PIXELS];
        int[] hSumB = new int[TILE_PIXELS * TILE_PIXELS];
        int[] hCount = new int[TILE_PIXELS * TILE_PIXELS];

        for (int z = 0; z < TILE_PIXELS; z++) {
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;
            int count = 0;
            for (int x = 0; x <= Math.min(radius, TILE_PIXELS - 1); x++) {
                if (!water[haloIdx(x, z)]) continue;
                int abgr = image.getPixelRGBA(x, z);
                sumR += FastColor.ABGR32.red(abgr);
                sumG += FastColor.ABGR32.green(abgr);
                sumB += FastColor.ABGR32.blue(abgr);
                count++;
            }
            int rowBase = z * TILE_PIXELS;
            hSumR[rowBase] = sumR;
            hSumG[rowBase] = sumG;
            hSumB[rowBase] = sumB;
            hCount[rowBase] = count;

            for (int x = 1; x < TILE_PIXELS; x++) {
                int addX = x + radius;
                int removeX = x - radius - 1;
                if (addX < TILE_PIXELS && water[haloIdx(addX, z)]) {
                    int abgr = image.getPixelRGBA(addX, z);
                    sumR += FastColor.ABGR32.red(abgr);
                    sumG += FastColor.ABGR32.green(abgr);
                    sumB += FastColor.ABGR32.blue(abgr);
                    count++;
                }
                if (removeX >= 0 && water[haloIdx(removeX, z)]) {
                    int abgr = image.getPixelRGBA(removeX, z);
                    sumR -= FastColor.ABGR32.red(abgr);
                    sumG -= FastColor.ABGR32.green(abgr);
                    sumB -= FastColor.ABGR32.blue(abgr);
                    count--;
                }
                hSumR[rowBase + x] = sumR;
                hSumG[rowBase + x] = sumG;
                hSumB[rowBase + x] = sumB;
                hCount[rowBase + x] = count;
            }
        }

        int[] blurred = new int[TILE_PIXELS * TILE_PIXELS];
        for (int x = 0; x < TILE_PIXELS; x++) {
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;
            int count = 0;
            for (int z = 0; z <= Math.min(radius, TILE_PIXELS - 1); z++) {
                int idx = z * TILE_PIXELS + x;
                sumR += hSumR[idx];
                sumG += hSumG[idx];
                sumB += hSumB[idx];
                count += hCount[idx];
            }
            if (water[haloIdx(x, 0)] && count > 0) {
                blurred[x] = FastColor.ABGR32.color(255, sumB / count, sumG / count, sumR / count);
            }

            for (int z = 1; z < TILE_PIXELS; z++) {
                int addZ = z + radius;
                int removeZ = z - radius - 1;
                if (addZ < TILE_PIXELS) {
                    int idx = addZ * TILE_PIXELS + x;
                    sumR += hSumR[idx];
                    sumG += hSumG[idx];
                    sumB += hSumB[idx];
                    count += hCount[idx];
                }
                if (removeZ >= 0) {
                    int idx = removeZ * TILE_PIXELS + x;
                    sumR -= hSumR[idx];
                    sumG -= hSumG[idx];
                    sumB -= hSumB[idx];
                    count -= hCount[idx];
                }
                if (water[haloIdx(x, z)] && count > 0) {
                    blurred[z * TILE_PIXELS + x] = FastColor.ABGR32.color(255, sumB / count, sumG / count,
                            sumR / count);
                }
            }
        }

        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (water[haloIdx(x, z)]) image.setPixelRGBA(x, z, blurred[z * TILE_PIXELS + x]);
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
