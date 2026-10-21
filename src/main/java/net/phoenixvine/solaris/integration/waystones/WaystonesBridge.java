package net.phoenixvine.solaris.integration.waystones;

import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.integration.MarkerSync;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

final class WaystonesBridge {

    private WaystonesBridge() {}

    static Map<String, MarkerSync.Marker> collect(ServerPlayer player) {
        Map<String, IWaystone> visible = new HashMap<>();
        for (IWaystone waystone : WaystonesAPI.getActivatedWaystones(player)) {
            visible.put(key(waystone), waystone);
        }
        try (Stream<IWaystone> all = WaystonesAPI.getAllWaystones(player.server)) {
            all.filter(IWaystone::isGlobal).forEach(waystone -> visible.put(key(waystone), waystone));
        }

        Map<String, MarkerSync.Marker> desired = new HashMap<>();
        for (Map.Entry<String, IWaystone> entry : visible.entrySet()) {
            IWaystone waystone = entry.getValue();
            if (!waystone.isValid()) continue;

            String name = waystone.getName();
            desired.put(entry.getKey(), new MarkerSync.Marker(name == null || name.isBlank() ? "Waystone" : name,
                    waystone.getDimension().location(), waystone.getPos()));
        }
        return desired;
    }

    private static String key(IWaystone waystone) {
        return WaystonesIntegration.MOD_ID + ":" + waystone.getWaystoneUid();
    }
}
