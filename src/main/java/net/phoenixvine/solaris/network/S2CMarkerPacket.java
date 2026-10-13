package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CMarkerPacket {

    public enum Op {
        PUT,
        REMOVE,
        CLEAR
    }

    private final Op op;
    private final String key;
    private final String name;
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;
    private final String color;
    private final String icon;
    private final String category;
    private final int durationTicks;

    private S2CMarkerPacket(Op op, String key, String name, String dimension, int x, int y, int z, String color,
                            String icon, String category, int durationTicks) {
        this.op = op;
        this.key = key;
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.icon = icon;
        this.category = category;
        this.durationTicks = durationTicks;
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

    public S2CMarkerPacket(FriendlyByteBuf buf) {
        this.op = buf.readEnum(Op.class);
        this.key = buf.readUtf(64);
        boolean put = op == Op.PUT;
        this.name = put ? buf.readUtf(48) : "";
        this.dimension = put ? buf.readUtf(128) : "";
        this.x = put ? buf.readInt() : 0;
        this.y = put ? buf.readInt() : 0;
        this.z = put ? buf.readInt() : 0;
        this.color = put ? buf.readUtf(8) : "";
        this.icon = put ? buf.readUtf(32) : "";
        this.category = put ? buf.readUtf(32) : "";
        this.durationTicks = put ? buf.readVarInt() : 0;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(op);
        buf.writeUtf(key, 64);
        if (op != Op.PUT) return;
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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Op o = op;
        String k = key;
        String n = name;
        String d = dimension;
        int px = x;
        int py = y;
        int pz = z;
        String c = color;
        String i = icon;
        String cat = category;
        int ticks = durationTicks;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.marker(o, k, n, d, px, py, pz, c, i, cat, ticks)));
        ctx.get().setPacketHandled(true);
    }
}
