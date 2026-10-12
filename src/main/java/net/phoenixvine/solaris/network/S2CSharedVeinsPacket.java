package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.client.network.ClientPacketHandlers;
import net.phoenixvine.solaris.sharing.SharedVeinEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CSharedVeinsPacket {

    public static final int FIRST = 1;
    public static final int LAST = 2;
    public static final int MAX_ENTRIES = 128;

    private final int flags;
    private final List<SharedVeinEntry> entries;

    public S2CSharedVeinsPacket(int flags, List<SharedVeinEntry> entries) {
        this.flags = flags;
        this.entries = entries;
    }

    public S2CSharedVeinsPacket(FriendlyByteBuf buf) {
        this.flags = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Too many veins: " + count);
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new SharedVeinEntry(buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readUtf(96), buf.readInt(), buf.readUtf(128)));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(flags);
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            SharedVeinEntry e = entries.get(i);
            buf.writeUtf(e.dimension(), 128);
            buf.writeInt(e.x());
            buf.writeInt(e.y());
            buf.writeInt(e.z());
            buf.writeUtf(e.name(), 96);
            buf.writeInt(e.argb());
            buf.writeUtf(e.itemId(), 128);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        int f = flags;
        List<SharedVeinEntry> list = entries;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.sharedVeins(f, list)));
        ctx.get().setPacketHandled(true);
    }
}
