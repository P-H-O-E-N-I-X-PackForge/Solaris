package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.CaveColorSampler;
import net.phoenixvine.solaris.client.color.PersistentCaveStore;
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

public final class CaveTileCache {

    public static final int TILE_CHUNKS = MapTileCache.TILE_CHUNKS;
    public static final int TILE_PIXELS = MapTileCache.TILE_PIXELS;

    // A fixed 512 was far too small: the render loop touches every on-screen tile every frame,
    // and a moderately zoomed-out view (e.g. default zoomMin=0.25 on a 1440p+ screen) needs
    // several thousand 128-block tiles to cover it. Once the on-screen working set exceeds the
    // cache size, tiles evict each other within a single frame's own iteration (early tiles get
    // pushed out to fit later ones), then reverse next frame — that thrash is what read as
    // panning "flicker" and as the area around the player going blank (whichever tiles lost the
    // LRU race that frame). ensureCapacity grows the cap to fit whatever's actually on screen,
    // bounded by HARD_CEILING so an extreme zoom-out can't balloon memory unboundedly.
    private static volatile int capacity = 512;
    private static final int HARD_CEILING = 4096;
    private static final int REVEAL_RADIUS_CHUNKS = 3;
    private static final long LIVE_REBUILD_THROTTLE_MS = 250L;

    // See MapTileCache's matching comment/fields — same LOD approach, reusing its math so the two
    // tile systems pick consistent levels for the same zoom. lod > 0 tiles here read only from
    // PersistentCaveStore (no live re-sampling): at a zoom coarse enough to need an overview tile,
    // the player's few-chunk live-reveal radius is well below one texture pixel anyway.
    public static final int MAX_LOD = MapTileCache.MAX_LOD;

    public record TileKey(ResourceLocation dimension, int tileX, int tileZ, int yBucket, int lod) {

        // See the matching constructor in MapTileCache.TileKey — keeps the pre-LOD 4-arg
        // constructor callable for external mods compiled against the old API.
        public TileKey(ResourceLocation dimension, int tileX, int tileZ, int yBucket) {
            this(dimension, tileX, tileZ, yBucket, 0);
        }
    }

    public static final class CaveTile implements AutoCloseable {

        private final ResourceLocation textureId;
        private final NativeImage image;
        private final DynamicTexture texture;
        private long lastLiveRebuildMillis = 0L;

