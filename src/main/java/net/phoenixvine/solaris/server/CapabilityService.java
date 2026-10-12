package net.phoenixvine.solaris.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.network.S2CServerCapabilitiesPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;

public final class CapabilityService {

    private CapabilityService() {}

    public static void send(ServerPlayer player) {
        if (!SolarisServerUtil.hasSolaris(player)) return;

        int flags = 0;
        if (DeathMarkerService.enabled()) flags |= S2CServerCapabilitiesPacket.DEATH_MARKERS;

        if (SolarisServerConfig.SPEC.isLoaded() &&
                SolarisServerConfig.PLAYER_VISIBILITY.get() != SolarisServerConfig.PlayerVisibility.OFF) {
            flags |= S2CServerCapabilitiesPacket.PLAYER_POSITIONS;
        }

        if (SolarisServerConfig.SPEC.isLoaded() && GuildWaypointService.inGuild(player)) {
            if (SolarisServerConfig.SHARED_EXPLORATION_ENABLED.get()) {
                flags |= S2CServerCapabilitiesPacket.SHARED_EXPLORATION;
            }
            if (SolarisServerConfig.SHARED_VEINS_ENABLED.get()) flags |= S2CServerCapabilitiesPacket.SHARED_VEINS;
        }

        final int sent = flags;
        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CServerCapabilitiesPacket(sent));
    }
}
