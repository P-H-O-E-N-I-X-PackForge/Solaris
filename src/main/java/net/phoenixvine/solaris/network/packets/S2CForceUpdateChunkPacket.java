package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

public record S2CForceUpdateChunkPacket(
                                        String dimension,
                                        int chunkX,
                                        int chunkZ)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CForceUpdateChunkPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_force_update_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CForceUpdateChunkPacket> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.stringUtf8(128), S2CForceUpdateChunkPacket::dimension,
                    ByteBufCodecs.VAR_INT, S2CForceUpdateChunkPacket::chunkX,
                    ByteBufCodecs.VAR_INT, S2CForceUpdateChunkPacket::chunkZ,
                    S2CForceUpdateChunkPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CForceUpdateChunkPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.forceUpdateChunk(
                    packet.dimension(),
                    packet.chunkX(),
                    packet.chunkZ());
        });
    }
}
