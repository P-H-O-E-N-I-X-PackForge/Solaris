package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

public record S2CDeathMarkerPacket(
                                   String dimension,
                                   int x,
                                   int y,
                                   int z,
                                   String playerName,
                                   boolean own)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CDeathMarkerPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_death_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDeathMarkerPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), S2CDeathMarkerPacket::dimension,
            ByteBufCodecs.INT, S2CDeathMarkerPacket::x,
            ByteBufCodecs.INT, S2CDeathMarkerPacket::y,
            ByteBufCodecs.INT, S2CDeathMarkerPacket::z,
            ByteBufCodecs.stringUtf8(16), S2CDeathMarkerPacket::playerName,
            ByteBufCodecs.BOOL, S2CDeathMarkerPacket::own,
            S2CDeathMarkerPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CDeathMarkerPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {

            ClientPacketHandlers.deathMarker(
                    packet.dimension(),
                    packet.x(),
                    packet.y(),
                    packet.z(),
                    packet.playerName(),
                    packet.own());
        });
    }
}
