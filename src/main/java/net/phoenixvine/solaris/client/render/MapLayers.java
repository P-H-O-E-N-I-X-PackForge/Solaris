package net.phoenixvine.solaris.client.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MapLayers {

    private static final List<MapLayer> LAYERS = new CopyOnWriteArrayList<>();

    private MapLayers() {}

    public static void register(MapLayer layer) {
        LAYERS.remove(layer);
        LAYERS.add(layer);
    }

    public static void unregister(MapLayer layer) {
        LAYERS.remove(layer);
    }

    public static List<MapLayer> all() {
        return LAYERS;
    }
}
