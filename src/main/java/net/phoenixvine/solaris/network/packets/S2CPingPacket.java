package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

public record S2CPingPacket(
                            String senderName,
                            String dimension,
                            int x,
                            int y,
                            int z,
                            int durationTicks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CPingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_ping"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPingPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), S2CPingPacket::senderName,
            ByteBufCodecs.stringUtf8(128), S2CPingPacket::dimension,
            ByteBufCodecs.INT, S2CPingPacket::x,
            ByteBufCodecs.INT, S2CPingPacket::y,
            ByteBufCodecs.INT, S2CPingPacket::z,
            ByteBufCodecs.VAR_INT, S2CPingPacket::durationTicks,
            S2CPingPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CPingPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.ping(
                    packet.senderName(),
                    packet.dimension(),
                    packet.x(),
                    packet.y(),
                    packet.z(),
                    packet.durationTicks());
        });
    }
}
