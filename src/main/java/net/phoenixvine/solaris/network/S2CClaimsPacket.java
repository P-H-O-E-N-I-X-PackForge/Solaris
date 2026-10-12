package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CClaimsPacket {

    public static final int MAX_OWNERS = 256;

    public record Owner(String name, int argb, long[] chunks) {}

    private final String dimension;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final List<Owner> owners;

    public S2CClaimsPacket(String dimension, int centerX, int centerZ, int radius, List<Owner> owners) {
        this.dimension = dimension;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.owners = owners;
    }

    public S2CClaimsPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(128);
        this.centerX = buf.readInt();
        this.centerZ = buf.readInt();
        this.radius = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_OWNERS) throw new IllegalArgumentException("Too many claim owners: " + count);
        this.owners = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            owners.add(new Owner(buf.readUtf(32), buf.readInt(), buf.readLongArray()));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 128);
        buf.writeInt(centerX);
        buf.writeInt(centerZ);
        buf.writeVarInt(radius);
        int count = Math.min(owners.size(), MAX_OWNERS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Owner o = owners.get(i);
            buf.writeUtf(o.name(), 32);
            buf.writeInt(o.argb());
            buf.writeLongArray(o.chunks());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        String dim = dimension;
        int cx = centerX;
        int cz = centerZ;
        int r = radius;
        List<Owner> list = owners;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.claims(dim, cx, cz, r, list)));
        ctx.get().setPacketHandled(true);
    }
}
