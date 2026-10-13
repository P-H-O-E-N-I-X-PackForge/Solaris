package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.List;

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
public class WaypointCompassOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        if (!SolarisConfig.WAYPOINT_COMPASS.get()) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;

        Waypoint target = WaypointManager.getTracked();
        if (target == null || !target.dimension.equals(mc.level.dimension().location().toString())) {
            target = nearest(mc);
        }
        if (target == null) return;

        double dx = (target.x + 0.5) - mc.player.getX();
        double dz = (target.z + 0.5) - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double bearingDeg = Math.toDegrees(Math.atan2(-dx, dz));
        float relativeDeg = (float) (bearingDeg - mc.player.getYRot());

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int cx = screenW / 2;
        int cy = 20;

        PlayerArrow.draw(g, cx, cy, 5, relativeDeg + 180f, target.colorArgb());

        String label = target.name + " (" + Math.round(distance) + "m)";
        int textWidth = mc.font.width(label);
        g.drawString(mc.font, label, cx - textWidth / 2, cy + 10, -1, false);
    }

    private static Waypoint nearest(Minecraft mc) {
        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        Waypoint best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Waypoint w : waypoints) {
            double distSq = w.distanceSq(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = w;
            }
        }
        return best;
    }
}
