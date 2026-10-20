package net.phoenixvine.solaris.integration.waystones;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.integration.MarkerSync;
import net.phoenixvine.solaris.integration.ModMarkerSource;
import net.phoenixvine.solaris.integration.ModPresence;

import java.util.Map;
import java.util.UUID;

/**
 * Shows Waystones (by BlayTheNinth) on the Solaris map: the waystones a player has activated plus
 * any global ones. This class never names the mod's types; {@link WaystonesBridge} does, and is
 * only reached when the mod is loaded.
 */
public final class WaystonesIntegration implements ModMarkerSource {

    public static final String MOD_ID = "waystones";
    public static final WaystonesIntegration INSTANCE = new WaystonesIntegration();

    private final MarkerSync markers = new MarkerSync("7FD4FF", "CIRCLE", "Waystones");
    private boolean failed = false;

    private WaystonesIntegration() {}

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
            Map<String, MarkerSync.Marker> desired = WaystonesBridge.collect(player);
            markers.apply(player, desired);
        } catch (LinkageError | RuntimeException e) {
            failed = true;
            PhoenixSolaris.LOGGER.error("Waystones is present but reading its waystones failed (its API may " +
                    "differ from the version Solaris was built against); they won't be shown on the map this " +
                    "session.", e);
        }
    }
}
