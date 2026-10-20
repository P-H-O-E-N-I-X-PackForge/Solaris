package net.phoenixvine.solaris.integration.kineticwaypoints;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.integration.MarkerSync;
import net.phoenixvine.solaris.integration.ModMarkerSource;
import net.phoenixvine.solaris.integration.ModPresence;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows Create: Kinetic Waypoints machines on the Solaris map as markers, for the players who are
 * allowed to see them (global ones, their own, and ones they've discovered). Done by reflection so
 * Solaris doesn't need that mod to build or run, and the mod's own visibility rules stay the single
 * source of truth.
 */
public final class KineticWaypointsIntegration implements ModMarkerSource {

    public static final String MOD_ID = "create_kinetic_waypoints";
    public static final KineticWaypointsIntegration INSTANCE = new KineticWaypointsIntegration();

    private final MarkerSync markers = new MarkerSync("E0A030", "STAR", "Kinetic Waypoints");

    private Bridge bridge;
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
            if (bridge == null) bridge = new Bridge();

            Map<String, MarkerSync.Marker> desired = new HashMap<>();
            for (Object record : bridge.records(player.serverLevel())) {
                if (!bridge.canSee(player, record)) continue;

                String rawName = bridge.name(record);
                String name = rawName == null || rawName.isBlank() ? "Kinetic Waypoint" : rawName;
                ResourceKey<Level> dimension = bridge.dimension(record);
                desired.put(MOD_ID + ":" + bridge.id(record),
                        new MarkerSync.Marker(name, dimension.location(), bridge.position(player, record)));
            }
            markers.apply(player, desired);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            PhoenixSolaris.LOGGER.error("Create: Kinetic Waypoints is present but reading its waypoints failed " +
                    "(its API may not match what Solaris expects); they won't be shown on the map this session.", e);
        }
    }

    private static final class Bridge {

        private static final String NETWORK = "com.victorio.createkineticwaypoints.content.network.";

        private final Method savedDataGet;
        private final Method savedDataAll;
        private final Method canPlayerSee;
        private final Method id;
        private final Method name;
        private final Method dimension;
        private final Method pos;
        // Newer versions can have waypoints on moving structures, whose stored pos is local to
        // that structure; the resolver turns it into a real world position. Absent in older ones.
        private final Method resolve;
        private final Method resolvedGlobalPosition;

        Bridge() throws ReflectiveOperationException {
            Class<?> savedData = Class.forName(NETWORK + "WaypointNetworkSavedData");
            Class<?> manager = Class.forName(NETWORK + "WaypointNetworkManager");
            Class<?> record = Class.forName(NETWORK + "WaypointRecord");

            savedDataGet = savedData.getMethod("get", ServerLevel.class);
            savedDataAll = savedData.getMethod("all");
            canPlayerSee = manager.getMethod("canPlayerSee", ServerPlayer.class, record);
            id = record.getMethod("id");
            name = record.getMethod("name");
            dimension = record.getMethod("dimension");
            pos = record.getMethod("pos");

            Method resolveMethod = null;
            Method globalMethod = null;
            try {
                Class<?> resolver = Class.forName(NETWORK + "WaypointPositionResolver");
                Class<?> resolved = Class.forName(NETWORK + "WaypointPositionResolver$ResolvedWaypoint");
                resolveMethod = resolver.getMethod("resolve", ServerLevel.class, record);
                globalMethod = resolved.getMethod("globalPosition");
            } catch (ReflectiveOperationException ignored) {}
            resolve = resolveMethod;
            resolvedGlobalPosition = globalMethod;
        }

        @SuppressWarnings("unchecked")
        Collection<Object> records(ServerLevel level) throws ReflectiveOperationException {
            return (Collection<Object>) savedDataAll.invoke(savedDataGet.invoke(null, level));
        }

        boolean canSee(ServerPlayer player, Object record) throws ReflectiveOperationException {
            return (Boolean) canPlayerSee.invoke(null, player, record);
        }

        UUID id(Object record) throws ReflectiveOperationException {
            return (UUID) id.invoke(record);
        }

        String name(Object record) throws ReflectiveOperationException {
            return (String) name.invoke(record);
        }

        @SuppressWarnings("unchecked")
        ResourceKey<Level> dimension(Object record) throws ReflectiveOperationException {
            return (ResourceKey<Level>) dimension.invoke(record);
        }

        BlockPos position(ServerPlayer player, Object record) throws ReflectiveOperationException {
            if (resolve != null) {
                ServerLevel level = player.server.getLevel(dimension(record));
                if (level != null) {
                    Object resolved = resolve.invoke(null, level, record);
                    if (resolved != null) {
                        Vec3 global = (Vec3) resolvedGlobalPosition.invoke(resolved);
                        if (global != null) return BlockPos.containing(global);
                    }
                }
            }
            return (BlockPos) pos.invoke(record);
        }
    }
}
