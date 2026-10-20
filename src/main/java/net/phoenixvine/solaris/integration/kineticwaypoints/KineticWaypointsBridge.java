package net.phoenixvine.solaris.integration.kineticwaypoints;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.phoenixvine.solaris.integration.MarkerSync;

import com.victorio.createkineticwaypoints.content.network.WaypointNetworkManager;
import com.victorio.createkineticwaypoints.content.network.WaypointNetworkSavedData;
import com.victorio.createkineticwaypoints.content.network.WaypointPositionResolver;
import com.victorio.createkineticwaypoints.content.network.WaypointRecord;

import java.util.HashMap;
import java.util.Map;

/**
 * The only class that touches Create: Kinetic Waypoints types. Keep it that way: it must only be
 * reached once the mod is known to be loaded, so nothing else may reference it from a signature.
 */
final class KineticWaypointsBridge {

    private KineticWaypointsBridge() {}

    static Map<String, MarkerSync.Marker> collect(ServerPlayer player) {
        Map<String, MarkerSync.Marker> desired = new HashMap<>();
        for (WaypointRecord record : WaypointNetworkSavedData.get(player.serverLevel()).all()) {
            if (!WaypointNetworkManager.canPlayerSee(player, record)) continue;

            String name = record.name() == null || record.name().isBlank() ? "Kinetic Waypoint" : record.name();
            desired.put(KineticWaypointsIntegration.MOD_ID + ":" + record.id(),
                    new MarkerSync.Marker(name, record.dimension().location(), position(player, record)));
        }
        return desired;
    }

    // A waypoint on a moving structure stores a position local to it; the resolver gives the real one.
    private static BlockPos position(ServerPlayer player, WaypointRecord record) {
        ServerLevel level = player.server.getLevel(record.dimension());
        if (level != null) {
            WaypointPositionResolver.ResolvedWaypoint resolved = WaypointPositionResolver.resolve(level, record);
            if (resolved != null) {
                Vec3 global = resolved.globalPosition();
                if (global != null) return BlockPos.containing(global);
            }
        }
        return record.pos();
    }
}
