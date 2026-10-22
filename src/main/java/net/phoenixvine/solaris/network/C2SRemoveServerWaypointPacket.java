package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.server.waypoint.ServerWaypointService;

import java.util.function.Supplier;

public class C2SRemoveServerWaypointPacket {

    private final String key;

    public C2SRemoveServerWaypointPacket(String key) {
        this.key = key.length() > 64 ? key.substring(0, 64) : key;
    }

    public C2SRemoveServerWaypointPacket(FriendlyByteBuf buf) {
        this.key = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(key, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || sender.getServer() == null) return;
            if (!sender.hasPermissions(2)) {
                sender.displayClientMessage(Component.literal("Only operators can delete server waypoints."), true);
                return;
            }
            ServerWaypointService.remove(sender.getServer(), key);
        });
        ctx.get().setPacketHandled(true);
    }
}
