package net.phoenixvine.solaris.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

public record S2CRemoveWaypointPacket(String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CRemoveWaypointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_remove_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRemoveWaypointPacket> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.stringUtf8(48), S2CRemoveWaypointPacket::name,
                    S2CRemoveWaypointPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CRemoveWaypointPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                WaypointManager.removeByName(packet.name());
            }
        });
    }
}
