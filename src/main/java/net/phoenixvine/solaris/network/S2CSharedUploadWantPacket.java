package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSharedUploadWantPacket {

    private final String dimension;
    private final int minChunkX;
    private final int minChunkZ;
    private final long[] want;

    public S2CSharedUploadWantPacket(String dimension, int minChunkX, int minChunkZ, long[] want) {
        this.dimension = dimension;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.want = want;
    }

    public S2CSharedUploadWantPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(128);
        this.minChunkX = buf.readInt();
        this.minChunkZ = buf.readInt();
        this.want = buf.readLongArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 128);
        buf.writeInt(minChunkX);
        buf.writeInt(minChunkZ);
        buf.writeLongArray(want);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        String dim = dimension;
        int mx = minChunkX;
        int mz = minChunkZ;
        long[] bits = want;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.sharedUploadWant(dim, mx, mz, bits)));
        ctx.get().setPacketHandled(true);
    }
}
