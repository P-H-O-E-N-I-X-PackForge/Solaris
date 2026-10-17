package net.phoenixvine.solaris.client.color;

import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
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

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
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
