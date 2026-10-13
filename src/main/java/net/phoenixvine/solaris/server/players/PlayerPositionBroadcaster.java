package net.phoenixvine.solaris.server.players;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.config.SolarisServerConfig.PlayerVisibility;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.packets.S2CPlayerPositionsPacket;
import net.phoenixvine.solaris.server.SolarisServerAPI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber()
public final class PlayerPositionBroadcaster {

    private static int counter = 0;

    private static final Set<UUID> LAST_SENT_NON_EMPTY = new HashSet<>();

    private PlayerPositionBroadcaster() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SolarisServerConfig.SPEC.isLoaded()) return;

        PlayerVisibility mode = SolarisServerConfig.PLAYER_VISIBILITY.get();
        if (mode == PlayerVisibility.OFF) {
            counter = 0;
            LAST_SENT_NON_EMPTY.clear();
            return;
        }
        if (++counter < SolarisServerConfig.PLAYER_UPDATE_TICKS.get()) return;
        counter = 0;

        MinecraftServer server = event.getServer();

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : players) online.add(p.getUUID());
        LAST_SENT_NON_EMPTY.retainAll(online);

        for (ServerPlayer receiver : players) {
            if (receiver.connection == null) continue;

            ResourceLocation dimension = receiver.level().dimension().location();
            if (SolarisServerConfig.PLAYER_BLOCKED_DIMENSIONS.get().contains(dimension.toString())) continue;
            if (!SolarisServerAPI.isFeatureEnabled(receiver, SolarisAPI.FEATURE_SHOW_PLAYERS)) continue;
            if (!SolarisServerAPI.getFeatureState(receiver, SolarisAPI.FEATURE_SHOW_PLAYERS, dimension)
                    .atLeast(SolarisFeatureState.VISIBLE)) {
                continue;
            }

            List<S2CPlayerPositionsPacket.Entry> entries = collectVisible(receiver, mode, players, dimension);

            if (entries.isEmpty() && !LAST_SENT_NON_EMPTY.contains(receiver.getUUID())) continue;
            if (entries.isEmpty()) LAST_SENT_NON_EMPTY.remove(receiver.getUUID());
            else LAST_SENT_NON_EMPTY.add(receiver.getUUID());

            PacketDistributor.sendToPlayer(receiver, new S2CPlayerPositionsPacket(dimension, entries));
        }
    }

    private static List<S2CPlayerPositionsPacket.Entry> collectVisible(ServerPlayer receiver, PlayerVisibility mode,
                                                                       Collection<ServerPlayer> everyone,
                                                                       ResourceLocation dimension) {
        Collection<ServerPlayer> candidates = mode == PlayerVisibility.ALL ? everyone :
                GuildsIntegration.getOnlineGuildmates(receiver);

        boolean hideSneaking = SolarisServerConfig.HIDE_SNEAKING.get();
        boolean hideInvisible = SolarisServerConfig.HIDE_INVISIBLE.get();
        int range = SolarisServerConfig.PLAYER_MAX_RANGE.get();
        double rangeSq = (double) range * range;

        List<S2CPlayerPositionsPacket.Entry> out = new ArrayList<>();
        for (ServerPlayer target : candidates) {
            if (target == receiver || target.getUUID().equals(receiver.getUUID())) continue;
            if (!target.level().dimension().location().equals(dimension)) continue;
            if (target.isSpectator()) continue;
            if (SolarisServerAPI.isHiddenFromMap(target)) continue;
            if (hideInvisible && target.isInvisible()) continue;
            if (hideSneaking && target.isShiftKeyDown()) continue;
            if (range > 0 && target.distanceToSqr(receiver) > rangeSq) continue;

            out.add(new S2CPlayerPositionsPacket.Entry(target.getUUID(), target.getGameProfile().getName(),
                    target.getBlockX(), target.getBlockY(), target.getBlockZ(), target.getYRot()));
            if (out.size() >= S2CPlayerPositionsPacket.MAX_ENTRIES) break;
        }
        return out;
    }
}
