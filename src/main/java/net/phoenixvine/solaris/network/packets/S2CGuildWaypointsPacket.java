package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

import java.util.List;

public record S2CGuildWaypointsPacket(List<S2CServerWaypointsPacket.Entry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CGuildWaypointsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_guild_waypoints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CGuildWaypointsPacket> STREAM_CODEC = StreamCodec
            .composite(
                    S2CServerWaypointsPacket.Entry.STREAM_CODEC
                            .apply(ByteBufCodecs.list(S2CServerWaypointsPacket.MAX_ENTRIES)),
                    S2CGuildWaypointsPacket::entries,
                    S2CGuildWaypointsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CGuildWaypointsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.guildWaypoints(packet.entries());
        });
    }
}
