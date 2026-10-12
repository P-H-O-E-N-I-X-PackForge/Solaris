package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.common.SharedChunkBlob;
import net.phoenixvine.solaris.common.SharedChunkFormat;
import net.phoenixvine.solaris.server.exploration.SharedExplorationService;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SSharedChunksPacket {

    public static final int MAX_BLOBS = 32;

    private final String dimension;
    private final List<SharedChunkBlob> blobs;

    public C2SSharedChunksPacket(String dimension, List<SharedChunkBlob> blobs) {
        this.dimension = dimension;
        this.blobs = blobs;
    }

    public C2SSharedChunksPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(128);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BLOBS) throw new IllegalArgumentException("Too many chunks: " + count);
        this.blobs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int x = buf.readInt();
            int z = buf.readInt();
            blobs.add(new SharedChunkBlob(x, z, buf.readByteArray(SharedChunkFormat.MAX_BLOB_BYTES)));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 128);
        int count = Math.min(blobs.size(), MAX_BLOBS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            SharedChunkBlob b = blobs.get(i);
            buf.writeInt(b.x());
            buf.writeInt(b.z());
            buf.writeByteArray(b.data());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) SharedExplorationService.handleUpload(sender, dimension, blobs);
        });
        ctx.get().setPacketHandled(true);
    }
}
