package net.phoenixvine.solaris.network.packets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

public record S2CStructureDiscoveredPacket(
                                           String structureName,
                                           String dimension,
                                           int x,
                                           int y,
                                           int z)
        implements CustomPacketPayload {

    private static final String STRUCTURE_COLOR = "FFC880";
    private static final String STRUCTURE_ICON = "HOME";

    public static final CustomPacketPayload.Type<S2CStructureDiscoveredPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PhoenixSolaris.MOD_ID, "s2c_structure_discovered"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CStructureDiscoveredPacket> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.stringUtf8(64), S2CStructureDiscoveredPacket::structureName,
                    ByteBufCodecs.stringUtf8(128), S2CStructureDiscoveredPacket::dimension,
                    ByteBufCodecs.VAR_INT, S2CStructureDiscoveredPacket::x,
                    ByteBufCodecs.VAR_INT, S2CStructureDiscoveredPacket::y,
                    ByteBufCodecs.VAR_INT, S2CStructureDiscoveredPacket::z,
                    S2CStructureDiscoveredPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CStructureDiscoveredPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!SolarisConfig.AUTO_STRUCTURE_WAYPOINTS.get()) return;

            Waypoint w = new Waypoint(
                    packet.structureName(),
                    ResourceLocation.parse(packet.dimension()),
                    packet.x(),
                    packet.y(),
                    packet.z(),
                    STRUCTURE_COLOR);
            w.icon = STRUCTURE_ICON;
            w.category = "Structures";
            WaypointManager.add(w);

            mc.player.sendSystemMessage(
                    Component.literal("Discovered: " + packet.structureName()).withStyle(ChatFormatting.GOLD));
        });
    }
}
