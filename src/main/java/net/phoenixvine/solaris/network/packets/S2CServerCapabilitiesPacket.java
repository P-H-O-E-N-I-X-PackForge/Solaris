package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

public record S2CServerCapabilitiesPacket(
                                          int flags,
                                          int uploadChunksPerSecond)
        implements CustomPacketPayload {

    public static final int DEATH_MARKERS = 1;
    public static final int SHARED_EXPLORATION = 2;
    public static final int SHARED_VEINS = 4;
    public static final int PLAYER_POSITIONS = 8;

    public static final CustomPacketPayload.Type<S2CServerCapabilitiesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_server_capabilities"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CServerCapabilitiesPacket> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT, S2CServerCapabilitiesPacket::flags,
                    ByteBufCodecs.VAR_INT, S2CServerCapabilitiesPacket::uploadChunksPerSecond,
                    S2CServerCapabilitiesPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CServerCapabilitiesPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.serverCapabilities(
                    packet.flags(),
                    packet.uploadChunksPerSecond());
        });
    }
}
