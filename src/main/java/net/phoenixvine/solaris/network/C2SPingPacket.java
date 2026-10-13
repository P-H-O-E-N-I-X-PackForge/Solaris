package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.server.players.PingService;

import java.util.function.Supplier;

public class C2SPingPacket {

    private final int x;
    private final int y;
    private final int z;

    public C2SPingPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public C2SPingPacket(FriendlyByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) PingService.handle(sender, x, y, z);
        });
        ctx.get().setPacketHandled(true);
    }
}
