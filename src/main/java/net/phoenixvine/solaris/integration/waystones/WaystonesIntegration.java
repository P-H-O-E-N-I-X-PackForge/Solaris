package net.phoenixvine.solaris.integration.waystones;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.integration.MarkerSync;
import net.phoenixvine.solaris.integration.ModMarkerSource;
import net.phoenixvine.solaris.integration.ModPresence;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Shows Waystones (by BlayTheNinth) on the Solaris map: the waystones a player has activated plus
 * any global ones. Reflection against the shared parts of its API, which keeps the same code working
 * on both the 1.20.1 (IWaystone) and 1.21.1 (Waystone) versions without building against either.
 */
public final class WaystonesIntegration implements ModMarkerSource {

    public static final String MOD_ID = "waystones";
    public static final WaystonesIntegration INSTANCE = new WaystonesIntegration();

    private final MarkerSync markers = new MarkerSync("7FD4FF", "CIRCLE", "Waystones");

    private Bridge bridge;
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
            if (bridge == null) bridge = new Bridge();

            Map<String, Object> visible = new HashMap<>();
            for (Object waystone : bridge.activated(player)) {
                visible.put(MOD_ID + ":" + bridge.uid(waystone), waystone);
            }
            try (Stream<?> all = bridge.all(player.server)) {
                Iterator<?> it = all.iterator();
                while (it.hasNext()) {
                    Object waystone = it.next();
                    if (bridge.isGlobal(waystone)) visible.put(MOD_ID + ":" + bridge.uid(waystone), waystone);
                }
            }

            Map<String, MarkerSync.Marker> desired = new HashMap<>();
            for (Map.Entry<String, Object> entry : visible.entrySet()) {
                Object waystone = entry.getValue();
                if (!bridge.isUsable(waystone)) continue;

                String name = bridge.name(waystone);
                ResourceKey<Level> dimension = bridge.dimension(waystone);
                desired.put(entry.getKey(), new MarkerSync.Marker(name.isBlank() ? "Waystone" : name,
                        dimension.location(), bridge.pos(waystone)));
            }
            markers.apply(player, desired);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            PhoenixSolaris.LOGGER.error("Waystones is present but reading its waystones failed (its API may not " +
                    "match what Solaris expects); they won't be shown on the map this session.", e);
        }
    }

    private static final class Bridge {

        private final Method getActivated;
        private final Method getAll;
        private final Method uid;
        private final Method name;
        private final Method dimension;
        private final Method pos;
        private final Method isValid;
        private final Method isGlobal;
        private final Method getVisibility;
        private final Method isTransient;

        Bridge() throws ReflectiveOperationException {
            Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            Class<?> waystone = firstClass("net.blay09.mods.waystones.api.Waystone",
                    "net.blay09.mods.waystones.api.IWaystone");

            getActivated = api.getMethod("getActivatedWaystones", Player.class);
            getAll = api.getMethod("getAllWaystones", MinecraftServer.class);
            uid = waystone.getMethod("getWaystoneUid");
            name = waystone.getMethod("getName");
            dimension = waystone.getMethod("getDimension");
            pos = waystone.getMethod("getPos");
            isValid = waystone.getMethod("isValid");
            isGlobal = optional(waystone, "isGlobal");
            getVisibility = optional(waystone, "getVisibility");
            isTransient = optional(waystone, "isTransient");
        }

        private static Class<?> firstClass(String... names) throws ClassNotFoundException {
            ClassNotFoundException last = null;
            for (String n : names) {
                try {
                    return Class.forName(n);
                } catch (ClassNotFoundException e) {
                    last = e;
                }
            }
            throw last;
        }

        private static Method optional(Class<?> type, String method) {
            try {
                return type.getMethod(method);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        Collection<Object> activated(ServerPlayer player) throws ReflectiveOperationException {
            return (Collection<Object>) getActivated.invoke(null, player);
        }

        Stream<?> all(MinecraftServer server) throws ReflectiveOperationException {
            return (Stream<?>) getAll.invoke(null, server);
        }

        UUID uid(Object waystone) throws ReflectiveOperationException {
            return (UUID) uid.invoke(waystone);
        }

        // 1.20.1 returns a String, 1.21.1 a Component.
        String name(Object waystone) throws ReflectiveOperationException {
            Object value = name.invoke(waystone);
            if (value instanceof Component component) return component.getString();
            return value == null ? "" : value.toString();
        }

        @SuppressWarnings("unchecked")
        ResourceKey<Level> dimension(Object waystone) throws ReflectiveOperationException {
            return (ResourceKey<Level>) dimension.invoke(waystone);
        }

        BlockPos pos(Object waystone) throws ReflectiveOperationException {
            return (BlockPos) pos.invoke(waystone);
        }

        boolean isUsable(Object waystone) throws ReflectiveOperationException {
            if (!(Boolean) isValid.invoke(waystone)) return false;
            return isTransient == null || !(Boolean) isTransient.invoke(waystone);
        }

        boolean isGlobal(Object waystone) throws ReflectiveOperationException {
            if (isGlobal != null) return (Boolean) isGlobal.invoke(waystone);
            if (getVisibility != null) {
                Object visibility = getVisibility.invoke(waystone);
                return visibility != null && visibility.toString().equals("GLOBAL");
            }
            return false;
        }
    }
}
