package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.common.SharedVeinEntry;
import net.phoenixvine.solaris.server.exploration.SharedVeinService;

import java.util.ArrayList;
import java.util.List;

public record C2SSharedVeinsPacket(List<SharedVeinEntry> entries) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 64;

    public static final CustomPacketPayload.Type<C2SSharedVeinsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "c2s_shared_veins"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSharedVeinsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
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
                return new C2SSharedVeinsPacket(entryList);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SSharedVeinsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                SharedVeinService.handleUpload(sender, packet.entries());
            }
        });
    }
}
