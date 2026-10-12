package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.server.SharedVeinService;
import net.phoenixvine.solaris.sharing.SharedVeinEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SSharedVeinsPacket {

    public static final int MAX_ENTRIES = 64;

    private final List<SharedVeinEntry> entries;

    public C2SSharedVeinsPacket(List<SharedVeinEntry> entries) {
        this.entries = entries;
    }

    public C2SSharedVeinsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Too many veins: " + count);
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new SharedVeinEntry(buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readUtf(96), buf.readInt(), buf.readUtf(128)));
        }
    }

    public void encode(FriendlyByteBuf buf) {
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
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) SharedVeinService.handleUpload(sender, entries);
        });
        ctx.get().setPacketHandled(true);
    }
}
