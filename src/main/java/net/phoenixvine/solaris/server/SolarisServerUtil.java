package net.phoenixvine.solaris.server;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.network.packets.S2CServerCapabilitiesPacket;

public final class SolarisServerUtil {

    private SolarisServerUtil() {}

    public static boolean hasSolaris(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(S2CServerCapabilitiesPacket.TYPE.id());
    }
}
