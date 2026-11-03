package net.phoenixvine.solaris.client.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MapLayers {

    private static final List<MapLayer> LAYERS = new CopyOnWriteArrayList<>();

    private static final List<MapLayer> MINIMAP_LAYERS = new CopyOnWriteArrayList<>();

    private MapLayers() {}

    /** A layer drawn on the minimap, over the terrain; the projection says where the world is and what is visible. */
    public static void registerMinimap(MapLayer layer) {
        MINIMAP_LAYERS.remove(layer);
        MINIMAP_LAYERS.add(layer);
    }

    public static void unregisterMinimap(MapLayer layer) {
        MINIMAP_LAYERS.remove(layer);
    }

    public static List<MapLayer> minimap() {
        return MINIMAP_LAYERS;
    }


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
