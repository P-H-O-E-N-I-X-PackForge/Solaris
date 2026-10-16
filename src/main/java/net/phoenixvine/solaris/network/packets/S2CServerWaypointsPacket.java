package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.network.ClientPacketHandlers;

import java.util.List;

public record S2CServerWaypointsPacket(List<S2CServerWaypointsPacket.Entry> entries) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 512;

    public static final CustomPacketPayload.Type<S2CServerWaypointsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_server_waypoints"));

    public record Entry(
                        String key,
                        String name,
                        String dimension,
                        int x,
                        int y,
                        int z,
                        String color,
                        String icon,
                        String category) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, entry) -> {
                    buf.writeUtf(entry.key(), 64);
                    buf.writeUtf(entry.name(), 48);
                    buf.writeUtf(entry.dimension(), 128);
                    buf.writeInt(entry.x());
                    buf.writeInt(entry.y());
                    buf.writeInt(entry.z());
                    buf.writeUtf(entry.color(), 8);
                    buf.writeUtf(entry.icon(), 32);
                    buf.writeUtf(entry.category(), 32);
                },
                buf -> new Entry(
                        buf.readUtf(64),
                        buf.readUtf(48),
                        buf.readUtf(128),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readUtf(8),
                        buf.readUtf(32),
                        buf.readUtf(32)));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CServerWaypointsPacket> STREAM_CODEC = StreamCodec
            .composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    S2CServerWaypointsPacket::entries,
                    S2CServerWaypointsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CServerWaypointsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            
            ClientPacketHandlers.serverWaypoints(packet.entries());
        });
    }
}
