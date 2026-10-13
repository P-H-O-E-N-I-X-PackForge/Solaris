package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

public record S2CSharedUploadWantPacket(
                                        String dimension,
                                        int minChunkX,
                                        int minChunkZ,
                                        long[] want)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CSharedUploadWantPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_shared_upload_want"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSharedUploadWantPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.dimension(), 128);
                buf.writeInt(packet.minChunkX());
                buf.writeInt(packet.minChunkZ());
                buf.writeLongArray(packet.want());
            },
            buf -> new S2CSharedUploadWantPacket(
                    buf.readUtf(128),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readLongArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CSharedUploadWantPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.sharedUploadWant(
                    packet.dimension(),
                    packet.minChunkX(),
                    packet.minChunkZ(),
                    packet.want());
        });
    }
}
