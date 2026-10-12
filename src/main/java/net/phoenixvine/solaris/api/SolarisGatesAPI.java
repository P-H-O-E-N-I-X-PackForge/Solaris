package net.phoenixvine.solaris.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.api.gates.GateCondition;
import net.phoenixvine.solaris.server.ProgressionGates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SolarisGatesAPI {

    public record Binding(String feature, ResourceLocation dimension, List<String> anyOf) {}

    private static final Map<String, GateCondition> CONDITIONS = new ConcurrentHashMap<>();
    private static final List<Binding> BINDINGS = new CopyOnWriteArrayList<>();

    private SolarisGatesAPI() {}

    public static void registerGate(String gateId, GateCondition condition) {
        CONDITIONS.put(gateId, condition);
    }

    public static void unregisterGate(String gateId) {
        CONDITIONS.remove(gateId);
    }

    public static GateCondition condition(String gateId) {
        return CONDITIONS.get(gateId);
    }

    public static void bind(String featureId, ResourceLocation dimension, String... anyOfGateIds) {
        if (anyOfGateIds.length == 0) throw new IllegalArgumentException("A binding needs at least one gate id");
        BINDINGS.add(new Binding(featureId, dimension, List.of(anyOfGateIds)));
    }

    public static void unbindAll(String featureId) {
        BINDINGS.removeIf(b -> b.feature().equals(featureId));
    }

    public static List<Binding> bindings() {
        return new ArrayList<>(BINDINGS);
    }

    public static boolean isUnlocked(ServerPlayer player, String gateId) {
        return ProgressionGates.isUnlocked(player, gateId);
    }

    public static void grant(ServerPlayer player, String gateId) {
        ProgressionGates.grant(player, gateId);
    }

    public static void revoke(ServerPlayer player, String gateId) {
        ProgressionGates.revoke(player, gateId);
    }

    public static void refresh(ServerPlayer player) {
        ProgressionGates.refresh(player);
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) ProgressionGates.refresh(player);
    }
}
