package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.common.SharedChunkBlob;
import net.phoenixvine.solaris.common.SharedChunkFormat;
import net.phoenixvine.solaris.server.exploration.SharedExplorationService;

import java.util.ArrayList;
import java.util.List;

public record C2SSharedChunksPacket(String dimension, List<SharedChunkBlob> blobs) implements CustomPacketPayload {

    public static final int MAX_BLOBS = 32;

    public static final CustomPacketPayload.Type<C2SSharedChunksPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "c2s_shared_chunks"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSharedChunksPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.dimension(), 128);
                int count = Math.min(packet.blobs().size(), MAX_BLOBS);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    SharedChunkBlob b = packet.blobs().get(i);
                    buf.writeInt(b.x());
                    buf.writeInt(b.z());
                    buf.writeByteArray(b.data());
                }
            },
            buf -> {
                String dim = buf.readUtf(128);
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_BLOBS) {
                    throw new IllegalArgumentException("Too many chunks: " + count);
                }
                List<SharedChunkBlob> blobList = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int x = buf.readInt();
                    int z = buf.readInt();
                    blobList.add(new SharedChunkBlob(x, z, buf.readByteArray(SharedChunkFormat.MAX_BLOB_BYTES)));
                }
                return new C2SSharedChunksPacket(dim, blobList);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SSharedChunksPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                SharedExplorationService.handleUpload(sender, packet.dimension(), packet.blobs());
            }
        });
    }
}
