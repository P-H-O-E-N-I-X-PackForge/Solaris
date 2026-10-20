package net.phoenixvine.solaris.integration;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.phoenixvine.solaris.api.SolarisMarkerSyncEvent;
import net.phoenixvine.solaris.integration.kineticwaypoints.KineticWaypointsIntegration;
import net.phoenixvine.solaris.integration.waystones.WaystonesIntegration;

import java.util.List;

/** Drives the mod marker sources: a full resend on marker sync, then a cheap diff every few seconds. */
@EventBusSubscriber()
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
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            for (ModMarkerSource source : SOURCES) source.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        for (ModMarkerSource source : SOURCES) source.reset(event.getEntity().getUUID());
    }
}
