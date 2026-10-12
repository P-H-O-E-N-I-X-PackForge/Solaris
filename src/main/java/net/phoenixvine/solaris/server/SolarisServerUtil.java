package net.phoenixvine.solaris.server;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.network.SolarisNetwork;

public final class SolarisServerUtil {

    private SolarisServerUtil() {}

    public static boolean hasSolaris(ServerPlayer player) {
        return player.connection != null && SolarisNetwork.CHANNEL.isRemotePresent(player.connection.connection);
    }
}
