package net.phoenixvine.solaris.server.guild;

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
import java.util.UUID;

public class GuildWaypointData extends SavedData {

    private static final String SAVE_KEY = "solaris_guild_waypoints";

    public record Entry(String key, String name, String dimension, int x, int y, int z, String color, String icon,
                        String category) {}

    private final Map<UUID, Map<String, Entry>> byGuild = new LinkedHashMap<>();

    private static final SavedData.Factory<GuildWaypointData> FACTORY = new SavedData.Factory<>(
            GuildWaypointData::new,
            GuildWaypointData::load,
            null);

    public static GuildWaypointData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, SAVE_KEY);
    }

    private static GuildWaypointData load(CompoundTag tag, HolderLookup.Provider registries) {
        GuildWaypointData data = new GuildWaypointData();
        for (String guildKey : tag.getAllKeys()) {
            UUID guild;
            try {
                guild = UUID.fromString(guildKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Map<String, Entry> entries = new LinkedHashMap<>();
            ListTag list = tag.getList(guildKey, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                String key = c.getString("key");
                if (key.isEmpty()) continue;
                entries.put(key, new Entry(key, c.getString("name"), c.getString("dimension"), c.getInt("x"),
                        c.getInt("y"), c.getInt("z"), c.getString("color"), c.getString("icon"),
                        c.getString("category")));
            }
            data.byGuild.put(guild, entries);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (Map.Entry<UUID, Map<String, Entry>> guild : byGuild.entrySet()) {
            ListTag list = new ListTag();
            for (Entry e : guild.getValue().values()) {
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
            tag.put(guild.getKey().toString(), list);
        }
        return tag;
    }

    public List<Entry> entries(UUID guild) {
        Map<String, Entry> entries = byGuild.get(guild);
        return entries == null ? List.of() : new ArrayList<>(entries.values());
    }

    public boolean has(UUID guild, String key) {
        Map<String, Entry> entries = byGuild.get(guild);
        return entries != null && entries.containsKey(key);
    }

    public int count(UUID guild) {
        Map<String, Entry> entries = byGuild.get(guild);
        return entries == null ? 0 : entries.size();
    }

    public void put(UUID guild, Entry entry) {
        byGuild.computeIfAbsent(guild, g -> new LinkedHashMap<>()).put(entry.key(), entry);
        setDirty();
    }

    public boolean remove(UUID guild, String key) {
        Map<String, Entry> entries = byGuild.get(guild);
        boolean removed = entries != null && entries.remove(key) != null;
        if (removed) setDirty();
        return removed;
    }
}
