package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

import java.util.ArrayList;
import java.util.List;

public record S2CClaimsPacket(
                              String dimension,
                              int centerX,
                              int centerZ,
                              int radius,
                              List<Owner> owners)
        implements CustomPacketPayload {

    public static final int MAX_OWNERS = 256;

    public record Owner(String name, int argb, long[] chunks) {}

    public static final CustomPacketPayload.Type<S2CClaimsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_claims"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CClaimsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.dimension(), 128);
                buf.writeInt(packet.centerX());
                buf.writeInt(packet.centerZ());
                buf.writeVarInt(packet.radius());
                int count = Math.min(packet.owners().size(), MAX_OWNERS);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    Owner o = packet.owners().get(i);
                    buf.writeUtf(o.name(), 32);
                    buf.writeInt(o.argb());
                    buf.writeLongArray(o.chunks());
                }
            },
            buf -> {
                String dim = buf.readUtf(128);
                int cx = buf.readInt();
                int cz = buf.readInt();
                int r = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_OWNERS) {
                    throw new IllegalArgumentException("Too many claim owners: " + count);
                }
                List<Owner> ownerList = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ownerList.add(new Owner(
                            buf.readUtf(32),
                            buf.readInt(),
                            buf.readLongArray()));
                }
                return new S2CClaimsPacket(dim, cx, cz, r, ownerList);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CClaimsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.claims(
                    packet.dimension(),
                    packet.centerX(),
                    packet.centerZ(),
                    packet.radius(),
                    packet.owners());
        });
    }
}
