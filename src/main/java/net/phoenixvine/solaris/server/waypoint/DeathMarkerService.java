package net.phoenixvine.solaris.server.waypoint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.packets.S2CDeathMarkerPacket;
import net.phoenixvine.solaris.server.CapabilityService;
import net.phoenixvine.solaris.server.SolarisServerUtil;

@EventBusSubscriber()
public final class DeathMarkerService {

    private DeathMarkerService() {}

    public static boolean enabled() {
        return SolarisServerConfig.SPEC.isLoaded() && SolarisServerConfig.SERVER_DEATH_MARKERS.get();
    }

    public static void sendCapabilities(ServerPlayer player) {
        CapabilityService.send(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!enabled()) return;

        ResourceLocation dimension = player.level().dimension().location();
        String name = player.getGameProfile().getName();
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();

        if (SolarisServerUtil.hasSolaris(player)) {
            PacketDistributor.sendToPlayer(player, new S2CDeathMarkerPacket(dimension.toString(), x, y, z, name, true));
        }

        if (SolarisServerConfig.SHARE_DEATHS_WITH_GUILD.get()) {
            for (ServerPlayer mate : GuildsIntegration.getOnlineGuildmates(player)) {
                if (!SolarisServerUtil.hasSolaris(mate)) continue;
                PacketDistributor.sendToPlayer(mate,
                        new S2CDeathMarkerPacket(dimension.toString(), x, y, z, name, false));
            }
        }
    }
}
