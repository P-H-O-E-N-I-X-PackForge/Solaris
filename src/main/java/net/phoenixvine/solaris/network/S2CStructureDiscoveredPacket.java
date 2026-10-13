package net.phoenixvine.solaris.network;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.function.Supplier;

public class S2CStructureDiscoveredPacket {

    private static final String STRUCTURE_COLOR = "FFC880";
    private static final String STRUCTURE_ICON = "HOME";

    private final String structureName;
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;

    public S2CStructureDiscoveredPacket(String structureName, String dimension, int x, int y, int z) {
        this.structureName = structureName;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public S2CStructureDiscoveredPacket(FriendlyByteBuf buf) {
        this.structureName = buf.readUtf(64);
        this.dimension = buf.readUtf(128);
        this.x = buf.readVarInt();
        this.y = buf.readVarInt();
        this.z = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(structureName, 64);
        buf.writeUtf(dimension, 128);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyOnClient(this)));
        ctx.get().setPacketHandled(true);
    }

    private static void applyOnClient(S2CStructureDiscoveredPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!SolarisConfig.AUTO_STRUCTURE_WAYPOINTS.get()) return;

        Waypoint w = new Waypoint(pkt.structureName, new ResourceLocation(pkt.dimension), pkt.x, pkt.y, pkt.z,
                STRUCTURE_COLOR);
        w.icon = STRUCTURE_ICON;
        w.category = "Structures";
        WaypointManager.add(w);

        mc.player.sendSystemMessage(
                Component.literal("Discovered: " + pkt.structureName).withStyle(ChatFormatting.GOLD));
    }
}
