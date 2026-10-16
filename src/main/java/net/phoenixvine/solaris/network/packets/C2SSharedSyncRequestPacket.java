package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.common.SharedChunkFormat;
import net.phoenixvine.solaris.server.exploration.SharedExplorationService;

public record C2SSharedSyncRequestPacket(
                                         String dimension,
                                         int minChunkX,
                                         int minChunkZ,
                                         long[] have)
        implements CustomPacketPayload {

    public static final Type<C2SSharedSyncRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "c2s_shared_sync_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSharedSyncRequestPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.dimension(), 128);
                buf.writeInt(packet.minChunkX());
                buf.writeInt(packet.minChunkZ());
                buf.writeLongArray(packet.have());
            },
            buf -> {
                String dimension = buf.readUtf(128);
                int minChunkX = buf.readInt();
                int minChunkZ = buf.readInt();
                long[] have = buf.readLongArray();
                return new C2SSharedSyncRequestPacket(dimension, minChunkX, minChunkZ, have);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SSharedSyncRequestPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null && packet.have().length == SharedChunkFormat.WINDOW) {
                SharedExplorationService.handleSyncRequest(
                        sender,
                        packet.dimension(),
                        packet.minChunkX(),
                        packet.minChunkZ(),
                        packet.have());
            }
        });
    }
}
