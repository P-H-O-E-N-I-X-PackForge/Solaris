package net.phoenixvine.solaris.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CServerCapabilitiesPacket {

    public static final int DEATH_MARKERS = 1;
    
    public static final int SHARED_EXPLORATION = 2;
    
    public static final int SHARED_VEINS = 4;
    
    public static final int PLAYER_POSITIONS = 8;

    private final int flags;

    public S2CServerCapabilitiesPacket(int flags) {
        this.flags = flags;
    }

    public S2CServerCapabilitiesPacket(FriendlyByteBuf buf) {
        this.flags = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(flags);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        int f = flags;
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.serverCapabilities(f)));
        ctx.get().setPacketHandled(true);
    }
}
