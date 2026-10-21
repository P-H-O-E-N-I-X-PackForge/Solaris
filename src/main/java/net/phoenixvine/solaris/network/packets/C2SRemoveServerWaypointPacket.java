package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.server.waypoint.ServerWaypointService;

/** An operator asks the server to delete one of the server-wide waypoints. */
public record C2SRemoveServerWaypointPacket(String key) implements CustomPacketPayload {

    public static final Type<C2SRemoveServerWaypointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "c2s_remove_server_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRemoveServerWaypointPacket> STREAM_CODEC = StreamCodec
            .of((buf, packet) -> buf.writeUtf(packet.key(), 64),
                    buf -> new C2SRemoveServerWaypointPacket(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SRemoveServerWaypointPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null || sender.getServer() == null) return;
            if (!sender.hasPermissions(2)) {
                sender.displayClientMessage(Component.literal("Only operators can delete server waypoints."), true);
                return;
            }
            ServerWaypointService.remove(sender.getServer(), packet.key());
        });
    }
}
