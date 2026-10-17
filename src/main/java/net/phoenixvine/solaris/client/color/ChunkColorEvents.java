package net.phoenixvine.solaris.client.color;

import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.SolarisTexture;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChunkColorEvents {

    static final Set<ChunkKey> FALLBACK_TAINTED = ConcurrentHashMap.newKeySet();

    static final Map<ChunkKey, Integer> FALLBACK_RETRY_COUNT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PersistentChunkStore.flushOnLogout();
        PersistentCaveStore.flushOnLogout();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        PhoenixSolaris.LOGGER.info("[Solaris] onLoggingIn fired. Clearing in-memory chunk caches");

        ChunkColorCache.clear();
        ChunkHeightCache.clear();
        ChunkWaterCache.clear();
        ChunkLightCache.clear();
        ChunkRailCache.clear();
        ChunkWaterTintCache.clear();
        ChunkWaterDepthCache.clear();
        ChunkWaterOceanCache.clear();
        ChunkFoliageCache.clear();
        FALLBACK_TAINTED.clear();
        FALLBACK_RETRY_COUNT.clear();
        PENDING_NEIGHBORS.clear();
        MapTileCache.clearAll();
        SolarisTexture.invalidateAll();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || !level.isClientSide()) return;
        if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk)) return;

        if (level.dimensionType().hasCeiling()) return;

        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WORLD_MAP, level.dimension().location())
                .atLeast(SolarisFeatureState.ENABLED)) {
            return;
        }

        var pos = event.getChunk().getPos();

        ChunkKey key = ChunkKey.of(level, pos);
        SolarisProfiler.time("chunkSample", () -> resample(level, key, pos));

        for (int[] d : new int[][] { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } }) {
            ChunkKey neighborKey = new ChunkKey(key.dimension(), key.x() + d[0], key.z() + d[1]);
            if (ChunkColorCache.contains(neighborKey)) PENDING_NEIGHBORS.add(neighborKey);
        }
    }

    private static final Set<ChunkKey> PENDING_NEIGHBORS = new LinkedHashSet<>();

    /** Outermost blocks on a side facing an unloaded chunk get replaced with data from just inside. */
    private static final int EDGE_TRIM = 2;

    /**
     * Blocks along a chunk side whose neighbouring chunk isn't loaded can't be sampled reliably (biome and
     * neighbour lookups return placeholders, which shows up as a thin off-colour line around the edge of
     * explored terrain). Until that neighbour loads and the chunk is resampled, copy the nearest reliable
     * blocks outward over them so the edge matches the terrain beside it.
     */
    static void trimUnloadedEdges(Level level, ChunkKey key, ChunkColorSampler.ColorSample c,
                                  ChunkColorSampler.HeightSample h) {
        boolean west = !level.hasChunk(key.x() - 1, key.z());
        boolean east = !level.hasChunk(key.x() + 1, key.z());
        boolean north = !level.hasChunk(key.x(), key.z() - 1);
        boolean south = !level.hasChunk(key.x(), key.z() + 1);
        if (!west && !east && !north && !south) return;

        for (int step = 0; step < EDGE_TRIM; step++) {
            for (int i = 0; i < 16; i++) {
                if (west) copyCell(c, h, i * 16 + step, i * 16 + EDGE_TRIM);
                if (east) copyCell(c, h, i * 16 + (15 - step), i * 16 + (15 - EDGE_TRIM));
            }
            for (int i = 0; i < 16; i++) {
                if (north) copyCell(c, h, step * 16 + i, EDGE_TRIM * 16 + i);
                if (south) copyCell(c, h, (15 - step) * 16 + i, (15 - EDGE_TRIM) * 16 + i);
            }
        }
    }

    private static void copyCell(ChunkColorSampler.ColorSample c, ChunkColorSampler.HeightSample h, int to,
                                 int from) {
        c.pixels[to] = c.pixels[from];
        c.lightEmitting[to] = c.lightEmitting[from];
        c.waterTint[to] = c.waterTint[from];
        c.waterDepth[to] = c.waterDepth[from];
        c.waterOcean[to] = c.waterOcean[from];
        c.foliage[to] = c.foliage[from];
        h.heights[to] = h.heights[from];
        h.water[to] = h.water[from];
        h.rails[to] = h.rails[from];
    }

    /** Drops queued work that belonged to the dimension we just left. */
    static void onDimensionChanged() {
        PENDING_NEIGHBORS.clear();
    }

    /** True if all 8 chunks around {@code key} are loaded (water blend radius never reaches past 1 chunk). */
    static boolean neighborsLoaded(Level level, ChunkKey key) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                if (!level.hasChunk(key.x() + dx, key.z() + dz)) return false;
            }
        }
        return true;
    }

    static void drainNeighborResamples(Level level, int budget) {
        Iterator<ChunkKey> it = PENDING_NEIGHBORS.iterator();
        while (budget > 0 && it.hasNext()) {
            ChunkKey key = it.next();
            it.remove();
            if (!key.dimension().equals(level.dimension().location()) || !level.hasChunk(key.x(), key.z())) continue;
            budget--;
            SolarisProfiler.time("chunkSample", () -> resample(level, key, key.toChunkPos()));
        }
    }

    public static void resample(Level level, ChunkKey key, net.minecraft.world.level.ChunkPos pos) {
        ChunkColorSampler.ColorSample colorSample = ChunkColorSampler.sample(level, pos);
        ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level, pos);
        trimUnloadedEdges(level, key, colorSample, heightSample);

        if (colorSample.hadFallback) {
            FALLBACK_TAINTED.add(key);

            if (ChunkColorCache.contains(key) || PersistentChunkStore.get(key) != null) return;
        } else {
            FALLBACK_TAINTED.remove(key);
        }

        if (!ChunkColorCache.contains(key)) SolarisTexture.markChunkDataChanged();

        ChunkColorCache.put(key, colorSample.pixels);
        ChunkLightCache.put(key, colorSample.lightEmitting);
        ChunkHeightCache.put(key, heightSample.heights);
        ChunkWaterCache.put(key, heightSample.water);
        ChunkRailCache.put(key, heightSample.rails);
        ChunkWaterTintCache.put(key, colorSample.waterTint);
        ChunkWaterDepthCache.put(key, colorSample.waterDepth);
        ChunkWaterOceanCache.put(key, colorSample.waterOcean);
        ChunkFoliageCache.put(key, colorSample.foliage);

        PersistentChunkStore.put(key, colorSample.pixels, heightSample.heights, heightSample.water,
                colorSample.waterTint, colorSample.waterDepth, colorSample.waterOcean, colorSample.foliage);
        MapTileCache.markDirty(key);
    }
}
