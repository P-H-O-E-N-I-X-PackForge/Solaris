package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.common.SharedVeinEntry;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

import java.util.ArrayList;
import java.util.List;

public record S2CSharedVeinsPacket(int flags, List<SharedVeinEntry> entries) implements CustomPacketPayload {

    public static final int FIRST = 1;
    public static final int LAST = 2;
    public static final int MAX_ENTRIES = 128;

    public static final CustomPacketPayload.Type<S2CSharedVeinsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_shared_veins"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSharedVeinsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.flags());
                int count = Math.min(packet.entries().size(), MAX_ENTRIES);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    SharedVeinEntry e = packet.entries().get(i);
                    buf.writeUtf(e.dimension(), 128);
                    buf.writeInt(e.x());
                    buf.writeInt(e.y());
                    buf.writeInt(e.z());
                    buf.writeUtf(e.name(), 96);
                    buf.writeInt(e.argb());
                    buf.writeUtf(e.itemId(), 128);
                }
            },
            buf -> {
                int flagsVal = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Too many veins: " + count);
                }
                List<SharedVeinEntry> entryList = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entryList.add(new SharedVeinEntry(
                            buf.readUtf(128),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readUtf(96),
                            buf.readInt(),
                            buf.readUtf(128)));
                }
                return new S2CSharedVeinsPacket(flagsVal, entryList);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CSharedVeinsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.sharedVeins(packet.flags(), packet.entries());
        });
    }
}
