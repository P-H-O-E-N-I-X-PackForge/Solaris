package net.phoenixvine.solaris.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.S2CDeathMarkerPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
            SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CDeathMarkerPacket(dimension.toString(), x, y, z, name, true));
        }

        if (SolarisServerConfig.SHARE_DEATHS_WITH_GUILD.get()) {
            for (ServerPlayer mate : GuildsIntegration.getOnlineGuildmates(player)) {
                if (!SolarisServerUtil.hasSolaris(mate)) continue;
                SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> mate),
                        new S2CDeathMarkerPacket(dimension.toString(), x, y, z, name, false));
            }
        }
    }
}
