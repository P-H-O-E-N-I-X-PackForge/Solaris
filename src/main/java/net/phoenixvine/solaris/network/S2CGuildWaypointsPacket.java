package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CGuildWaypointsPacket {

    private final List<S2CServerWaypointsPacket.Entry> entries;

    public S2CGuildWaypointsPacket(List<S2CServerWaypointsPacket.Entry> entries) {
        this.entries = entries;
    }

    public S2CGuildWaypointsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > S2CServerWaypointsPacket.MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many guild waypoints: " + count);
        }
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new S2CServerWaypointsPacket.Entry(buf.readUtf(64), buf.readUtf(48), buf.readUtf(128),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(8), buf.readUtf(32), buf.readUtf(32)));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        int count = Math.min(entries.size(), S2CServerWaypointsPacket.MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            S2CServerWaypointsPacket.Entry e = entries.get(i);
            buf.writeUtf(e.key(), 64);
            buf.writeUtf(e.name(), 48);
            buf.writeUtf(e.dimension(), 128);
            buf.writeInt(e.x());
            buf.writeInt(e.y());
            buf.writeInt(e.z());
            buf.writeUtf(e.color(), 8);
            buf.writeUtf(e.icon(), 32);
            buf.writeUtf(e.category(), 32);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        List<S2CServerWaypointsPacket.Entry> snapshot = entries;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.guildWaypoints(snapshot)));
        ctx.get().setPacketHandled(true);
    }
}
