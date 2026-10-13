package net.phoenixvine.solaris.network.packets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

public record S2CReceiveWaypointPacket(
                                       String senderName,
                                       String name,
                                       String dimension,
                                       int x,
                                       int y,
                                       int z,
                                       String color,
                                       String icon,
                                       boolean locked)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CReceiveWaypointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_receive_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CReceiveWaypointPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.senderName(), 16);
                buf.writeUtf(packet.name(), 48);
                buf.writeUtf(packet.dimension(), 128);
                buf.writeVarInt(packet.x());
                buf.writeVarInt(packet.y());
                buf.writeVarInt(packet.z());
                buf.writeUtf(packet.color(), 8);
                buf.writeUtf(packet.icon(), 32);
                buf.writeBoolean(packet.locked());
            },
            buf -> new S2CReceiveWaypointPacket(
                    buf.readUtf(16),
                    buf.readUtf(48),
                    buf.readUtf(128),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(8),
                    buf.readUtf(32),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CReceiveWaypointPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Note: In 1.21.1, ResourceLocation parsing uses ResourceLocation.parse(...) or fromNamespaceAndPath
            Waypoint w = new Waypoint(packet.name(), ResourceLocation.parse(packet.dimension()), packet.x(), packet.y(),
                    packet.z(), packet.color());
            w.icon = packet.icon();
            w.locked = packet.locked();
            WaypointManager.add(w);

            mc.player.sendSystemMessage(Component.literal(
                    "Received waypoint '" + packet.name() + "' from " + packet.senderName() + " Added to your list.")
                    .withStyle(ChatFormatting.AQUA));
        });
    }
}
