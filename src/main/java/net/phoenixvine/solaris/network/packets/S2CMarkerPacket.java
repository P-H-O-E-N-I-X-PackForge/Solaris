package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

public record S2CMarkerPacket(
                              Op op,
                              String key,
                              String name,
                              String dimension,
                              int x,
                              int y,
                              int z,
                              String color,
                              String icon,
                              String category,
                              int durationTicks)
        implements CustomPacketPayload {

    public static final Type<S2CMarkerPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CMarkerPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.encode(buf),
            S2CMarkerPacket::decode);

    public enum Op {
        PUT,
        REMOVE,
        CLEAR
    }

    public static S2CMarkerPacket put(String key, String name, String dimension, int x, int y, int z, String color,
                                      String icon, String category, int durationTicks) {
        return new S2CMarkerPacket(Op.PUT, key, name, dimension, x, y, z, color, icon, category, durationTicks);
    }

    public static S2CMarkerPacket remove(String key) {
        return new S2CMarkerPacket(Op.REMOVE, key, "", "", 0, 0, 0, "", "", "", 0);
    }

    public static S2CMarkerPacket clear(String keyPrefix) {
        return new S2CMarkerPacket(Op.CLEAR, keyPrefix, "", "", 0, 0, 0, "", "", "", 0);
    }

    public static S2CMarkerPacket decode(RegistryFriendlyByteBuf buf) {
        Op op = buf.readEnum(Op.class);
        String key = buf.readUtf(64);
        if (!buf.readBoolean()) return new S2CMarkerPacket(op, key, "", "", 0, 0, 0, "", "", "", 0);
        String name = buf.readUtf(48);
        String dimension = buf.readUtf(128);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        String color = buf.readUtf(8);
        String icon = buf.readUtf(32);
        String category = buf.readUtf(32);
        int duration = buf.readVarInt();
        return new S2CMarkerPacket(op, key, name, dimension, x, y, z, color, icon, category, duration);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(op);
        buf.writeUtf(key, 64);
        boolean put = (op == Op.PUT);
        buf.writeBoolean(put);
        if (put) {
            buf.writeUtf(name, 48);
            buf.writeUtf(dimension, 128);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeUtf(color, 8);
            buf.writeUtf(icon, 32);
            buf.writeUtf(category, 32);
            buf.writeVarInt(durationTicks);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.marker(op, key, name, dimension, x, y, z, color, icon, category, durationTicks);
        });
    }
}
