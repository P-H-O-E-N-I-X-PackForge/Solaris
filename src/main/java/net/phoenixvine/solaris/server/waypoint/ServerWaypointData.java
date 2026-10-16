package net.phoenixvine.solaris.server.waypoint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerWaypointData extends SavedData {

    private static final String SAVE_KEY = "solaris_server_waypoints";

    public record Entry(String key, String name, String dimension, int x, int y, int z, String color, String icon,
                        String category) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private static final SavedData.Factory<ServerWaypointData> FACTORY = new SavedData.Factory<>(
            ServerWaypointData::new,
            ServerWaypointData::load,
            null);

    public static ServerWaypointData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, SAVE_KEY);
    }

    private static ServerWaypointData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerWaypointData data = new ServerWaypointData();
        ListTag list = tag.getList("waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String key = c.getString("key");
            if (key.isEmpty()) continue;
            data.entries.put(key, new Entry(key, c.getString("name"), c.getString("dimension"), c.getInt("x"),
                    c.getInt("y"), c.getInt("z"), c.getString("color"), c.getString("icon"),
                    c.getString("category")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("key", e.key());
            c.putString("name", e.name());
            c.putString("dimension", e.dimension());
            c.putInt("x", e.x());
            c.putInt("y", e.y());
            c.putInt("z", e.z());
            c.putString("color", e.color());
            c.putString("icon", e.icon());
            c.putString("category", e.category());
            list.add(c);
        }
        tag.put("waypoints", list);
        return tag;
    }

    public List<Entry> all() {
        return new ArrayList<>(entries.values());
    }

    public boolean put(Entry entry) {
        boolean replaced = entries.put(entry.key(), entry) != null;
        setDirty();
        return replaced;
    }

    public boolean remove(String key) {
        boolean removed = entries.remove(key) != null;
        if (removed) setDirty();
        return removed;
    }
}