        private CaveTile(ResourceLocation textureId) {
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

    private static final Map<TileKey, CaveTile> TILES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<TileKey, CaveTile> eldest) {
                    if (size() <= capacity) return false;
                    eldest.getValue().close();
                    return true;
                }
            });

    private static final Set<TileKey> DIRTY = ConcurrentHashMap.newKeySet();

    private CaveTileCache() {}

    public static void ensureCapacity(int neededTiles) {
        if (neededTiles > capacity) {
            capacity = Math.min(HARD_CEILING, neededTiles);
        }
    }

    public static void clearAll() {
        synchronized (TILES) {
            for (CaveTile tile : TILES.values()) tile.close();
            TILES.clear();
        }
        DIRTY.clear();
    }

    private static boolean overlapsReveal(TileKey key, int playerChunkX, int playerChunkZ) {
        int tileMinX = key.tileX() * TILE_CHUNKS;
        int tileMinZ = key.tileZ() * TILE_CHUNKS;
        int tileMaxX = tileMinX + TILE_CHUNKS - 1;
        int tileMaxZ = tileMinZ + TILE_CHUNKS - 1;
        return tileMinX <= playerChunkX + REVEAL_RADIUS_CHUNKS && tileMaxX >= playerChunkX - REVEAL_RADIUS_CHUNKS &&
                tileMinZ <= playerChunkZ + REVEAL_RADIUS_CHUNKS && tileMaxZ >= playerChunkZ - REVEAL_RADIUS_CHUNKS;
    }

    // See the matching overload in MapTileCache — kept for the same pre-budget external callers.
    public static CaveTile getOrBuildTile(TileKey key, Level level, Player player) {
        return getOrBuildTile(key, level, player, new int[] { Integer.MAX_VALUE });
    }

    public static CaveTile getOrBuildTile(TileKey key, Level level, Player player, int[] buildBudget) {
        CaveTile tile = TILES.get(key);
        boolean dirty = DIRTY.remove(key);

        int playerChunkX = player != null ? Mth.floor(player.getX()) >> 4 : Integer.MAX_VALUE / 2;
        int playerChunkZ = player != null ? Mth.floor(player.getZ()) >> 4 : Integer.MAX_VALUE / 2;
        // Live re-sampling only makes sense at lod 0: at any coarser lod the player's few-chunk
        // reveal radius is smaller than a single texture pixel, so there's nothing meaningful to
        // refresh live.
        boolean live = key.lod() == 0 && level != null && player != null &&
                overlapsReveal(key, playerChunkX, playerChunkZ);

        if (tile != null && !dirty) {
            if (!live) return tile;
            long now = System.currentTimeMillis();
            if (now - tile.lastLiveRebuildMillis < LIVE_REBUILD_THROTTLE_MS) return tile;
        }

        if (buildBudget[0] <= 0) {
            DIRTY.add(key);
            return tile;
        }
        buildBudget[0]--;

        if (tile == null) {
            ResourceLocation id = new ResourceLocation(PhoenixSolaris.MOD_ID,
                    "dynamic/cave_tile_" + nextTileId++ + "_" + key.tileX() + "_" + key.tileZ() + "_" +
                            key.yBucket() + "_" + key.lod());
            tile = new CaveTile(id);
            TILES.put(key, tile);
        }

        if (key.lod() == 0) {
            buildTile(key, tile, level, player, playerChunkX, playerChunkZ);
        } else {
            buildTileLod(key, tile);
        }
        if (live) tile.lastLiveRebuildMillis = System.currentTimeMillis();
        return tile;
    }

    private static int themeToAbgr(int argb) {
        return FastColor.ABGR32.color(FastColor.ARGB32.alpha(argb), FastColor.ARGB32.blue(argb),
                FastColor.ARGB32.green(argb), FastColor.ARGB32.red(argb));
    }

    // See MapTileCache.buildTileLod for the reasoning — same nearest-neighbor decimation, reading
    // straight from PersistentCaveStore instead of live block scanning since this only runs at a
    // zoom coarse enough that per-block/per-chunk cave detail wouldn't be visible anyway.
    private static void buildTileLod(TileKey key, CaveTile tile) {
        NativeImage image = tile.image;
        String dimensionStr = key.dimension().toString();

        int blocksPerPixel = MapTileCache.blocksPerPixel(key.lod());
        int worldOriginX = key.tileX() * MapTileCache.tileWorldSize(key.lod());
        int worldOriginZ = key.tileZ() * MapTileCache.tileWorldSize(key.lod());

        int fogColor = themeToAbgr(SolarisThemeUtils.C_FAINT);
        UnexploredStyle unexploredStyle = SolarisAPI.getUnexploredStyle(key.dimension());
        double unexploredDensity = SolarisConfig.UNEXPLORED_DENSITY.get();
        double unexploredBrightness = SolarisConfig.UNEXPLORED_BRIGHTNESS.get();
        int starAccent = themeToAbgr(SolarisThemeUtils.C_ACCENT);
        int starDim = themeToAbgr(SolarisThemeUtils.C_DIM);
        int starSpace = SolarisTexture.scaleBrightness(fogColor, 0.15);
        boolean unexploredImageCover = SolarisConfig.UNEXPLORED_IMAGE_COVER.get();

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;

        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();
        boolean showClaims = SolarisConfig.SHOW_CLAIMS_MAP.get();

        for (int pz = 0; pz < TILE_PIXELS; pz++) {
            int worldZ = worldOriginZ + pz * blocksPerPixel;
            int chunkZ = Math.floorDiv(worldZ, 16);
            int localZ = Math.floorMod(worldZ, 16);

            for (int px = 0; px < TILE_PIXELS; px++) {
                int worldX = worldOriginX + px * blocksPerPixel;
                int chunkX = Math.floorDiv(worldX, 16);
                int localX = Math.floorMod(worldX, 16);
                int local = localZ * 16 + localX;

                int[] pixels = PersistentCaveStore.get(dimensionStr, chunkX, chunkZ, key.yBucket());

                int tint = 0;
                boolean hasTint = false;
                if (showClaims) {
                    for (SolarisOverlay overlay : overlays) {
                        Optional<Integer> color = overlay.colorAt(key.dimension(), chunkX, chunkZ);
                        if (color.isPresent()) {
                            tint = color.get();
                            hasTint = true;
                        }
                    }
                }

                int color = pixels != null ? pixels[local] : 0;
                if (color == 0) {
                    if (pixels != null) {
                        color = fogColor;
                    } else {
                        color = switch (unexploredStyle) {
                            case FOG -> FastColor.ABGR32.color(255, 0, 0, 0);
                            case STARFIELD -> MapTileCache.starfieldPixel(worldX, worldZ, unexploredDensity,
                                    unexploredBrightness, starAccent, starDim, starSpace);
                            case PHOENIX -> MapTileCache.phoenixPixel(worldX, worldZ, unexploredDensity,
                                    unexploredBrightness);
                            case CLOUD -> MapTileCache.cloudPixel(worldX, worldZ, unexploredDensity,
                                    unexploredBrightness);
                            case IMAGE -> {
                                if (unexploredImageCover) yield 0;
                                int imgColor = UnexploredImageStyle.getPixel(worldX, worldZ);
                                yield imgColor != 0 ? imgColor : FastColor.ABGR32.color(255, 0, 0, 0);
                            }
                        };
                    }
                } else {
                    if (saturation != 1.0) color = SolarisTexture.applySaturation(color, saturation);
                    if (contrast != 1.0) color = SolarisTexture.applyContrast(color, contrast);
                    if (brightness != 1.0) color = SolarisTexture.scaleBrightness(color, brightness);
                    if (hasColorTint) {
                        color = SolarisTexture.scaleChannels(color, tintRed, tintGreen, tintBlue);
                    }
                }
                if (hasTint) color = SolarisTexture.blend(color, tint);
                image.setPixelRGBA(px, pz, color);
            }
        }

        if (SolarisConfig.BLACK_AND_WHITE.get()) {
            applyBlackAndWhite(image);
        }

        tile.texture.bind();
        tile.texture.upload();

        tile.texture.setFilter(false, false);
    }

    private static void buildTile(TileKey key, CaveTile tile, Level level, Player player, int playerChunkX,
                                  int playerChunkZ) {
        NativeImage image = tile.image;
        String dimensionStr = key.dimension().toString();

        int playerY = player != null ? Mth.floor(player.getY()) : 0;

        int fogColor = themeToAbgr(SolarisThemeUtils.C_FAINT);

        UnexploredStyle unexploredStyle = SolarisAPI.getUnexploredStyle(key.dimension());
        double unexploredDensity = SolarisConfig.UNEXPLORED_DENSITY.get();
        double unexploredBrightness = SolarisConfig.UNEXPLORED_BRIGHTNESS.get();
        int starAccent = themeToAbgr(SolarisThemeUtils.C_ACCENT);
        int starDim = themeToAbgr(SolarisThemeUtils.C_DIM);
        int starSpace = SolarisTexture.scaleBrightness(fogColor, 0.15);
        boolean unexploredImageCover = SolarisConfig.UNEXPLORED_IMAGE_COVER.get();

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;

        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();
        boolean showClaims = SolarisConfig.SHOW_CLAIMS_MAP.get();

        int tileChunkMinX = key.tileX() * TILE_CHUNKS;
        int tileChunkMinZ = key.tileZ() * TILE_CHUNKS;

        for (int cz = 0; cz < TILE_CHUNKS; cz++) {
            for (int cx = 0; cx < TILE_CHUNKS; cx++) {
                int chunkX = tileChunkMinX + cx;
                int chunkZ = tileChunkMinZ + cz;

                boolean caveReveal = level != null && player != null &&
                        Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ)) <=
                                REVEAL_RADIUS_CHUNKS;

                int[] pixels;
                if (caveReveal) {
                    pixels = CaveColorSampler.sample(level, new ChunkPos(chunkX, chunkZ), playerY);
                    PersistentCaveStore.put(dimensionStr, chunkX, chunkZ, key.yBucket(), pixels);
                } else {
                    pixels = PersistentCaveStore.get(dimensionStr, chunkX, chunkZ, key.yBucket());
                }
                int tint = 0;
                boolean hasTint = false;
                if (showClaims) {
                    for (SolarisOverlay overlay : overlays) {
                        Optional<Integer> color = overlay.colorAt(key.dimension(), chunkX, chunkZ);
                        if (color.isPresent()) {
                            tint = color.get();
                            hasTint = true;
                        }
                    }
                }

                int baseX = cx * 16;
                int baseZ = cz * 16;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int local = lz * 16 + lx;
                        int color = pixels != null ? pixels[local] : 0;
                        if (color == 0) {
                            if (pixels != null) {
                                color = fogColor;
                            } else {
                                int worldPx = chunkX * 16 + lx;
                                int worldPz = chunkZ * 16 + lz;
                                color = switch (unexploredStyle) {
                                    case FOG -> FastColor.ABGR32.color(255, 0, 0, 0);
                                    case STARFIELD -> MapTileCache.starfieldPixel(worldPx, worldPz,
                                            unexploredDensity, unexploredBrightness, starAccent, starDim, starSpace);
                                    case PHOENIX -> MapTileCache.phoenixPixel(worldPx, worldPz, unexploredDensity,
                                            unexploredBrightness);
                                    case CLOUD -> MapTileCache.cloudPixel(worldPx, worldPz, unexploredDensity,
                                            unexploredBrightness);
                                    case IMAGE -> {
                                        if (unexploredImageCover) yield 0;
                                        int imgColor = UnexploredImageStyle.getPixel(worldPx, worldPz);
                                        yield imgColor != 0 ? imgColor : FastColor.ABGR32.color(255, 0, 0, 0);
                                    }
                                };
                            }
                        } else {
                            if (saturation != 1.0) color = SolarisTexture.applySaturation(color, saturation);
                            if (contrast != 1.0) color = SolarisTexture.applyContrast(color, contrast);
                            if (brightness != 1.0) color = SolarisTexture.scaleBrightness(color, brightness);
                            if (hasColorTint) {
                                color = SolarisTexture.scaleChannels(color, tintRed, tintGreen, tintBlue);
                            }
                        }
                        if (hasTint) color = SolarisTexture.blend(color, tint);
                        image.setPixelRGBA(baseX + lx, baseZ + lz, color);
                    }
                }
            }
        }

        if (SolarisConfig.BLACK_AND_WHITE.get()) {
            applyBlackAndWhite(image);
        }

        tile.texture.bind();
        tile.texture.upload();

        tile.texture.setFilter(false, false);
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
