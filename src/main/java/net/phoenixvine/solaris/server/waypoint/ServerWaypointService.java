package net.phoenixvine.solaris.server.waypoint;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.solaris.network.packets.S2CServerWaypointsPacket;
import net.phoenixvine.solaris.server.SolarisServerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerWaypointService {

    private ServerWaypointService() {}

    public static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() > 40) s = s.substring(0, 40);
        return s.isEmpty() ? "waypoint" : s;
    }

    public static List<ServerWaypointData.Entry> all(MinecraftServer server) {
        return ServerWaypointData.get(server.overworld()).all();
    }

    public static void put(MinecraftServer server, ServerWaypointData.Entry entry) {
        ServerWaypointData.get(server.overworld()).put(entry);
        broadcast(server);
    }

    public static boolean remove(MinecraftServer server, String key) {
        boolean removed = ServerWaypointData.get(server.overworld()).remove(key);
        if (removed) broadcast(server);
        return removed;
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendTo(player);
    }

    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !SolarisServerUtil.hasSolaris(player)) return;

        List<S2CServerWaypointsPacket.Entry> out = new ArrayList<>();
        for (ServerWaypointData.Entry e : all(server)) {
            out.add(new S2CServerWaypointsPacket.Entry(e.key(), e.name(), e.dimension(), e.x(), e.y(), e.z(),
                    e.color(), e.icon(), e.category()));
        }

        // Modernized 1.21 packet dispatch
        PacketDistributor.sendToPlayer(player, new S2CServerWaypointsPacket(out));
    }
}
