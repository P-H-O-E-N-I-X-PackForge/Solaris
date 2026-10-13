package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.server.ServerCapabilities;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !recorded) return;
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || !(mc.screen instanceof DeathScreen)) recorded = false;
    }
}
