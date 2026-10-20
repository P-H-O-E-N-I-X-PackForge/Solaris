package net.phoenixvine.solaris.integration.kineticwaypoints;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisMarkerSyncEvent;
import net.phoenixvine.solaris.api.SolarisMarkers;

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
@EventBusSubscriber()
public final class KineticWaypointsIntegration {

    public static final String MOD_ID = "create_kinetic_waypoints";

    private static final String KEY_PREFIX = MOD_ID + ":";
    private static final String CATEGORY = "Kinetic Waypoints";
    private static final String COLOR = "E0A030";
    private static final String ICON = "STAR";
    private static final int SYNC_INTERVAL_TICKS = 100;

    // player -> marker key -> what was last sent for it, so only changes and removals cost a packet
    private static final Map<UUID, Map<String, String>> SENT = new HashMap<>();

    private static Bridge bridge;
    private static boolean bridgeFailed = false;

    private KineticWaypointsIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SubscribeEvent
    public static void onMarkerSync(SolarisMarkerSyncEvent event) {
        SENT.remove(event.getPlayer().getUUID());
        sync(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    private static void sync(ServerPlayer player) {
        if (bridgeFailed || !isAvailable()) return;

        Bridge b = bridge();
        if (b == null) return;

        try {
            Map<String, String> previous = SENT.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
            Map<String, String> current = new HashMap<>();

            for (Object record : b.records(player.serverLevel())) {
                if (!b.canSee(player, record)) continue;

                String key = KEY_PREFIX + b.id(record);
                String rawName = b.name(record);
                String name = rawName == null || rawName.isBlank() ? "Kinetic Waypoint" : rawName;
                ResourceLocation dimension = b.dimension(record).location();
                BlockPos pos = b.pos(record);
                String signature = name + "|" + dimension + "|" + pos.asLong();
                current.put(key, signature);

                if (!signature.equals(previous.get(key))) {
                    SolarisMarkers.marker(key)
                            .name(name)
                            .at(dimension, pos.getX(), pos.getY(), pos.getZ())
                            .color(COLOR)
                            .icon(ICON)
                            .category(CATEGORY)
                            .sendTo(player);
                }
            }

            for (String key : previous.keySet()) {
                if (!current.containsKey(key)) SolarisMarkers.remove(player, key);
            }
            previous.clear();
            previous.putAll(current);
        } catch (ReflectiveOperationException | RuntimeException e) {
            bridgeFailed = true;
            PhoenixSolaris.LOGGER.error("Create: Kinetic Waypoints is present but reading its waypoints failed; " +
                    "they won't be shown on the map this session.", e);
        }
    }

    private static Bridge bridge() {
        if (bridge != null) return bridge;
        try {
            bridge = new Bridge();
        } catch (ReflectiveOperationException e) {
            bridgeFailed = true;
            PhoenixSolaris.LOGGER.error("Create: Kinetic Waypoints is present but its API didn't match what " +
                    "Solaris expects; its waypoints won't be shown on the map.", e);
        }
        return bridge;
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

        BlockPos pos(Object record) throws ReflectiveOperationException {
            return (BlockPos) pos.invoke(record);
        }
    }
}
