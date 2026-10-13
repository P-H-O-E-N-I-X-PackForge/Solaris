package net.phoenixvine.solaris.server.exploration;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.common.SharedChunkBlob;
import net.phoenixvine.solaris.common.SharedChunkFormat;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.S2CSharedChunksPacket;
import net.phoenixvine.solaris.network.S2CSharedUploadWantPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;
import net.phoenixvine.solaris.server.GuildMapStore;
import net.phoenixvine.solaris.server.SolarisServerAPI;
import net.phoenixvine.solaris.server.SolarisServerUtil;
import net.phoenixvine.solaris.server.guild.GuildWaypointService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SharedExplorationService {

    private static final int MAX_COORD = 1_875_000;
    private static final int DOWNLOAD_TICK_INTERVAL = 4;
    private static final int PACKET_MAX_BLOBS = 16;
    private static final int PACKET_MAX_BYTES = 40_000;
    private static final int SYNC_COOLDOWN_TICKS = 40;

    private static final RateLimiter UPLOAD_LIMITER = new RateLimiter();
    private static final Map<UUID, ArrayDeque<Long>> DOWNLOAD_QUEUES = new HashMap<>();
    private static final Map<UUID, java.util.Set<Long>> DOWNLOAD_QUEUED = new HashMap<>();
    private static final int MAX_DOWNLOAD_QUEUE = 40_000;
    private static final Map<UUID, String> DOWNLOAD_DIMENSIONS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SYNC_TICK = new HashMap<>();

    private static long uploadsAccepted = 0;
    private static long uploadsRejected = 0;
    private static long uploadsDroppedLoading = 0;
    private static long uploadsThrottled = 0;
    private static long chunksRelayed = 0;

    private static int downloadCounter = 0;
    private static int saveCounter = 0;

    private SharedExplorationService() {}

    public static void forget(UUID player) {
        UPLOAD_LIMITER.forget(player);
        DOWNLOAD_QUEUES.remove(player);
        DOWNLOAD_QUEUED.remove(player);
        DOWNLOAD_DIMENSIONS.remove(player);
        LAST_SYNC_TICK.remove(player);
    }

    private static boolean allowed(ServerPlayer player, ResourceLocation dimension) {
        if (!SolarisServerConfig.SPEC.isLoaded() || !SolarisServerConfig.SHARED_EXPLORATION_ENABLED.get()) {
            return false;
        }
        if (!SolarisServerUtil.hasSolaris(player) || !GuildWaypointService.inGuild(player)) return false;
        if (!SolarisServerAPI.isFeatureEnabled(player, SolarisAPI.FEATURE_SHARED_EXPLORATION)) return false;
        return SolarisServerAPI.getFeatureState(player, SolarisAPI.FEATURE_SHARED_EXPLORATION, dimension)
                .atLeast(SolarisFeatureState.ENABLED);
    }

    private static ResourceLocation validDimension(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null || server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) == null) return null;
        return id;
    }

    public static void handleUpload(ServerPlayer player, String dimension, List<SharedChunkBlob> blobs) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ResourceLocation dim = validDimension(server, dimension);
        if (dim == null || !allowed(player, dim)) return;

        UUID guild = GuildsIntegration.ownerTokenFor(player);
        GuildMapStore.DimStore store = GuildMapStore.get(server, guild, dim.toString());
        if (store == null) {
            uploadsDroppedLoading += blobs.size();
            return;
        }

        int permitted = UPLOAD_LIMITER.take(player.getUUID(), blobs.size(), server.getTickCount(),
                SolarisServerConfig.SHARED_EXPLORATION_UPLOAD_PER_SECOND.get());
        int cap = SolarisServerConfig.SHARED_EXPLORATION_MAX_CHUNKS.get();
        if (permitted < blobs.size()) uploadsThrottled += blobs.size() - permitted;

        List<SharedChunkBlob> added = new ArrayList<>();
        for (int i = 0; i < permitted; i++) {
            SharedChunkBlob blob = blobs.get(i);
            if (Math.abs(blob.x()) > MAX_COORD || Math.abs(blob.z()) > MAX_COORD) continue;
            if (SharedChunkFormat.inflateChecked(blob.data()) == null) {
                uploadsRejected++;
                continue;
            }
            if (GuildMapStore.putIfAbsent(store, blob.x(), blob.z(), blob.data(), cap)) added.add(blob);
        }
        uploadsAccepted += added.size();
        if (!added.isEmpty()) relay(player, dim, added);
    }

    private static void relay(ServerPlayer uploader, ResourceLocation dimension, List<SharedChunkBlob> added) {
        int radius = SolarisServerConfig.SHARED_EXPLORATION_RELAY_RADIUS.get();
        for (ServerPlayer mate : GuildsIntegration.getOnlineGuildmates(uploader)) {
            if (mate == uploader || !mate.level().dimension().location().equals(dimension)) continue;
            if (!allowed(mate, dimension)) continue;

            ChunkPos at = mate.chunkPosition();
            List<SharedChunkBlob> near = new ArrayList<>();
            for (SharedChunkBlob blob : added) {
                if (Math.abs(blob.x() - at.x) <= radius && Math.abs(blob.z() - at.z) <= radius) near.add(blob);
            }
            chunksRelayed += near.size();
            sendBlobs(mate, dimension.toString(), near);
        }
    }

    public static List<String> status(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        ResourceLocation dimension = player.level().dimension().location();
        boolean configured = SolarisServerConfig.SPEC.isLoaded() &&
                SolarisServerConfig.SHARED_EXPLORATION_ENABLED.get();
        lines.add("sharedExploration.enabled = " + configured);
        lines.add("client has Solaris = " + SolarisServerUtil.hasSolaris(player));
        boolean inGuild = GuildWaypointService.inGuild(player);
        lines.add("in a guild = " + inGuild + " (owner token " + GuildsIntegration.ownerTokenFor(player) + ")");
        lines.add("shared_exploration feature: toggle=" +
                SolarisServerAPI.isFeatureEnabled(player, SolarisAPI.FEATURE_SHARED_EXPLORATION) + ", state in " +
                dimension + " = " + SolarisServerAPI.getFeatureState(player, SolarisAPI.FEATURE_SHARED_EXPLORATION,
                        dimension));
        lines.add("allowed right now = " + allowed(player, dimension));

        MinecraftServer server = player.getServer();
        if (server != null && inGuild) {
            GuildMapStore.DimStore store = GuildMapStore.get(server, GuildsIntegration.ownerTokenFor(player),
                    dimension.toString());
            lines.add("guild store for " + dimension + ": " +
                    (store == null ? "still loading" : store.chunks.size() + " chunk(s)"));
        }
        ArrayDeque<Long> queue = DOWNLOAD_QUEUES.get(player.getUUID());
        lines.add("queued downloads for this player = " + (queue == null ? 0 : queue.size()));
        lines.add("since start: uploads accepted " + uploadsAccepted + ", rejected (malformed) " + uploadsRejected +
                ", dropped while loading " + uploadsDroppedLoading + ", turned away by the rate limit " +
                uploadsThrottled +
                ", chunks relayed live " + chunksRelayed);
        return lines;
    }

    private static void sendBlobs(ServerPlayer player, String dimension, List<SharedChunkBlob> blobs) {
        List<SharedChunkBlob> batch = new ArrayList<>();
        int bytes = 0;
        for (SharedChunkBlob blob : blobs) {
            if (!batch.isEmpty() &&
                    (batch.size() >= PACKET_MAX_BLOBS || bytes + blob.data().length > PACKET_MAX_BYTES)) {
                SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CSharedChunksPacket(dimension, batch));
                batch = new ArrayList<>();
                bytes = 0;
            }
            batch.add(blob);
            bytes += blob.data().length;
        }
        if (!batch.isEmpty()) {
            final List<SharedChunkBlob> last = batch;
            SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CSharedChunksPacket(dimension, last));
        }
    }

    public static void handleSyncRequest(ServerPlayer player, String dimension, int minX, int minZ, long[] have) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ResourceLocation dim = validDimension(server, dimension);
        if (dim == null || !allowed(player, dim)) return;
        if (Math.abs(minX) > MAX_COORD || Math.abs(minZ) > MAX_COORD) return;

        int now = server.getTickCount();
        Integer last = LAST_SYNC_TICK.get(player.getUUID());
        if (last != null && now - last < SYNC_COOLDOWN_TICKS) return;

        UUID guild = net.phoenixvine.solaris.integration.guilds.GuildsIntegration.ownerTokenFor(player);
        GuildMapStore.DimStore store = GuildMapStore.get(server, guild, dim.toString());
        if (store == null) return;
        LAST_SYNC_TICK.put(player.getUUID(), now);

        int window = SharedChunkFormat.WINDOW;
        long[] want = new long[window];
        boolean anyWant = false;
        List<Long> toSend = new ArrayList<>();

        for (int row = 0; row < window; row++) {
            for (int col = 0; col < window; col++) {
                long key = ChunkPos.asLong(minX + col, minZ + row);
                boolean clientHas = ((have[row] >>> col) & 1L) != 0L;
                boolean serverHas = store.chunks.containsKey(key);
                if (serverHas && !clientHas) {
                    toSend.add(key);
                } else if (clientHas && !serverHas) {
                    want[row] |= 1L << col;
                    anyWant = true;
                }
            }
        }

        if (anyWant) {
            final long[] bits = want;
            SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CSharedUploadWantPacket(dim.toString(), minX, minZ, bits));
        }

        double centerX = minX + window / 2.0;
        double centerZ = minZ + window / 2.0;
        toSend.sort((a, b) -> Double.compare(distanceSq(a, centerX, centerZ), distanceSq(b, centerX, centerZ)));

        if (toSend.isEmpty()) return;

        String previousDimension = DOWNLOAD_DIMENSIONS.get(player.getUUID());
        if (previousDimension != null && !previousDimension.equals(dim.toString())) {
            DOWNLOAD_QUEUES.remove(player.getUUID());
            DOWNLOAD_QUEUED.remove(player.getUUID());
        }
        ArrayDeque<Long> queue = DOWNLOAD_QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        java.util.Set<Long> queued = DOWNLOAD_QUEUED.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>());
        DOWNLOAD_DIMENSIONS.put(player.getUUID(), dim.toString());

        for (int i = toSend.size() - 1; i >= 0 && queue.size() < MAX_DOWNLOAD_QUEUE; i--) {
            long key = toSend.get(i);
            if (queued.add(key)) queue.addFirst(key);
        }
    }

    private static double distanceSq(long chunk, double cx, double cz) {
        double dx = ChunkPos.getX(chunk) - cx;
        double dz = ChunkPos.getZ(chunk) - cz;
        return dx * dx + dz * dz;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !SolarisServerConfig.SPEC.isLoaded()) return;
        if (!SolarisServerConfig.SHARED_EXPLORATION_ENABLED.get()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        if (++saveCounter >= SolarisServerConfig.SHARED_EXPLORATION_SAVE_MINUTES.get() * 1200) {
            saveCounter = 0;
            GuildMapStore.saveDirty(server, false);
        }

        if (++downloadCounter < DOWNLOAD_TICK_INTERVAL) return;
        downloadCounter = 0;
        streamDownloads(server);
    }

    private static void streamDownloads(MinecraftServer server) {
        if (DOWNLOAD_QUEUES.isEmpty()) return;

        int perTick = Math.max(1,
                SolarisServerConfig.SHARED_EXPLORATION_DOWNLOAD_PER_SECOND.get() * DOWNLOAD_TICK_INTERVAL / 20);

        for (UUID id : new ArrayList<>(DOWNLOAD_QUEUES.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            ArrayDeque<Long> queue = DOWNLOAD_QUEUES.get(id);
            String dimension = DOWNLOAD_DIMENSIONS.get(id);
            if (player == null || queue == null || dimension == null ||
                    !player.level().dimension().location().toString().equals(dimension)) {
                DOWNLOAD_QUEUES.remove(id);
                DOWNLOAD_QUEUED.remove(id);
                DOWNLOAD_DIMENSIONS.remove(id);
                continue;
            }

            ResourceLocation dim = ResourceLocation.tryParse(dimension);
            if (dim == null || !allowed(player, dim)) {
                DOWNLOAD_QUEUES.remove(id);
                DOWNLOAD_QUEUED.remove(id);
                DOWNLOAD_DIMENSIONS.remove(id);
                continue;
            }
            GuildMapStore.DimStore store = GuildMapStore.get(server, GuildsIntegration.ownerTokenFor(player),
                    dimension);
            if (store == null) continue;

            List<SharedChunkBlob> out = new ArrayList<>();
            while (out.size() < perTick && !queue.isEmpty()) {
                long key = queue.poll();
                java.util.Set<Long> queuedKeys = DOWNLOAD_QUEUED.get(id);
                if (queuedKeys != null) queuedKeys.remove(key);
                GuildMapStore.Stored stored = store.chunks.get(key);
                if (stored != null) out.add(new SharedChunkBlob(ChunkPos.getX(key), ChunkPos.getZ(key), stored.data()));
            }
            sendBlobs(player, dimension, out);

            if (queue.isEmpty()) {
                DOWNLOAD_QUEUES.remove(id);
                DOWNLOAD_QUEUED.remove(id);
                DOWNLOAD_DIMENSIONS.remove(id);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GuildMapStore.saveDirty(event.getServer(), true);
        GuildMapStore.clear();
        DOWNLOAD_QUEUES.clear();
        DOWNLOAD_QUEUED.clear();
        DOWNLOAD_DIMENSIONS.clear();
        LAST_SYNC_TICK.clear();
    }
}
