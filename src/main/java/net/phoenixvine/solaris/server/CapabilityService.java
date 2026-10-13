package net.phoenixvine.solaris.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.network.packets.S2CServerCapabilitiesPacket;
import net.phoenixvine.solaris.server.guild.GuildWaypointService;
import net.phoenixvine.solaris.server.waypoint.DeathMarkerService;

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
        final int uploadRate = SolarisServerConfig.SPEC.isLoaded() ?
                SolarisServerConfig.SHARED_EXPLORATION_UPLOAD_PER_SECOND.get() : 0;

        // Modernized 1.21 packet dispatch using PacketDistributor directly
        PacketDistributor.sendToPlayer(player, new S2CServerCapabilitiesPacket(sent, uploadRate));
    }
}
