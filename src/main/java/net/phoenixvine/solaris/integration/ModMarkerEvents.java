package net.phoenixvine.solaris.integration;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisMarkerSyncEvent;
import net.phoenixvine.solaris.integration.kineticwaypoints.KineticWaypointsIntegration;
import net.phoenixvine.solaris.integration.waystones.WaystonesIntegration;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModMarkerEvents {

    private static final List<ModMarkerSource> SOURCES = List.of(KineticWaypointsIntegration.INSTANCE,
            WaystonesIntegration.INSTANCE);
    private static final int SYNC_INTERVAL_TICKS = 100;

    private ModMarkerEvents() {}

    @SubscribeEvent
    public static void onMarkerSync(SolarisMarkerSyncEvent event) {
        for (ModMarkerSource source : SOURCES) {
            source.reset(event.getPlayer().getUUID());
            source.sync(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            for (ModMarkerSource source : SOURCES) source.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        for (ModMarkerSource source : SOURCES) source.reset(event.getEntity().getUUID());
    }
}
