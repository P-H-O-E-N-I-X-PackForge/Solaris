package net.phoenixvine.solaris.common;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.PersistentChunkStore;
import net.phoenixvine.solaris.client.color.SharedChunkCodec;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.network.C2SSharedChunksPacket;
import net.phoenixvine.solaris.network.C2SSharedSyncRequestPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;
import net.phoenixvine.solaris.server.ServerCapabilities;
import net.phoenixvine.solaris.sharing.SharedChunkBlob;
import net.phoenixvine.solaris.sharing.SharedChunkFormat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SharedExplorationClient {

    private static final int WINDOW = SharedChunkFormat.WINDOW;
    private static final int MAX_QUEUE = 20000;
    private static final int UPLOAD_MAX_BLOBS = 12;
    private static final int UPLOAD_MAX_BYTES = 24000;
    
    private static final int TICK_INTERVAL = 5;
    private static final int REQUEST_MOVE_CHUNKS = 24;
    private static final long REQUEST_SPACING_MS = 2500L;
    private static final long WINDOW_REFRESH_MS = 60_000L;
    private static final int MAX_QUEUED_WINDOWS = 16;
    private static final int MAX_VISIBLE_WINDOWS = 12;
    private static final long INVALIDATE_INTERVAL_MS = 1000L;
    private static final long ACTIVITY_SHOWN_MS = 6000L;

    private record Window(ResourceLocation dimension, int minX, int minZ) {}

    private static final LinkedHashSet<ChunkKey> UPLOAD_QUEUE = new LinkedHashSet<>();
    private static final LinkedHashSet<ChunkKey> WANTED = new LinkedHashSet<>();
    private static final ArrayDeque<Window> WINDOW_QUEUE = new ArrayDeque<>();
    private static final Map<Window, Long> WINDOW_REQUESTED_AT = new HashMap<>();

    private static int tickCounter = 0;
    private static long lastRequestMillis = 0L;
    private static ResourceLocation lastPlayerDimension = null;
    private static int lastPlayerChunkX = 0;
    private static int lastPlayerChunkZ = 0;
    private static long lastPlayerRequestMillis = 0L;
    private static int lastAroundX = Integer.MIN_VALUE;
    private static int lastAroundZ = Integer.MIN_VALUE;

    private static boolean pendingTextureRefresh = false;
    private static long lastTextureRefreshMillis = 0L;
    private static long lastActivityMillis = 0L;

    private static int uploadedChunks = 0;
    private static int mergedChunks = 0;
    private static int requestedUploads = 0;
    private static int logCounter = 0;
    private static int lastLoggedUploaded = 0;
    private static int lastLoggedMerged = 0;

    private SharedExplorationClient() {}

    private static boolean enabledByUserAndServer() {
        return ServerCapabilities.sharedExploration() && SolarisConfig.SHARE_EXPLORATION.get();
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !enabledByUserAndServer()) return false;
        return SolarisAPI.getFeatureState(SolarisAPI.FEATURE_SHARED_EXPLORATION, mc.level.dimension().location())
                .atLeast(SolarisFeatureState.ENABLED);
    }

    public static String activityText() {
        if (System.currentTimeMillis() - lastActivityMillis > ACTIVITY_SHOWN_MS) return null;
        return "Guild map: sent " + uploadedChunks + ", received " + mergedChunks;
    }

    public static void onLocalChunk(ChunkKey key) {
        if (!enabledByUserAndServer()) return;
        synchronized (UPLOAD_QUEUE) {
            if (UPLOAD_QUEUE.size() < MAX_QUEUE) UPLOAD_QUEUE.add(key);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        synchronized (UPLOAD_QUEUE) {
            UPLOAD_QUEUE.clear();
        }
        WANTED.clear();
        WINDOW_QUEUE.clear();
        WINDOW_REQUESTED_AT.clear();
        lastPlayerDimension = null;
        lastAroundX = Integer.MIN_VALUE;
        lastAroundZ = Integer.MIN_VALUE;
        lastRequestMillis = 0L;
        pendingTextureRefresh = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = System.currentTimeMillis();
        if (pendingTextureRefresh && now - lastTextureRefreshMillis >= INVALIDATE_INTERVAL_MS) {
            pendingTextureRefresh = false;
            lastTextureRefreshMillis = now;
            SolarisTexture.markChunkDataChanged();
        }

        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        if (++logCounter >= 120 && (uploadedChunks != lastLoggedUploaded || mergedChunks != lastLoggedMerged)) {
            logCounter = 0;
            lastLoggedUploaded = uploadedChunks;
            lastLoggedMerged = mergedChunks;
            PhoenixSolaris.LOGGER.info("[Solaris] Shared exploration: uploaded {} chunk(s), merged {} from the " +
                    "guild, {} requested by the server", uploadedChunks, mergedChunks, requestedUploads);
        }

        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation dimension = mc.level.dimension().location();

        uploadStep(dimension);

        ChunkPos here = mc.player.chunkPosition();
        boolean moved = lastPlayerDimension == null || !lastPlayerDimension.equals(dimension) ||
                Math.abs(here.x - lastPlayerChunkX) >= REQUEST_MOVE_CHUNKS ||
                Math.abs(here.z - lastPlayerChunkZ) >= REQUEST_MOVE_CHUNKS;
        if (moved || now - lastPlayerRequestMillis >= WINDOW_REFRESH_MS) {
            lastPlayerDimension = dimension;
            lastPlayerChunkX = here.x;
            lastPlayerChunkZ = here.z;
            lastPlayerRequestMillis = now;
            enqueueWindow(new Window(dimension, here.x - WINDOW / 2, here.z - WINDOW / 2), true, now);
        }

        pumpRequests(dimension, now);
    }

    public static void requestVisible(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        if (!active()) return;
        ResourceLocation dimension = Minecraft.getInstance().level.dimension().location();

        int firstX = Math.floorDiv(minChunkX, WINDOW) * WINDOW;
        int firstZ = Math.floorDiv(minChunkZ, WINDOW) * WINDOW;
        double centerX = (minChunkX + maxChunkX) / 2.0;
        double centerZ = (minChunkZ + maxChunkZ) / 2.0;

        List<Window> windows = new ArrayList<>();
        for (int wz = firstZ; wz <= maxChunkZ && windows.size() < 64; wz += WINDOW) {
            for (int wx = firstX; wx <= maxChunkX && windows.size() < 64; wx += WINDOW) {
                windows.add(new Window(dimension, wx, wz));
            }
        }
        
        windows.sort((a, b) -> Double.compare(distanceSq(a, centerX, centerZ), distanceSq(b, centerX, centerZ)));

        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(windows.size(), MAX_VISIBLE_WINDOWS); i++) {
            enqueueWindow(windows.get(i), false, now);
        }
    }

    public static void requestAround(int chunkX, int chunkZ) {
        if (!active()) return;
        if (lastAroundX != Integer.MIN_VALUE && Math.abs(chunkX - lastAroundX) < REQUEST_MOVE_CHUNKS &&
                Math.abs(chunkZ - lastAroundZ) < REQUEST_MOVE_CHUNKS) {
            return;
        }
        lastAroundX = chunkX;
        lastAroundZ = chunkZ;
        ResourceLocation dimension = Minecraft.getInstance().level.dimension().location();
        enqueueWindow(new Window(dimension, chunkX - WINDOW / 2, chunkZ - WINDOW / 2), false,
                System.currentTimeMillis());
    }

    private static double distanceSq(Window w, double cx, double cz) {
        double dx = w.minX() + WINDOW / 2.0 - cx;
        double dz = w.minZ() + WINDOW / 2.0 - cz;
        return dx * dx + dz * dz;
    }

    private static void enqueueWindow(Window window, boolean urgent, long now) {
        if (!urgent) {
            Long at = WINDOW_REQUESTED_AT.get(window);
            if (at != null && now - at < WINDOW_REFRESH_MS) return;
        }
        if (WINDOW_QUEUE.contains(window)) return;
        if (WINDOW_QUEUE.size() >= MAX_QUEUED_WINDOWS) {
            if (!urgent) return;
            WINDOW_QUEUE.pollLast();
        }
        if (urgent) WINDOW_QUEUE.addFirst(window);
        else WINDOW_QUEUE.addLast(window);
    }

    private static void pumpRequests(ResourceLocation currentDimension, long now) {
        if (WINDOW_QUEUE.isEmpty() || now - lastRequestMillis < REQUEST_SPACING_MS) return;

        Window window = WINDOW_QUEUE.peekFirst();
        if (!window.dimension().equals(currentDimension)) {
            WINDOW_QUEUE.pollFirst();
            return;
        }
        if (!sendWindow(window)) return;

        WINDOW_QUEUE.pollFirst();
        lastRequestMillis = now;
        if (WINDOW_REQUESTED_AT.size() > 2000) WINDOW_REQUESTED_AT.clear();
        WINDOW_REQUESTED_AT.put(window, now);
    }

    private static boolean sendWindow(Window window) {
        String dimensionName = window.dimension().toString();
        
        ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> chunks = PersistentChunkStore.chunksFor(dimensionName);
        if (chunks == null || !PersistentChunkStore.isLoaded(dimensionName)) return false;

        long[] have = new long[WINDOW];
        for (int row = 0; row < WINDOW; row++) {
            for (int col = 0; col < WINDOW; col++) {
                if (chunks.containsKey(new ChunkKey(window.dimension(), window.minX() + col, window.minZ() + row))) {
                    have[row] |= 1L << col;
                }
            }
        }
        SolarisNetwork.CHANNEL.sendToServer(
                new C2SSharedSyncRequestPacket(dimensionName, window.minX(), window.minZ(), have));
        return true;
    }

    private static void uploadStep(ResourceLocation dimension) {
        List<SharedChunkBlob> blobs = new ArrayList<>();
        int bytes = 0;

        while (!WANTED.isEmpty() && blobs.size() < UPLOAD_MAX_BLOBS && bytes < UPLOAD_MAX_BYTES) {
            ChunkKey key = WANTED.iterator().next();
            WANTED.remove(key);
            synchronized (UPLOAD_QUEUE) {
                UPLOAD_QUEUE.remove(key);
            }
            bytes += addBlob(blobs, key, dimension);
        }
        synchronized (UPLOAD_QUEUE) {
            Iterator<ChunkKey> it = UPLOAD_QUEUE.iterator();
            while (it.hasNext() && blobs.size() < UPLOAD_MAX_BLOBS && bytes < UPLOAD_MAX_BYTES) {
                ChunkKey key = it.next();
                it.remove();
                bytes += addBlob(blobs, key, dimension);
            }
        }

        if (!blobs.isEmpty()) {
            uploadedChunks += blobs.size();
            lastActivityMillis = System.currentTimeMillis();
            SolarisNetwork.CHANNEL.sendToServer(new C2SSharedChunksPacket(dimension.toString(), blobs));
        }
    }

    private static int addBlob(List<SharedChunkBlob> blobs, ChunkKey key, ResourceLocation dimension) {
        if (!key.dimension().equals(dimension)) return 0;
        PersistentChunkStore.Entry entry = PersistentChunkStore.get(key);
        if (entry == null) return 0;
        byte[] data = SharedChunkCodec.encode(entry);
        blobs.add(new SharedChunkBlob(key.x(), key.z(), data));
        return data.length;
    }

    public static void receiveChunks(String dimensionName, List<SharedChunkBlob> blobs) {
        if (!enabledByUserAndServer()) return;
        ResourceLocation dimension = ResourceLocation.tryParse(dimensionName);
        if (dimension == null) return;

        boolean merged = false;
        for (SharedChunkBlob blob : blobs) {
            PersistentChunkStore.Entry entry = SharedChunkCodec.decode(blob.data());
            if (entry == null) continue;

            ChunkKey key = new ChunkKey(dimension, blob.x(), blob.z());
            if (PersistentChunkStore.putSharedIfAbsent(key, entry)) {
                MapTileCache.markDirty(key);
                mergedChunks++;
                merged = true;
            }
        }
        if (merged) {
            pendingTextureRefresh = true;
            lastActivityMillis = System.currentTimeMillis();
        }
    }

    public static void receiveWant(String dimensionName, int minX, int minZ, long[] want) {
        if (!active() || want.length != WINDOW) return;
        ResourceLocation dimension = ResourceLocation.tryParse(dimensionName);
        if (dimension == null) return;

        for (int row = 0; row < WINDOW; row++) {
            for (int col = 0; col < WINDOW; col++) {
                if (((want[row] >>> col) & 1L) != 0L && WANTED.size() < MAX_QUEUE &&
                        WANTED.add(new ChunkKey(dimension, minX + col, minZ + row))) {
                    requestedUploads++;
                }
            }
        }
    }
}