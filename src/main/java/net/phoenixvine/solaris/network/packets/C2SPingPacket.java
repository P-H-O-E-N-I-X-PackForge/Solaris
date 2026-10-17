package net.phoenixvine.solaris.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.server.players.PingService;

public class C2SPingPacket implements CustomPacketPayload {

    public static final Type<C2SPingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "c2s_ping"));

    public static final StreamCodec<FriendlyByteBuf, C2SPingPacket> STREAM_CODEC = StreamCodec.ofMember(
            C2SPingPacket::encode, C2SPingPacket::new);

    private final int x;
    private final int y;
    private final int z;

    public C2SPingPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public C2SPingPacket(FriendlyByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SPingPacket msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sender) {
            PingService.handle(sender, msg.x, msg.y, msg.z);
        }
    }
}
