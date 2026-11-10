package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.server.waypoint.WaypointTeleportService;

/**
 * Asks the server to teleport the sender to a waypoint. For SERVER and GUILD waypoints the server looks the
 * waypoint up by key and ignores the coordinates; for PERSONAL waypoints (which only exist on the client) the
 * coordinates are used as sent, so the server treats them as untrusted and applies its own rules.
 */
public record C2STeleportToWaypointPacket(
                                          Source source,
                                          String key,
                                          String name,
                                          String dimension,
                                          int x,
                                          int y,
                                          int z)
        implements CustomPacketPayload {

    public enum Source {
        PERSONAL,
        SERVER,
        GUILD
    }

    public static final Type<C2STeleportToWaypointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "c2s_teleport_to_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2STeleportToWaypointPacket> STREAM_CODEC = StreamCodec
            .of(
                    (buf, packet) -> {
                        buf.writeEnum(packet.source());
                        buf.writeUtf(packet.key(), 64);
                        buf.writeUtf(packet.name(), 48);
                        buf.writeUtf(packet.dimension(), 128);
                        buf.writeInt(packet.x());
                        buf.writeInt(packet.y());
                        buf.writeInt(packet.z());
                    },
                    buf -> new C2STeleportToWaypointPacket(
                            buf.readEnum(Source.class),
                            buf.readUtf(64),
                            buf.readUtf(48),
                            buf.readUtf(128),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2STeleportToWaypointPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            WaypointTeleportService.handle(sender, packet);
        });
    }
}
