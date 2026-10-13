package net.phoenixvine.solaris.client.overlay;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.network.packets.S2CClaimsPacket;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
public final class ClaimOverlay implements SolarisOverlay {

    public static final ClaimOverlay INSTANCE = new ClaimOverlay();

    private static final int DEFAULT_FILL_ALPHA = 0x48;
    private static final int BORDER_BOOST = 0x50;

    private record Snapshot(ResourceLocation dimension, Long2IntOpenHashMap colors) {}

    private static volatile Snapshot snapshot = null;

    private ClaimOverlay() {}

    @Override
    public Optional<Integer> colorAt(ResourceLocation dimension, int chunkX, int chunkZ) {
        Snapshot s = snapshot;
        if (s == null || !s.dimension().equals(dimension)) return Optional.empty();
        int color = s.colors().get(ChunkPos.asLong(chunkX, chunkZ));
        return color == 0 ? Optional.empty() : Optional.of(color);
    }

    public void apply(ResourceLocation dimension, int centerX, int centerZ, int radius,
                      List<S2CClaimsPacket.Owner> owners) {
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

        if (owners.isEmpty()) {
            snapshot = null;
            SolarisAPI.requestRefresh();
            return;
        }

        Long2IntOpenHashMap ownerOf = new Long2IntOpenHashMap();
        ownerOf.defaultReturnValue(-1);
        int[] rgb = new int[owners.size()];
        int[] fillAlpha = new int[owners.size()];
        for (int i = 0; i < owners.size(); i++) {
            S2CClaimsPacket.Owner owner = owners.get(i);
            rgb[i] = owner.argb() & 0xFFFFFF;
            int a = owner.argb() >>> 24;
            fillAlpha[i] = a == 0 ? DEFAULT_FILL_ALPHA : a;
            for (long chunk : owner.chunks()) ownerOf.put(chunk, i);
        }

        Long2IntOpenHashMap colors = new Long2IntOpenHashMap(ownerOf.size());
        colors.defaultReturnValue(0);
        for (Long2IntMap.Entry entry : ownerOf.long2IntEntrySet()) {
            long key = entry.getLongKey();
            int owner = entry.getIntValue();
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);

            boolean border = differs(ownerOf, owner, cx + 1, cz, minX, maxX, minZ, maxZ) ||
                    differs(ownerOf, owner, cx - 1, cz, minX, maxX, minZ, maxZ) ||
                    differs(ownerOf, owner, cx, cz + 1, minX, maxX, minZ, maxZ) ||
                    differs(ownerOf, owner, cx, cz - 1, minX, maxX, minZ, maxZ);

            int alpha = Math.min(255, fillAlpha[owner] + (border ? BORDER_BOOST : 0));
            colors.put(key, (alpha << 24) | rgb[owner]);
        }

        snapshot = new Snapshot(dimension, colors);
        SolarisAPI.requestRefresh();
    }

    private static boolean differs(Long2IntOpenHashMap ownerOf, int owner, int cx, int cz, int minX, int maxX,
                                   int minZ, int maxZ) {
        if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) return false;
        return ownerOf.get(ChunkPos.asLong(cx, cz)) != owner;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = null;
    }
}
