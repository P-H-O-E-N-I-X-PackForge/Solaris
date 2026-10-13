package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.player.RemotePlayerTracker;

import java.util.List;
import java.util.UUID;

public record S2CPlayerPositionsPacket(
                                       ResourceLocation dimension,
                                       List<Entry> entries)
        implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 512;

    public record Entry(UUID id, String name, int x, int y, int z, float yaw) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, entry) -> {
                    buf.writeUUID(entry.id());
                    buf.writeUtf(entry.name(), 16);
                    buf.writeInt(entry.x());
                    buf.writeShort(entry.y());
                    buf.writeInt(entry.z());
                    buf.writeByte(Math.round(entry.yaw() * 256f / 360f));
                },
                buf -> {
                    UUID id = buf.readUUID();
                    String name = buf.readUtf(16);
                    int x = buf.readInt();
                    int y = buf.readShort();
                    int z = buf.readInt();
                    float yaw = buf.readByte() * 360f / 256f;
                    return new Entry(id, name, x, y, z, yaw);
                });
    }

    public static final CustomPacketPayload.Type<S2CPlayerPositionsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_player_positions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPlayerPositionsPacket> STREAM_CODEC = StreamCodec
            .composite(
                    ResourceLocation.STREAM_CODEC, S2CPlayerPositionsPacket::dimension,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), S2CPlayerPositionsPacket::entries,
                    S2CPlayerPositionsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CPlayerPositionsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            RemotePlayerTracker.apply(packet.dimension(), packet.entries());
        });
    }
}
