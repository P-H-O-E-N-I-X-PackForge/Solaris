package net.phoenixvine.solaris.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureWaypointTracker {

    private static final int CHECK_INTERVAL_TICKS = 20;

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

        Map<Structure, LongSet> candidates = structureManager.getAllStructuresAt(pos);
        if (candidates.isEmpty()) return;

        StructureWaypointData data = StructureWaypointData.get(player.getServer().overworld());
        ResourceLocation dimension = player.level().dimension().location();

        for (Structure structure : candidates.keySet()) {
            StructureStart start = structureManager.getStructureWithPieceAt(pos, structure);
            if (!start.isValid()) continue;

            ResourceLocation structureId = player.level().registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getKey(structure);
            if (structureId == null) continue;

            ChunkPos originChunk = start.getChunkPos();
            String key = dimension + "|" + structureId + "|" + originChunk.toLong();
            if (!data.markDiscovered(id, key)) continue;

            BlockPos center = start.getBoundingBox().getCenter();
            SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CStructureDiscoveredPacket(displayName(structureId), dimension.toString(),
                            center.getX(), pos.getY(), center.getZ()));
        }
    }

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
