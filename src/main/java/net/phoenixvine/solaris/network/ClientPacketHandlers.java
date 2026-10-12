package net.phoenixvine.solaris.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.color.ChunkColorEvents;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.overlay.ClaimOverlay;
import net.phoenixvine.solaris.client.ping.PingTracker;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.waypoint.TransientMarkers;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointIcon;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.common.SharedChunkBlob;
import net.phoenixvine.solaris.common.SharedExplorationClient;
import net.phoenixvine.solaris.common.SharedVeinClient;
import net.phoenixvine.solaris.common.SharedVeinEntry;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.network.S2CClaimsPacket;
import net.phoenixvine.solaris.network.S2CMarkerPacket;
import net.phoenixvine.solaris.network.S2CServerWaypointsPacket;
import net.phoenixvine.solaris.server.ServerCapabilities;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    public static final String SERVER_WAYPOINT_PREFIX = "server:";
    
    public static final String GUILD_WAYPOINT_PREFIX = "guild:";

    private ClientPacketHandlers() {}

    public static void forceUpdateChunk(String dimension, int chunkX, int chunkZ) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || !level.dimension().location().toString().equals(dimension)) return;

        if (!level.hasChunk(chunkX, chunkZ)) return;

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        ChunkKey key = ChunkKey.of(level, pos);
        ChunkColorEvents.resample(level, key, pos);
        SolarisTexture.invalidateAll();
    }

    public static void serverCapabilities(int flags) {
        ServerCapabilities.setFlags(flags);
        PhoenixSolaris.LOGGER.info("[Solaris] Server features: deathMarkers={}, sharedExploration={}, " +
                "sharedVeins={}, playerPositions={}", ServerCapabilities.serverDeathMarkers(),
                ServerCapabilities.sharedExploration(), ServerCapabilities.sharedVeins(),
                ServerCapabilities.playerPositions());
    }

    public static void sharedChunks(String dimension, List<SharedChunkBlob> blobs) {
        SharedExplorationClient.receiveChunks(dimension, blobs);
    }

    public static void sharedUploadWant(String dimension, int minChunkX, int minChunkZ, long[] want) {
        SharedExplorationClient.receiveWant(dimension, minChunkX, minChunkZ, want);
    }

    public static void sharedVeins(int flags, List<SharedVeinEntry> entries) {
        SharedVeinClient.receive(flags, entries);
    }

    public static void deathMarker(String dimension, int x, int y, int z, String playerName, boolean own) {
        if (!SolarisConfig.DEATH_MARKERS.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ResourceLocation dim = new ResourceLocation(dimension);
        if (!WaypointManager.canPlace(dim)) return;

        Waypoint waypoint = new Waypoint(own ? "Death" : "Death: " + playerName, dim, x, y, z,
                own ? "FF3B30" : "FF8A00");
        waypoint.icon = WaypointIcon.DANGER.name();
        waypoint.category = own ? "Deaths" : "Guild Deaths";
        WaypointManager.add(waypoint);
    }

    public static void serverWaypoints(List<S2CServerWaypointsPacket.Entry> entries) {
        syncManagedWaypoints(SERVER_WAYPOINT_PREFIX, entries);
    }

    public static void guildWaypoints(List<S2CServerWaypointsPacket.Entry> entries) {
        syncManagedWaypoints(GUILD_WAYPOINT_PREFIX, entries);
    }

    private static void syncManagedWaypoints(String prefix, List<S2CServerWaypointsPacket.Entry> entries) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Set<String> keep = new HashSet<>();
        List<Waypoint> existing = new ArrayList<>(WaypointManager.getAll());

        for (S2CServerWaypointsPacket.Entry e : entries) {
            String id = prefix + e.key();
            keep.add(id);

            Waypoint target = null;
            for (Waypoint w : existing) {
                if (id.equals(w.id)) {
                    target = w;
                    break;
                }
            }

            if (target == null) {
                Waypoint w = new Waypoint(e.name(), new ResourceLocation(e.dimension()), e.x(), e.y(), e.z(),
                        e.color());
                w.id = id;
                w.icon = e.icon();
                w.category = e.category();
                w.locked = true;
                WaypointManager.add(w);
            } else {
                target.name = e.name();
                target.dimension = e.dimension();
                target.x = e.x();
                target.y = e.y();
                target.z = e.z();
                target.color = e.color();
                target.icon = e.icon();
                target.category = e.category();
                target.locked = true;
            }
        }

        for (Waypoint w : existing) {
            if (w.id != null && w.id.startsWith(prefix) && !keep.contains(w.id)) WaypointManager.remove(w.id);
        }
        WaypointManager.save();
    }

    public static void marker(S2CMarkerPacket.Op op, String key, String name, String dimension, int x, int y, int z,
                              String color, String icon, String category, int durationTicks) {
        switch (op) {
            case PUT -> {
                Waypoint w = new Waypoint(name, new ResourceLocation(dimension), x, y, z, color);
                w.id = TransientMarkers.ID_PREFIX + key;
                w.icon = icon;
                w.category = category;
                w.locked = true;
                TransientMarkers.put(key, w, durationTicks > 0 ? durationTicks * 50L : 0L);
            }
            case REMOVE -> TransientMarkers.remove(key);
            case CLEAR -> TransientMarkers.clearPrefix(key);
        }
    }

    public static void claims(String dimension, int centerX, int centerZ, int radius,
                              List<S2CClaimsPacket.Owner> owners) {
        ClaimOverlay.INSTANCE.apply(ResourceLocation.parse(dimension), centerX, centerZ, radius, owners);
    }

    public static void ping(String sender, String dimension, int x, int y, int z, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PingTracker.add(sender, ResourceLocation.parse(dimension), x, y, z, durationTicks * 50L);
        if (!sender.equals(mc.player.getGameProfile().getName())) {
            mc.player.displayClientMessage(Component.literal(sender + " pinged a location"), true);
        }
    }
}
