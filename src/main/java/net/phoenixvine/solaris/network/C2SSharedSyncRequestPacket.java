package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.common.SharedChunkFormat;
import net.phoenixvine.solaris.server.exploration.SharedExplorationService;


import java.util.function.Supplier;

public class C2SSharedSyncRequestPacket {

    private final String dimension;
    private final int minChunkX;
    private final int minChunkZ;
    private final long[] have;

    public C2SSharedSyncRequestPacket(String dimension, int minChunkX, int minChunkZ, long[] have) {
        this.dimension = dimension;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.have = have;
    }

    public C2SSharedSyncRequestPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(128);
        this.minChunkX = buf.readInt();
        this.minChunkZ = buf.readInt();
        this.have = buf.readLongArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 128);
        buf.writeInt(minChunkX);
        buf.writeInt(minChunkZ);
        buf.writeLongArray(have);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && have.length == SharedChunkFormat.WINDOW) {
                SharedExplorationService.handleSyncRequest(sender, dimension, minChunkX, minChunkZ, have);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
