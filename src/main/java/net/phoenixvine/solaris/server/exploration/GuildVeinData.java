package net.phoenixvine.solaris.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.phoenixvine.solaris.sharing.SharedVeinEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuildVeinData extends SavedData {

    private static final String SAVE_KEY = "solaris_guild_veins";

    private final Map<UUID, Map<String, SharedVeinEntry>> byGuild = new LinkedHashMap<>();

    public static GuildVeinData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(GuildVeinData::load, GuildVeinData::new, SAVE_KEY);
    }

    private static GuildVeinData load(CompoundTag tag) {
        GuildVeinData data = new GuildVeinData();
        for (String guildKey : tag.getAllKeys()) {
            UUID guild;
            try {
                guild = UUID.fromString(guildKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Map<String, SharedVeinEntry> entries = new LinkedHashMap<>();
            ListTag list = tag.getList(guildKey, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                SharedVeinEntry e = new SharedVeinEntry(c.getString("dimension"), c.getInt("x"), c.getInt("y"),
                        c.getInt("z"), c.getString("name"), c.getInt("argb"), c.getString("item"));
                entries.put(e.key(), e);
            }
            data.byGuild.put(guild, entries);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, Map<String, SharedVeinEntry>> guild : byGuild.entrySet()) {
            ListTag list = new ListTag();
            for (SharedVeinEntry e : guild.getValue().values()) {
                CompoundTag c = new CompoundTag();
                c.putString("dimension", e.dimension());
                c.putInt("x", e.x());
                c.putInt("y", e.y());
                c.putInt("z", e.z());
                c.putString("name", e.name());
                c.putInt("argb", e.argb());
                c.putString("item", e.itemId());
                list.add(c);
            }
            tag.put(guild.getKey().toString(), list);
        }
        return tag;
    }

    public List<SharedVeinEntry> entries(UUID guild) {
        Map<String, SharedVeinEntry> entries = byGuild.get(guild);
        return entries == null ? List.of() : new ArrayList<>(entries.values());
    }

    public int count(UUID guild) {
        Map<String, SharedVeinEntry> entries = byGuild.get(guild);
        return entries == null ? 0 : entries.size();
    }

    public boolean add(UUID guild, SharedVeinEntry entry) {
        Map<String, SharedVeinEntry> entries = byGuild.computeIfAbsent(guild, g -> new LinkedHashMap<>());
        if (entries.putIfAbsent(entry.key(), entry) != null) return false;
        setDirty();
        return true;
    }
}
