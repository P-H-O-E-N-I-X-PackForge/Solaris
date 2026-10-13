package net.phoenixvine.solaris.integration.gtceu;

import com.gregtechceu.gtceu.integration.map.IWaypointHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

public class SolarisWaypointHandler implements IWaypointHandler {

    @Override
    public void setWaypoint(String key, String name, int color, ResourceKey<Level> dim, BlockPos pos) {
        WaypointManager.remove(key);

        if (!WaypointManager.canPlace(dim.location())) return;

        Waypoint waypoint = new Waypoint(name, dim.location(), pos.getX(), pos.getY(), pos.getZ(),
                String.format("%06X", color & 0xFFFFFF));
        waypoint.id = key;
        waypoint.category = "GT Veins";
        WaypointManager.add(waypoint);
    }

    @Override
    public void removeWaypoint(String key) {
        WaypointManager.remove(key);
    }
}
