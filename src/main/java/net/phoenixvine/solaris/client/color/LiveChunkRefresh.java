package net.phoenixvine.solaris.client.color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.DimensionModes;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LiveChunkRefresh {

    private static int tickCounter = 0;
    private static int taintedRetryTickCounter = 0;

    private static final int TAINTED_RETRY_INTERVAL_TICKS = 20;

    private static final int MAX_FALLBACK_RETRIES = 10;

    /** Each retry is a full chunk sample, so only a few are done per pass; the rest wait for the next one. */
    private static final int TAINTED_RETRIES_PER_PASS = 4;
    private static int taintedRetryCursor = 0;
    private static int taintedPass = 0;

    private static final int CHUNKS_PER_TICK_BUDGET = 16;
    private static final int NEIGHBOR_RESAMPLES_PER_TICK = 4;
    private static final Deque<ChunkKey> PENDING_SWEEP = new ArrayDeque<>();
    private static boolean sweepRefreshedAny = false;

    private static final int SAVE_INTERVAL_TICKS = 200;
    private static int saveTickCounter = 0;

    private LiveChunkRefresh() {}

    private static net.minecraft.resources.ResourceLocation lastDimension = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        net.minecraft.resources.ResourceLocation currentDimension = level.dimension().location();
        if (!currentDimension.equals(lastDimension)) {
            lastDimension = currentDimension;
            PENDING_SWEEP.clear();
            sweepRefreshedAny = false;
            tickCounter = 0;
            taintedRetryTickCounter = 0;
            taintedRetryCursor = 0;
            ChunkColorEvents.onDimensionChanged();
        }

        boolean writeEnabled = SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WORLD_MAP, level.dimension().location())
                .atLeast(SolarisFeatureState.ENABLED);
        boolean surfaceMap = writeEnabled && !DimensionModes.isCaveDimension(level);

        if (surfaceMap) MapTileCache.checkNightFactor(level);

        if (surfaceMap && ++taintedRetryTickCounter >= TAINTED_RETRY_INTERVAL_TICKS) {
            taintedRetryTickCounter = 0;
            retryTaintedChunks(level);
        }

        if (surfaceMap) ChunkColorEvents.drainNeighborResamples(level, NEIGHBOR_RESAMPLES_PER_TICK);

        if (surfaceMap && !PENDING_SWEEP.isEmpty()) {
            drainSweepBudget(level);
        } else {
            int intervalTicks = SolarisConfig.LIVE_REFRESH_INTERVAL_SECONDS.get() * 20;
            if (++tickCounter >= intervalTicks) {
                tickCounter = 0;
                if (surfaceMap) enqueueSweep(level, player);
            }
        }

        if (++saveTickCounter >= SAVE_INTERVAL_TICKS) {
            saveTickCounter = 0;
            PersistentChunkStore.saveDirtyAsync();
            PersistentCaveStore.saveDirtyAsync();
        }
    }

    private static void enqueueSweep(Level level, LocalPlayer player) {
        int radius = Math.min(SolarisConfig.LIVE_REFRESH_RADIUS_CHUNKS.get(),
                SolarisConfig.WORLD_MAP_WRITE_RANGE_CHUNKS.get());
        int centerX = player.chunkPosition().x;
        int centerZ = player.chunkPosition().z;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                PENDING_SWEEP.add(ChunkKey.of(level, new ChunkPos(chunkX, chunkZ)));
            }
        }

        int writeRange = SolarisConfig.WORLD_MAP_WRITE_RANGE_CHUNKS.get();
        for (int dz = -writeRange; dz <= writeRange; dz++) {
            for (int dx = -writeRange; dx <= writeRange; dx++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= radius) continue;
                if (!level.hasChunk(chunkX, chunkZ)) continue;

                ChunkKey key = ChunkKey.of(level, new ChunkPos(chunkX, chunkZ));
                if (ChunkColorCache.contains(key)) continue;
                PENDING_SWEEP.add(key);
            }
        }

        sweepRefreshedAny = false;
    }

    private static void drainSweepBudget(Level level) {
        long sweepStart = SolarisProfiler.start();
        for (int i = 0; i < CHUNKS_PER_TICK_BUDGET && !PENDING_SWEEP.isEmpty(); i++) {
            ChunkKey key = PENDING_SWEEP.poll();
            if (!key.dimension().equals(level.dimension().location())) continue;

            if (!ChunkColorEvents.neighborsLoaded(level, key) &&
                    (ChunkColorCache.contains(key) || PersistentChunkStore.get(key) != null)) {
                continue;
            }
            Boolean changed = SolarisProfiler.time("chunkSample",
                    () -> ChunkColorEvents.resample(level, key, key.toChunkPos()));
            if (Boolean.TRUE.equals(changed)) sweepRefreshedAny = true;
        }
        SolarisProfiler.end("liveRefreshSweep:batch", sweepStart);

        if (PENDING_SWEEP.isEmpty() && sweepRefreshedAny) {
            SolarisTexture.invalidateAll();
            sweepRefreshedAny = false;
        }
    }

    private static void retryTaintedChunks(Level level) {
        if (ChunkColorEvents.FALLBACK_TAINTED.isEmpty()) return;

        boolean refreshedAny = false;
        taintedPass++;
        List<ChunkKey> tainted = List.copyOf(ChunkColorEvents.FALLBACK_TAINTED);
        int start = taintedRetryCursor % tainted.size();
        taintedRetryCursor = start;
        int retried = 0;
        for (int n = 0; n < tainted.size() && retried < TAINTED_RETRIES_PER_PASS; n++) {
            ChunkKey key = tainted.get((start + n) % tainted.size());
            taintedRetryCursor = start + n + 1;
            if (!key.dimension().equals(level.dimension().location())) continue;
            if (!level.hasChunk(key.x(), key.z())) continue;

            // A chunk that keeps coming back tainted usually has air columns for good (sky islands, void),
            // so the more it has failed the less often it is worth another full sample.
            int attempts = ChunkColorEvents.FALLBACK_RETRY_COUNT.getOrDefault(key, 0);
            int interval = attempts < 3 ? 1 : attempts < 6 ? 4 : 16;
            if (taintedPass % interval != 0) continue;
            retried++;
            ChunkColorSampler.ColorSample colorSample = ChunkColorSampler.sample(level, key.toChunkPos());
            ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level, key.toChunkPos());
            ChunkColorEvents.trimUnloadedEdges(level, key, colorSample, heightSample);
            if (colorSample.hadFallback) {
                int failures = ChunkColorEvents.FALLBACK_RETRY_COUNT.merge(key, 1, Integer::sum);
                if (failures >= MAX_FALLBACK_RETRIES) {
                    ChunkColorEvents.FALLBACK_TAINTED.remove(key);
                    ChunkColorEvents.FALLBACK_RETRY_COUNT.remove(key);
                }
                continue;
            }
            ChunkColorEvents.FALLBACK_RETRY_COUNT.remove(key);

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
            ChunkColorEvents.FALLBACK_TAINTED.remove(key);
            MapTileCache.markDirty(key);
            refreshedAny = true;
        }
        if (refreshedAny) SolarisTexture.invalidateAll();
    }
}
