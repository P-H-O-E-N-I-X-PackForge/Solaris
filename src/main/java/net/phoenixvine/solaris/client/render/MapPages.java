package net.phoenixvine.solaris.client.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The pages other mods have added next to the world map. */
public final class MapPages {

    private static final List<MapPage> PAGES = new CopyOnWriteArrayList<>();

    private MapPages() {}

    public static void register(MapPage page) {
        if (!PAGES.contains(page)) PAGES.add(page);
    }

    public static void unregister(MapPage page) {
        PAGES.remove(page);
    }

    public static List<MapPage> all() {
        return PAGES;
    }

    public static List<MapPage> available() {
        return PAGES.stream().filter(MapPage::available).toList();
    }
}
