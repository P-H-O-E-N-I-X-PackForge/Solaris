package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TransientMarkers {

    public static final String ID_PREFIX = "marker:";

    private record Entry(Waypoint waypoint, long expiresAtMillis) {}

    private static final Map<String, Entry> MARKERS = new LinkedHashMap<>();

    private TransientMarkers() {}

    public static void put(String key, Waypoint waypoint, long durationMillis) {
        long expires = durationMillis > 0 ? System.currentTimeMillis() + durationMillis : 0L;
        MARKERS.put(key, new Entry(waypoint, expires));
    }

    public static void remove(String key) {
        MARKERS.remove(key);
    }

    public static void clearPrefix(String prefix) {
        MARKERS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clear() {
        MARKERS.clear();
    }

    public static List<Waypoint> collect(ResourceLocation dimension) {
        if (MARKERS.isEmpty()) return List.of();

        long now = System.currentTimeMillis();
        String dim = dimension.toString();
        List<Waypoint> out = new ArrayList<>();
        Iterator<Entry> it = MARKERS.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.expiresAtMillis() != 0L && e.expiresAtMillis() <= now) {
                it.remove();
                continue;
            }
            if (e.waypoint().visible && dim.equals(e.waypoint().dimension)) out.add(e.waypoint());
        }
        return out;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
