package net.phoenixvine.solaris.network.packets;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;

import java.util.List;

public record C2SShareWaypointPacket(
                                     String name,
                                     String dimension,
                                     int x,
                                     int y,
                                     int z,
                                     String color,
                                     String icon)
        implements CustomPacketPayload {

    public static final Type<C2SShareWaypointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "share_waypoint"));

    public static final StreamCodec<FriendlyByteBuf, C2SShareWaypointPacket> STREAM_CODEC = StreamCodec.of(
            C2SShareWaypointPacket::encode,
            C2SShareWaypointPacket::new);

    private C2SShareWaypointPacket(FriendlyByteBuf buf) {
        this(
                buf.readUtf(48),
                buf.readUtf(128),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(8),
                buf.readUtf(32));
    }

    private static void encode(FriendlyByteBuf buf, C2SShareWaypointPacket packet) {
        buf.writeUtf(packet.name, 48);
        buf.writeUtf(packet.dimension, 128);
        buf.writeVarInt(packet.x);
        buf.writeVarInt(packet.y);
        buf.writeVarInt(packet.z);
        buf.writeUtf(packet.color, 8);
        buf.writeUtf(packet.icon, 32);
    }

    @Override
    public Type<C2SShareWaypointPacket> type() {
        return TYPE;
    }

    public static void handle(C2SShareWaypointPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;

            if (!GuildsIntegration.isAvailable()) {
                sender.sendSystemMessage(
                        Component.literal("Guilds isn't installed on this server.").withStyle(ChatFormatting.RED));
                return;
            }

            List<ServerPlayer> guildmates = GuildsIntegration.getOnlineGuildmates(sender);
            if (guildmates.isEmpty()) {
                sender.sendSystemMessage(Component.literal("No online guildmates to share with.")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }

            S2CReceiveWaypointPacket outgoing = new S2CReceiveWaypointPacket(
                    sender.getGameProfile().getName(),
                    packet.name, packet.dimension, packet.x, packet.y, packet.z, packet.color, packet.icon, false);

            for (ServerPlayer mate : guildmates) {
                PacketDistributor.sendToPlayer(mate, outgoing);
            }

            sender.sendSystemMessage(
                    Component.literal("Shared waypoint '" + packet.name + "' with " + guildmates.size() +
                            " guildmate(s).").withStyle(ChatFormatting.GREEN));
        });
    }
}
