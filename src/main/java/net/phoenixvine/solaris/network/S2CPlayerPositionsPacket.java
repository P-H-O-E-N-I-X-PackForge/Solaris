package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.client.player.RemotePlayerTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CPlayerPositionsPacket {

    public static final int MAX_ENTRIES = 512;

    public record Entry(UUID id, String name, int x, int y, int z, float yaw) {}

    private final ResourceLocation dimension;
    private final List<Entry> entries;

    public S2CPlayerPositionsPacket(ResourceLocation dimension, List<Entry> entries) {
        this.dimension = dimension;
        this.entries = entries;
    }

    public S2CPlayerPositionsPacket(FriendlyByteBuf buf) {
        dimension = buf.readResourceLocation();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Too many player entries: " + count);
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(16);
            int x = buf.readInt();
            int y = buf.readShort();
            int z = buf.readInt();
            float yaw = buf.readByte() * 360f / 256f;
            entries.add(new Entry(id, name, x, y, z, yaw));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry e = entries.get(i);
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 16);
            buf.writeInt(e.x());
            buf.writeShort(e.y());
            buf.writeInt(e.z());
            buf.writeByte(Math.round(e.yaw() * 256f / 360f));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ResourceLocation dim = dimension;
        List<Entry> snapshot = entries;
        ctx.get().enqueueWork(
                () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemotePlayerTracker.apply(dim, snapshot)));
        ctx.get().setPacketHandled(true);
    }
}
