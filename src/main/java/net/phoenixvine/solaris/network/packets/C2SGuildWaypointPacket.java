package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.server.guild.GuildWaypointService;

public record C2SGuildWaypointPacket(
                                     Op op,
                                     String key,
                                     String name,
                                     String dimension,
                                     int x,
                                     int y,
                                     int z,
                                     String color,
                                     String icon,
                                     String category)
        implements CustomPacketPayload {

    public enum Op {
        UPSERT,
        REMOVE
    }

    public static final Type<C2SGuildWaypointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "c2s_guild_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SGuildWaypointPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeEnum(packet.op());
                buf.writeUtf(packet.key(), 64);
                if (packet.op() == Op.UPSERT) {
                    buf.writeUtf(packet.name(), 48);
                    buf.writeUtf(packet.dimension(), 128);
                    buf.writeInt(packet.x());
                    buf.writeInt(packet.y());
                    buf.writeInt(packet.z());
                    buf.writeUtf(packet.color(), 8);
                    buf.writeUtf(packet.icon(), 32);
                    buf.writeUtf(packet.category(), 32);
                }
            },
            buf -> {
                Op op = buf.readEnum(Op.class);
                String key = buf.readUtf(64);
                if (op != Op.UPSERT) {
                    return new C2SGuildWaypointPacket(op, key, "", "", 0, 0, 0, "", "", "");
                }
                String name = buf.readUtf(48);
                String dimension = buf.readUtf(128);
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                String color = buf.readUtf(8);
                String icon = buf.readUtf(32);
                String category = buf.readUtf(32);
                return new C2SGuildWaypointPacket(op, key, name, dimension, x, y, z, color, icon, category);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static C2SGuildWaypointPacket upsert(String key, String name, String dimension, int x, int y, int z,
                                                String color, String icon, String category) {
        return new C2SGuildWaypointPacket(
                Op.UPSERT,
                cut(key, 64),
                cut(name, 48),
                cut(dimension, 128),
                x, y, z,
                cut(color, 8),
                cut(icon, 32),
                cut(category, 32));
    }

    public static C2SGuildWaypointPacket remove(String key) {
        return new C2SGuildWaypointPacket(Op.REMOVE, cut(key, 64), "", "", 0, 0, 0, "", "", "");
    }

    private static String cut(String s, int max) {
        String t = s == null ? "" : s;
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static void handle(final C2SGuildWaypointPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null) return;

            if (packet.op() == Op.REMOVE) {
                GuildWaypointService.remove(sender, packet.key());
            } else {
                GuildWaypointService.upsert(
                        sender, packet.key(), packet.name(), packet.dimension(),
                        packet.x(), packet.y(), packet.z(),
                        packet.color(), packet.icon(), packet.category());
            }
        });
    }
}
