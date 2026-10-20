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

        // Switching dimensions doesn't fire LoggingIn, so anything queued for the previous dimension
        // would otherwise be sampled against the new level and written under the old dimension's keys.
        net.minecraft.resources.ResourceLocation currentDimension = level.dimension().location();
        if (!currentDimension.equals(lastDimension)) {
            lastDimension = currentDimension;
            PENDING_SWEEP.clear();
            sweepRefreshedAny = false;
            tickCounter = 0;
            taintedRetryTickCounter = 0;
            ChunkColorEvents.onDimensionChanged();
        }

        boolean writeEnabled = SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WORLD_MAP, level.dimension().location())
                .atLeast(SolarisFeatureState.ENABLED);
        boolean surfaceMap = writeEnabled && !level.dimensionType().hasCeiling();

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

            // Edge-of-view chunks sample water colour/biome blending against neighbours that aren't loaded
            // (the client reports those as plains), which gives off-colour water. If we already have data
            // for this chunk, keep it until the neighbours are actually there.
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
        for (ChunkKey key : List.copyOf(ChunkColorEvents.FALLBACK_TAINTED)) {
            if (!key.dimension().equals(level.dimension().location())) continue;
            if (!level.hasChunk(key.x(), key.z())) continue;
            ChunkColorSampler.ColorSample colorSample = ChunkColorSampler.sample(level, key.toChunkPos());
            ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level, key.toChunkPos());
            ChunkColorEvents.trimUnloadedEdges(level, key, colorSample, heightSample);
            if (colorSample.hadFallback) {
                int attempts = ChunkColorEvents.FALLBACK_RETRY_COUNT.merge(key, 1, Integer::sum);
                if (attempts >= MAX_FALLBACK_RETRIES) {
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
