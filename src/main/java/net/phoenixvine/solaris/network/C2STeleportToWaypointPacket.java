package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.server.waypoint.WaypointTeleportService;

import java.util.function.Supplier;

/**
 * Asks the server to teleport the sender to a waypoint. For SERVER and GUILD waypoints the server looks the
 * waypoint up by key and ignores the coordinates; for PERSONAL waypoints (which only exist on the client) the
 * coordinates are used as sent, so the server treats them as untrusted and applies its own rules.
 */
public record C2STeleportToWaypointPacket(Source source, String key, String name, String dimension, int x, int y,
                                          int z) {

    public enum Source {
        PERSONAL,
        SERVER,
        GUILD
    }

    public C2STeleportToWaypointPacket(FriendlyByteBuf buf) {
        this(buf.readEnum(Source.class), buf.readUtf(64), buf.readUtf(48), buf.readUtf(128), buf.readInt(),
                buf.readInt(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(source);
        buf.writeUtf(key, 64);
        buf.writeUtf(name, 48);
        buf.writeUtf(dimension, 128);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            WaypointTeleportService.handle(sender, this);
        });
        ctx.get().setPacketHandled(true);
    }
}
