package net.phoenixvine.solaris.integration.kineticwaypoints;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.integration.MarkerSync;
import net.phoenixvine.solaris.integration.ModMarkerSource;
import net.phoenixvine.solaris.integration.ModPresence;

import java.util.Map;
import java.util.UUID;

public final class KineticWaypointsIntegration implements ModMarkerSource {

    public static final String MOD_ID = "create_kinetic_waypoints";
    public static final KineticWaypointsIntegration INSTANCE = new KineticWaypointsIntegration();

    private final MarkerSync markers = new MarkerSync("E0A030", "STAR", "Kinetic Waypoints");
    private boolean failed = false;

    private KineticWaypointsIntegration() {}

    @Override
    public boolean isAvailable() {
        return ModPresence.isLoaded(MOD_ID);
    }

    @Override
    public void reset(UUID player) {
        markers.reset(player);
    }

    @Override
    public void sync(ServerPlayer player) {
        if (failed || !isAvailable()) return;

        try {
            Map<String, MarkerSync.Marker> desired = KineticWaypointsBridge.collect(player);
            markers.apply(player, desired);
        } catch (LinkageError | RuntimeException e) {
            failed = true;
            PhoenixSolaris.LOGGER.error("Create: Kinetic Waypoints is present but reading its waypoints failed " +
                    "(its API may differ from the version Solaris was built against); they won't be shown on " +
                    "the map this session.", e);
        }
    }
}
