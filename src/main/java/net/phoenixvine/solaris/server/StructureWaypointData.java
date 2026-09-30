package net.phoenixvine.solaris.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StructureWaypointData extends SavedData {

    private static final String SAVE_KEY = "solaris_structure_waypoints";

    private final Map<UUID, Set<String>> discoveredByPlayer = new LinkedHashMap<>();

    public static StructureWaypointData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(StructureWaypointData::load, StructureWaypointData::new,
                SAVE_KEY);
    }

    private static StructureWaypointData load(CompoundTag tag) {
        StructureWaypointData data = new StructureWaypointData();
        for (String playerKey : tag.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(playerKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Set<String> discovered = new LinkedHashSet<>();
            for (Tag entry : tag.getList(playerKey, Tag.TAG_STRING)) {
                discovered.add(entry.getAsString());
            }
            data.discoveredByPlayer.put(player, discovered);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, Set<String>> entry : discoveredByPlayer.entrySet()) {
            ListTag list = new ListTag();
            for (String key : entry.getValue()) {
                list.add(StringTag.valueOf(key));
            }
            tag.put(entry.getKey().toString(), list);
        }
        return tag;
    }

    public boolean hasDiscovered(UUID player, String structureInstanceKey) {
        Set<String> discovered = discoveredByPlayer.get(player);
        return discovered != null && discovered.contains(structureInstanceKey);
    }

    public boolean markDiscovered(UUID player, String structureInstanceKey) {
        boolean added = discoveredByPlayer.computeIfAbsent(player, p -> new LinkedHashSet<>())
                .add(structureInstanceKey);
        if (added) setDirty();
        return added;
    }
}
