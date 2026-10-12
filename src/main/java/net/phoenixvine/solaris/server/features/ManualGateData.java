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

public class ManualGateData extends SavedData {

    private static final String SAVE_KEY = "solaris_manual_gates";

    private final Map<UUID, Set<String>> byToken = new LinkedHashMap<>();

    public static ManualGateData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(ManualGateData::load, ManualGateData::new, SAVE_KEY);
    }

    private static ManualGateData load(CompoundTag tag) {
        ManualGateData data = new ManualGateData();
        for (String key : tag.getAllKeys()) {
            UUID token;
            try {
                token = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Set<String> gates = new LinkedHashSet<>();
            for (Tag entry : tag.getList(key, Tag.TAG_STRING)) gates.add(entry.getAsString());
            data.byToken.put(token, gates);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, Set<String>> entry : byToken.entrySet()) {
            ListTag list = new ListTag();
            for (String gate : entry.getValue()) list.add(StringTag.valueOf(gate));
            tag.put(entry.getKey().toString(), list);
        }
        return tag;
    }

    public boolean has(UUID token, String gate) {
        Set<String> gates = byToken.get(token);
        return gates != null && gates.contains(gate);
    }

    public boolean grant(UUID token, String gate) {
        boolean added = byToken.computeIfAbsent(token, t -> new LinkedHashSet<>()).add(gate);
        if (added) setDirty();
        return added;
    }

    public boolean revoke(UUID token, String gate) {
        Set<String> gates = byToken.get(token);
        boolean removed = gates != null && gates.remove(gate);
        if (removed) setDirty();
        return removed;
    }

    public Set<String> list(UUID token) {
        Set<String> gates = byToken.get(token);
        return gates == null ? Set.of() : new LinkedHashSet<>(gates);
    }
}
