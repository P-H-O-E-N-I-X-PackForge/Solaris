package net.phoenixvine.solaris.server;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.packets.S2CServerCapabilitiesPacket;

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
public final class ServerCapabilities {

    private static int flags = 0;
    private static int uploadChunksPerSecond = 0;

    private ServerCapabilities() {}

    public static boolean serverDeathMarkers() {
        return (flags & S2CServerCapabilitiesPacket.DEATH_MARKERS) != 0;
    }

    public static boolean sharedExploration() {
        return (flags & S2CServerCapabilitiesPacket.SHARED_EXPLORATION) != 0;
    }

    public static boolean sharedVeins() {
        return (flags & S2CServerCapabilitiesPacket.SHARED_VEINS) != 0;
    }

    public static boolean playerPositions() {
        return (flags & S2CServerCapabilitiesPacket.PLAYER_POSITIONS) != 0;
    }

    public static int uploadChunksPerSecond() {
        return uploadChunksPerSecond;
    }

    public static void setFlags(int value, int uploadRate) {
        flags = value;
        uploadChunksPerSecond = uploadRate;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        flags = 0;
        uploadChunksPerSecond = 0;
    }
}
