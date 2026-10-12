package net.phoenixvine.solaris.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisMarkerSyncEvent;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SolarisServerEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            
            ProgressionGates.evaluate(player);
            SolarisServerAPI.syncToClient(player);
            CapabilityService.send(player);
            ServerWaypointService.sendTo(player);
            GuildWaypointService.sendTo(player);
            SharedVeinService.sendAll(player);
            MinecraftForge.EVENT_BUS.post(new SolarisMarkerSyncEvent(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PingService.forget(player.getUUID());
            ClaimSyncService.forget(player.getUUID());
            GuildWaypointService.forget(player.getUUID());
            SharedExplorationService.forget(player.getUUID());
            SharedVeinService.forget(player.getUUID());
        }
    }
}
