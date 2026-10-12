package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CForceUpdateChunkPacket {

    private final String dimension;
    private final int chunkX;
    private final int chunkZ;

    public S2CForceUpdateChunkPacket(String dimension, int chunkX, int chunkZ) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public S2CForceUpdateChunkPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(128);
        this.chunkX = buf.readVarInt();
        this.chunkZ = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 128);
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        String dim = dimension;
        int cx = chunkX;
        int cz = chunkZ;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.forceUpdateChunk(dim, cx, cz)));
        ctx.get().setPacketHandled(true);
    }
}
