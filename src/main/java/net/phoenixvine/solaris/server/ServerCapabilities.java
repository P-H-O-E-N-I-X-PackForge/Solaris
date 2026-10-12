package net.phoenixvine.solaris.server;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.S2CServerCapabilitiesPacket;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ServerCapabilities {

    private static int flags = 0;

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

    public static void setFlags(int value) {
        flags = value;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        flags = 0;
    }
}
