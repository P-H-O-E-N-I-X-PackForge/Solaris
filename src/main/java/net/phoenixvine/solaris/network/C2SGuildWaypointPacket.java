package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.server.guild.GuildWaypointService;


import java.util.function.Supplier;

public class C2SGuildWaypointPacket {

    public enum Op { UPSERT, REMOVE }

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

    private C2SGuildWaypointPacket(Op op, String key, String name, String dimension, int x, int y, int z,
                                   String color, String icon, String category) {
        this.op = op;
        this.key = cut(key, 64);
        this.name = cut(name, 48);
        this.dimension = cut(dimension, 128);
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = cut(color, 8);
        this.icon = cut(icon, 32);
        this.category = cut(category, 32);
    }

    public static C2SGuildWaypointPacket upsert(String key, String name, String dimension, int x, int y, int z,
                                                String color, String icon, String category) {
        return new C2SGuildWaypointPacket(Op.UPSERT, key, name, dimension, x, y, z, color, icon, category);
    }

    public static C2SGuildWaypointPacket remove(String key) {
        return new C2SGuildWaypointPacket(Op.REMOVE, key, "", "", 0, 0, 0, "", "", "");
    }

    private static String cut(String s, int max) {
        String t = s == null ? "" : s;
        return t.length() > max ? t.substring(0, max) : t;
    }

    public C2SGuildWaypointPacket(FriendlyByteBuf buf) {
        this.op = buf.readEnum(Op.class);
        this.key = buf.readUtf(64);
        boolean upsert = op == Op.UPSERT;
        this.name = upsert ? buf.readUtf(48) : "";
        this.dimension = upsert ? buf.readUtf(128) : "";
        this.x = upsert ? buf.readInt() : 0;
        this.y = upsert ? buf.readInt() : 0;
        this.z = upsert ? buf.readInt() : 0;
        this.color = upsert ? buf.readUtf(8) : "";
        this.icon = upsert ? buf.readUtf(32) : "";
        this.category = upsert ? buf.readUtf(32) : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(op);
        buf.writeUtf(key, 64);
        if (op != Op.UPSERT) return;
        buf.writeUtf(name, 48);
        buf.writeUtf(dimension, 128);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeUtf(color, 8);
        buf.writeUtf(icon, 32);
        buf.writeUtf(category, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (op == Op.REMOVE) {
                GuildWaypointService.remove(sender, key);
            } else {
                GuildWaypointService.upsert(sender, key, name, dimension, x, y, z, color, icon, category);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
