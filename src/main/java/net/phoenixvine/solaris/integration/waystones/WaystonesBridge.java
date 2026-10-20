package net.phoenixvine.solaris.integration.waystones;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneVisibility;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.integration.MarkerSync;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The only class that touches Waystones types. It must only be reached once the mod is known to be
 * loaded, so nothing else may reference it from a signature.
 */
final class WaystonesBridge {

    private WaystonesBridge() {}

    static Map<String, MarkerSync.Marker> collect(ServerPlayer player) {
        Map<String, Waystone> visible = new HashMap<>();
        for (Waystone waystone : WaystonesAPI.getActivatedWaystones(player)) {
            visible.put(key(waystone), waystone);
        }
        try (Stream<Waystone> all = WaystonesAPI.getAllWaystones(player.server)) {
            all.filter(waystone -> waystone.getVisibility() == WaystoneVisibility.GLOBAL)
                    .forEach(waystone -> visible.put(key(waystone), waystone));
        }

        Map<String, MarkerSync.Marker> desired = new HashMap<>();
        for (Map.Entry<String, Waystone> entry : visible.entrySet()) {
            Waystone waystone = entry.getValue();
            if (!waystone.isValid() || waystone.isTransient()) continue;

            String name = waystone.getEffectiveName().getString();
            desired.put(entry.getKey(), new MarkerSync.Marker(name.isBlank() ? "Waystone" : name,
                    waystone.getDimension().location(), waystone.getPos()));
        }
        return desired;
    }

    private static String key(Waystone waystone) {
        return WaystonesIntegration.MOD_ID + ":" + waystone.getWaystoneUid();
    }
}
