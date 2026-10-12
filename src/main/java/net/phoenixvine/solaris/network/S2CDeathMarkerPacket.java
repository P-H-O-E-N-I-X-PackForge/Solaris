package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CDeathMarkerPacket {

    private final String dimension;
    private final int x;
    private final int y;
    private final int z;
    private final String playerName;
    private final boolean own;

    public S2CDeathMarkerPacket(String dimension, int x, int y, int z, String playerName, boolean own) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.playerName = playerName;
        this.own = own;
    }

    public S2CDeathMarkerPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(128);
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.playerName = buf.readUtf(16);
        this.own = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 128);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeUtf(playerName, 16);
        buf.writeBoolean(own);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        String dim = dimension;
        int px = x;
        int py = y;
        int pz = z;
        String name = playerName;
        boolean mine = own;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.deathMarker(dim, px, py, pz, name, mine)));
        ctx.get().setPacketHandled(true);
    }
}
