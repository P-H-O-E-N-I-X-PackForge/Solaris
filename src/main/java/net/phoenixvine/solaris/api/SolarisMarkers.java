package net.phoenixvine.solaris.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.S2CMarkerPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;
import net.phoenixvine.solaris.server.SolarisServerUtil;

import java.util.Collection;

public final class SolarisMarkers {

    public static final int MAX_DURATION_TICKS = 20 * 60 * 60 * 24;

    private SolarisMarkers() {}

    public static Builder marker(String key) {
        return new Builder(validKey(key));
    }

    public static void remove(ServerPlayer target, String key) {
        send(target, S2CMarkerPacket.remove(validKey(key)));
    }

    public static void removeFromAll(MinecraftServer server, String key) {
        S2CMarkerPacket packet = S2CMarkerPacket.remove(validKey(key));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, packet);
    }

    public static void clear(ServerPlayer target, String keyPrefix) {
        send(target, S2CMarkerPacket.clear(limit(keyPrefix, 64)));
    }

    private static String validKey(String key) {
        if (key == null || key.length() > 64 || key.indexOf(':') <= 0) {
            throw new IllegalArgumentException("Marker keys must look like 'modid:name' and be at most 64 characters");
        }
        return key;
    }

    private static String limit(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static void send(ServerPlayer target, S2CMarkerPacket packet) {
        if (!SolarisServerUtil.hasSolaris(target)) return;
        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), packet);
    }

    public static final class Builder {

        private final String key;
        private String name = "";
        private ResourceLocation dimension;
        private int x;
        private int y;
        private int z;
        private String color = "FFFFFF";
        private String icon = "DOT";
        private String category = "Markers";
        private int durationTicks = 0;

        private Builder(String key) {
            this.key = key;
        }

        public Builder name(String name) {
            this.name = limit(name, 48);
            return this;
        }

        public Builder at(ResourceLocation dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder color(String hex) {
            this.color = hex != null && hex.matches("[0-9A-Fa-f]{6,8}") ? hex.toUpperCase(java.util.Locale.ROOT) :
                    "FFFFFF";
            return this;
        }

        public Builder icon(String icon) {
            this.icon = limit(icon, 32).isEmpty() ? "DOT" : limit(icon, 32);
            return this;
        }

        public Builder category(String category) {
            this.category = limit(category, 32);
            return this;
        }

        public Builder expiresAfterTicks(int ticks) {
            this.durationTicks = Math.max(0, Math.min(ticks, MAX_DURATION_TICKS));
            return this;
        }

        private S2CMarkerPacket build() {
            if (dimension == null) throw new IllegalStateException("Call at(dimension, x, y, z) before sending");
            return S2CMarkerPacket.put(key, name, dimension.toString(), x, y, z, color, icon, category,
                    durationTicks);
        }

        public void sendTo(ServerPlayer target) {
            send(target, build());
        }

        public void sendTo(Collection<? extends ServerPlayer> targets) {
            S2CMarkerPacket packet = build();
            for (ServerPlayer p : targets) send(p, packet);
        }

        public void sendToGuild(ServerPlayer player) {
            S2CMarkerPacket packet = build();
            send(player, packet);
            for (ServerPlayer mate : GuildsIntegration.getOnlineGuildmates(player)) send(mate, packet);
        }

        public void sendToAll(MinecraftServer server) {
            sendTo(server.getPlayerList().getPlayers());
        }
    }
}
