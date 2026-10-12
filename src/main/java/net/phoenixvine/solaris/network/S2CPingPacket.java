package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CPingPacket {

    private final String senderName;
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;
    private final int durationTicks;

    public S2CPingPacket(String senderName, String dimension, int x, int y, int z, int durationTicks) {
        this.senderName = senderName;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.durationTicks = durationTicks;
    }

    public S2CPingPacket(FriendlyByteBuf buf) {
        this.senderName = buf.readUtf(16);
        this.dimension = buf.readUtf(128);
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.durationTicks = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(senderName, 16);
        buf.writeUtf(dimension, 128);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeVarInt(durationTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        String sender = senderName;
        String dim = dimension;
        int px = x;
        int py = y;
        int pz = z;
        int ticks = durationTicks;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.ping(sender, dim, px, py, pz, ticks)));
        ctx.get().setPacketHandled(true);
    }
}
