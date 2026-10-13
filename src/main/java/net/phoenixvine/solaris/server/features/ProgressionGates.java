package net.phoenixvine.solaris.server.features;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.api.SolarisGatesAPI;
import net.phoenixvine.solaris.api.gates.GateCondition;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.server.SolarisServerAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber()
public final class ProgressionGates {

    private record GroupKey(String feature, ResourceLocation dimension) {}

    private static final class Result {

        boolean changed = false;
        final Set<String> newlyUnlocked = new HashSet<>();
    }

    private static List<? extends String> parsedFrom = null;
    private static List<SolarisGatesAPI.Binding> parsed = List.of();
    private static final Set<String> WARNED = new HashSet<>();
    private static int pollCounter = 0;

    private ProgressionGates() {}

    private static List<SolarisGatesAPI.Binding> configBindings() {
        if (!SolarisServerConfig.SPEC.isLoaded()) return List.of();
        List<? extends String> raw = SolarisServerConfig.PROGRESSION_GATES.get();
        if (raw.equals(parsedFrom)) return parsed;

        List<SolarisGatesAPI.Binding> out = new ArrayList<>();
        for (String entry : raw) {
            int eq = entry.indexOf('=');
            if (eq < 0) {
                warnOnce("Ignoring Solaris gate '" + entry + "': expected feature@dimension=gate");
                continue;
            }
            String left = entry.substring(0, eq).trim();
            int at = left.indexOf('@');
            String feature = (at < 0 ? left : left.substring(0, at)).trim();
            String dimensionText = at < 0 ? "*" : left.substring(at + 1).trim();
            ResourceLocation dimension = "*".equals(dimensionText) ? null : ResourceLocation.tryParse(dimensionText);

            List<String> anyOf = new ArrayList<>();
            for (String gate : entry.substring(eq + 1).split("\\|")) {
                if (!gate.isBlank()) anyOf.add(gate.trim());
            }

            if (feature.isEmpty() || anyOf.isEmpty() || (dimension == null && !"*".equals(dimensionText))) {
                warnOnce("Ignoring Solaris gate '" + entry + "': expected feature@dimension=gate");
                continue;
            }
            out.add(new SolarisGatesAPI.Binding(feature, dimension, anyOf));
        }
        parsedFrom = new ArrayList<>(raw);
        parsed = out;
        return parsed;
    }

    private static List<SolarisGatesAPI.Binding> allBindings() {
        List<SolarisGatesAPI.Binding> all = new ArrayList<>(configBindings());
        all.addAll(SolarisGatesAPI.bindings());
        return all;
    }

    private static void warnOnce(String message) {
        if (WARNED.add(message)) PhoenixSolaris.LOGGER.warn(message);
    }

    public static boolean isUnlocked(ServerPlayer player, String gateId) {
        GateCondition condition = SolarisGatesAPI.condition(gateId);
        if (condition != null) {
            try {
                return condition.test(player);
            } catch (Exception e) {
                warnOnce("Solaris gate '" + gateId + "' threw " + e);
                return false;
            }
        }

        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (ManualGateData.get(server.overworld()).has(GuildsIntegration.ownerTokenFor(player), gateId)) return true;

        ResourceLocation advancementId = ResourceLocation.tryParse(gateId);
        if (advancementId != null) {
            // Modernized 1.21.1 advancement lookup using Holder lookup / registries
            AdvancementHolder advancement = server.getAdvancements().get(advancementId);
            if (advancement != null) {
                return player.getAdvancements().getOrStartProgress(advancement).isDone();
            }
        }
        return false;
    }

    public static void grant(ServerPlayer player, String gateId) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ManualGateData.get(server.overworld()).grant(GuildsIntegration.ownerTokenFor(player), gateId);
        refresh(player);
    }

    public static void revoke(ServerPlayer player, String gateId) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ManualGateData.get(server.overworld()).revoke(GuildsIntegration.ownerTokenFor(player), gateId);
        refresh(player);
    }

    public static Set<String> manualGates(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return Set.of();
        return ManualGateData.get(server.overworld()).list(GuildsIntegration.ownerTokenFor(player));
    }

    private static Result apply(ServerPlayer player) {
        Result result = new Result();
        MinecraftServer server = player.getServer();
        if (server == null || !SolarisServerConfig.SPEC.isLoaded()) return result;

        List<SolarisGatesAPI.Binding> bindings = allBindings();
        if (bindings.isEmpty()) return result;

        SolarisFeatureState locked = SolarisServerConfig.GATE_LOCKED_STATE.get();
        SolarisFeatureState unlocked = SolarisServerConfig.GATE_UNLOCKED_STATE.get();
        if (locked == unlocked) return result;

        SolarisFeatureStateData data = SolarisFeatureStateData.get(server.overworld());
        UUID token = GuildsIntegration.ownerTokenFor(player);
        boolean solo = token.equals(player.getUUID());

        Map<GroupKey, List<SolarisGatesAPI.Binding>> groups = new LinkedHashMap<>();
        for (SolarisGatesAPI.Binding binding : bindings) {
            if (binding.dimension() != null) {
                groups.computeIfAbsent(new GroupKey(binding.feature(), binding.dimension()), k -> new ArrayList<>())
                        .add(binding);
            } else {
                for (ResourceKey<Level> key : server.levelKeys()) {
                    groups.computeIfAbsent(new GroupKey(binding.feature(), key.location()), k -> new ArrayList<>())
                            .add(binding);
                }
            }
        }

        Map<String, Boolean> memo = new HashMap<>();
        for (Map.Entry<GroupKey, List<SolarisGatesAPI.Binding>> group : groups.entrySet()) {
            String feature = group.getKey().feature();
            ResourceLocation dimension = group.getKey().dimension();

            boolean satisfied = true;
            for (SolarisGatesAPI.Binding binding : group.getValue()) {
                boolean any = false;
                for (String gate : binding.anyOf()) {
                    if (memo.computeIfAbsent(gate, g -> isUnlocked(player, g))) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    satisfied = false;
                    break;
                }
            }

            SolarisFeatureState current = data.get(token, dimension, feature);
            boolean explicit = data.isExplicit(token, dimension, feature);
            if (satisfied) {
                if (current != unlocked) {
                    data.set(token, dimension, feature, unlocked);
                    result.changed = true;
                    if (current == locked) result.newlyUnlocked.add(feature);
                }
            } else if (!explicit) {
                data.set(token, dimension, feature, locked);
                result.changed = true;
            } else if (solo && current == unlocked) {

                data.set(token, dimension, feature, locked);
                result.changed = true;
            }
        }
        return result;
    }

    public static void evaluate(ServerPlayer player) {
        apply(player);
    }

    public static void refresh(ServerPlayer player) {
        Result result = apply(player);
        if (!result.changed) return;

        SolarisServerAPI.syncToClient(player);
        for (ServerPlayer mate : GuildsIntegration.getOnlineGuildmates(player)) {
            SolarisServerAPI.syncToClient(mate);
        }
        for (String feature : result.newlyUnlocked) {
            player.displayClientMessage(
                    Component.literal("Map feature unlocked: " + feature).withStyle(ChatFormatting.GREEN), true);
        }
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SolarisServerConfig.SPEC.isLoaded()) return;
        int interval = SolarisServerConfig.PROGRESSION_POLL_TICKS.get();
        if (interval <= 0) return;
        if (++pollCounter < interval) return;
        pollCounter = 0;

        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }
}
