package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.server.ServerCapabilities;

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
public class DeathMarkerHandler {

    private static boolean recorded = false;

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DeathScreen)) return;
        if (recorded) return;

        if (ServerCapabilities.serverDeathMarkers()) return;
        if (!SolarisConfig.DEATH_MARKERS.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ResourceLocation dimension = mc.level.dimension().location();
        if (!WaypointManager.canPlace(dimension)) return;

        recorded = true;

        Waypoint waypoint = new Waypoint("Death", dimension,
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), "FF3B30");
        waypoint.icon = WaypointIcon.DANGER.name();
        waypoint.category = "Deaths";
        WaypointManager.add(waypoint);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!recorded) return;
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || !(mc.screen instanceof DeathScreen)) recorded = false;
    }
}
