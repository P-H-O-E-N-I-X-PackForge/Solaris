package net.phoenixvine.solaris.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.network.S2CStructureDiscoveredPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;

import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-waypoints structures as players walk into them. Detection lives server-side (structure
 * reference data isn't synced to the client at all — ClientLevel has no structure manager), so
 * this only ever runs on a logical server; single-player still goes through here via the
 * integrated server.
 * <p>
 * Discovery state persists via StructureWaypointData (a SavedData attached to the overworld), so
 * a server restart or the player relogging doesn't re-announce a structure they've already found.
 */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureWaypointTracker {

    private static final int CHECK_INTERVAL_TICKS = 20;

    // Structure lookups (reading each overlapping chunk's structure references) aren't free, so
    // this only re-checks when the player has actually moved to a different chunk since the last
    // check, on top of the tick-interval throttle below. Fine to keep this part in-memory only —
    // worst case after a restart is one redundant re-check, not a re-announced waypoint (that's
    // what StructureWaypointData's persisted dedup actually guards against).
    private static final Map<UUID, ChunkPos> LAST_CHECKED_CHUNK = new ConcurrentHashMap<>();

    private StructureWaypointTracker() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!SolarisConfig.AUTO_STRUCTURE_WAYPOINTS.get()) return;
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) return;

        BlockPos pos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(pos);
        UUID id = player.getUUID();
        if (chunkPos.equals(LAST_CHECKED_CHUNK.get(id))) return;
        LAST_CHECKED_CHUNK.put(id, chunkPos);

        StructureManager structureManager = player.serverLevel().structureManager();
        Map<Structure, LongSet> structures = structureManager.getAllStructuresAt(pos);
        if (structures.isEmpty()) return;

        StructureWaypointData data = StructureWaypointData.get(player.getServer().overworld());
        ResourceLocation dimension = player.level().dimension().location();

        for (Map.Entry<Structure, LongSet> entry : structures.entrySet()) {
            LongSet chunks = entry.getValue();
            if (chunks.isEmpty()) continue;

            long instanceChunk = chunks.longStream().min().orElseThrow();
            ResourceLocation structureId = player.level().registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getKey(entry.getKey());
            if (structureId == null) continue;

            String key = dimension + "|" + structureId + "|" + instanceChunk;
            if (!data.markDiscovered(id, key)) continue;

            ChunkPos originChunk = new ChunkPos(instanceChunk);
            SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CStructureDiscoveredPacket(displayName(structureId), dimension.toString(),
                            originChunk.getMiddleBlockX(), pos.getY(), originChunk.getMiddleBlockZ()));
        }
    }

    /** "village_plains" -> "Village Plains" — structures have no standard translation namespace. */
    private static String displayName(ResourceLocation structureId) {
        String[] words = structureId.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
