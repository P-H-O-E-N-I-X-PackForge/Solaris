package net.phoenixvine.solaris.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.solaris.api.SolarisMarkers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps one player's Solaris markers for a mod in step with what that mod says they should see:
 * only new, renamed or moved markers are sent, and ones that are no longer wanted are removed.
 */
public final class MarkerSync {

    /** color, icon and category are optional per-marker overrides of the MarkerSync's defaults. */
    public record Marker(String name, ResourceLocation dimension, BlockPos pos, String color, String icon,
                         String category) {

        public Marker(String name, ResourceLocation dimension, BlockPos pos) {
            this(name, dimension, pos, null, null, null);
        }
    }

    private final String color;
    private final String icon;
    private final String category;
    private final Map<UUID, Map<String, String>> sent = new HashMap<>();

    public MarkerSync(String color, String icon, String category) {
        this.color = color;
        this.icon = icon;
        this.category = category;
    }

    public void apply(ServerPlayer player, Map<String, Marker> desired) {
        Map<String, String> previous = sent.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Map<String, String> current = new HashMap<>();

        for (Map.Entry<String, Marker> entry : desired.entrySet()) {
            String key = entry.getKey();
            Marker marker = entry.getValue();
            String signature = marker.name() + "|" + marker.dimension() + "|" + marker.pos().asLong() + "|" +
                    marker.color() + "|" + marker.icon() + "|" + marker.category();
            current.put(key, signature);

            if (!signature.equals(previous.get(key))) {
                SolarisMarkers.marker(key)
                        .name(marker.name())
                        .at(marker.dimension(), marker.pos().getX(), marker.pos().getY(), marker.pos().getZ())
                        .color(marker.color() != null ? marker.color() : color)
                        .icon(marker.icon() != null ? marker.icon() : icon)
                        .category(marker.category() != null ? marker.category() : category)
                        .sendTo(player);
            }
        }

        for (String key : previous.keySet()) {
            if (!current.containsKey(key)) SolarisMarkers.remove(player, key);
        }
        previous.clear();
        previous.putAll(current);
    }

    /** Forget what was sent, so the next apply resends everything (the client's markers were reset). */
    public void reset(UUID player) {
        sent.remove(player);
    }
}
