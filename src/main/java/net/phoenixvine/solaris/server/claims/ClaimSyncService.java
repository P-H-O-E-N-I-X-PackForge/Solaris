package net.phoenixvine.solaris.server.claims;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisClaimsAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.api.claims.ClaimProvider;
import net.phoenixvine.solaris.api.claims.ClaimSink;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.network.S2CClaimsPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.phoenixvine.solaris.server.SolarisServerAPI;
import net.phoenixvine.solaris.server.SolarisServerUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimSyncService {

    private static final int MAX_CLAIM_CHUNKS = 20000;
    
    private static final int SNAP_SHIFT = 3;

    private static int counter = 0;
    private static final Map<UUID, Integer> LAST_HASH = new HashMap<>();

    private ClaimSyncService() {}

    public static void forget(UUID player) {
        LAST_HASH.remove(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!SolarisServerConfig.SPEC.isLoaded() || !SolarisServerConfig.CLAIMS_ENABLED.get()) return;
        if (SolarisClaimsAPI.providers().isEmpty()) return;
        if (++counter < SolarisServerConfig.CLAIMS_UPDATE_TICKS.get()) return;
        counter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }

    private static final class Accumulator {
        final String name;
        final int argb;
        final LongArrayList chunks = new LongArrayList();

        Accumulator(String name, int argb) {
            this.name = name;
            this.argb = argb;
        }
    }

    private static void sync(ServerPlayer player) {
        if (!SolarisServerUtil.hasSolaris(player)) return;

        ResourceLocation dimension = player.level().dimension().location();
        boolean allowed = SolarisServerAPI.isFeatureEnabled(player, SolarisAPI.FEATURE_CLAIMS) &&
                SolarisServerAPI.getFeatureState(player, SolarisAPI.FEATURE_CLAIMS, dimension)
                        .atLeast(SolarisFeatureState.VISIBLE);

        int radius = SolarisServerConfig.CLAIMS_RADIUS_CHUNKS.get();
        ChunkPos here = player.chunkPosition();
        int centerX = (here.x >> SNAP_SHIFT) << SNAP_SHIFT;
        int centerZ = (here.z >> SNAP_SHIFT) << SNAP_SHIFT;
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

        Map<String, Accumulator> owners = new LinkedHashMap<>();
        if (allowed) {
            int[] total = { 0 };
            ClaimSink sink = (cx, cz, ownerName, argb) -> {
                if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) return;
                if (total[0] >= MAX_CLAIM_CHUNKS) return;
                total[0]++;
                String name = ownerName == null ? "" : ownerName.length() > 32 ? ownerName.substring(0, 32) :
                        ownerName;
                owners.computeIfAbsent(name + "#" + argb, k -> new Accumulator(name, argb)).chunks
                        .add(ChunkPos.asLong(cx, cz));
            };
            for (ClaimProvider provider : SolarisClaimsAPI.providers()) {
                try {
                    provider.collect(player, dimension, minX, minZ, maxX, maxZ, sink);
                } catch (Exception e) {
                    PhoenixSolaris.LOGGER.warn("A Solaris claim provider failed", e);
                }
            }
        }

        List<S2CClaimsPacket.Owner> out = new ArrayList<>();
        int hash = 0;
        for (Accumulator acc : owners.values()) {
            if (out.size() >= S2CClaimsPacket.MAX_OWNERS) break;
            long[] chunks = acc.chunks.toLongArray();
            Arrays.sort(chunks);
            out.add(new S2CClaimsPacket.Owner(acc.name, acc.argb, chunks));
            hash = 31 * hash + acc.name.hashCode();
            hash = 31 * hash + acc.argb;
            hash = 31 * hash + Arrays.hashCode(chunks);
        }
        if (!out.isEmpty()) {
            hash = 31 * hash + dimension.hashCode();
            hash = 31 * hash + centerX;
            hash = 31 * hash + centerZ;
            if (hash == 0) hash = 1;
        }

        int last = LAST_HASH.getOrDefault(player.getUUID(), 0);
        if (hash == last) return;
        LAST_HASH.put(player.getUUID(), hash);

        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CClaimsPacket(dimension.toString(), centerX, centerZ, radius, out));
    }
}
