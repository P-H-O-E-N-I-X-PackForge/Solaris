package net.phoenixvine.solaris.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.util.Optional;

public final class SolarisNetwork {

    private static final String PROTOCOL = "2";

    public static SimpleChannel CHANNEL;

    private static int id = 0;

    private SolarisNetwork() {}

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(PhoenixSolaris.MOD_ID, "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals);

        CHANNEL.registerMessage(id++,
                C2SShareWaypointPacket.class,
                C2SShareWaypointPacket::encode,
                C2SShareWaypointPacket::new,
                C2SShareWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CReceiveWaypointPacket.class,
                S2CReceiveWaypointPacket::encode,
                S2CReceiveWaypointPacket::new,
                S2CReceiveWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CSyncFeatureStatePacket.class,
                S2CSyncFeatureStatePacket::encode,
                S2CSyncFeatureStatePacket::new,
                S2CSyncFeatureStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CForceUpdateChunkPacket.class,
                S2CForceUpdateChunkPacket::encode,
                S2CForceUpdateChunkPacket::new,
                S2CForceUpdateChunkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CRemoveWaypointPacket.class,
                S2CRemoveWaypointPacket::encode,
                S2CRemoveWaypointPacket::new,
                S2CRemoveWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CStructureDiscoveredPacket.class,
                S2CStructureDiscoveredPacket::encode,
                S2CStructureDiscoveredPacket::new,
                S2CStructureDiscoveredPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CPlayerPositionsPacket.class,
                S2CPlayerPositionsPacket::encode,
                S2CPlayerPositionsPacket::new,
                S2CPlayerPositionsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CServerCapabilitiesPacket.class,
                S2CServerCapabilitiesPacket::encode,
                S2CServerCapabilitiesPacket::new,
                S2CServerCapabilitiesPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CDeathMarkerPacket.class,
                S2CDeathMarkerPacket::encode,
                S2CDeathMarkerPacket::new,
                S2CDeathMarkerPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CServerWaypointsPacket.class,
                S2CServerWaypointsPacket::encode,
                S2CServerWaypointsPacket::new,
                S2CServerWaypointsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2SPingPacket.class,
                C2SPingPacket::encode,
                C2SPingPacket::new,
                C2SPingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CPingPacket.class,
                S2CPingPacket::encode,
                S2CPingPacket::new,
                S2CPingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CMarkerPacket.class,
                S2CMarkerPacket::encode,
                S2CMarkerPacket::new,
                S2CMarkerPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CClaimsPacket.class,
                S2CClaimsPacket::encode,
                S2CClaimsPacket::new,
                S2CClaimsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CGuildWaypointsPacket.class,
                S2CGuildWaypointsPacket::encode,
                S2CGuildWaypointsPacket::new,
                S2CGuildWaypointsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2SGuildWaypointPacket.class,
                C2SGuildWaypointPacket::encode,
                C2SGuildWaypointPacket::new,
                C2SGuildWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                C2SSharedChunksPacket.class,
                C2SSharedChunksPacket::encode,
                C2SSharedChunksPacket::new,
                C2SSharedChunksPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CSharedChunksPacket.class,
                S2CSharedChunksPacket::encode,
                S2CSharedChunksPacket::new,
                S2CSharedChunksPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2SSharedSyncRequestPacket.class,
                C2SSharedSyncRequestPacket::encode,
                C2SSharedSyncRequestPacket::new,
                C2SSharedSyncRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CSharedUploadWantPacket.class,
                S2CSharedUploadWantPacket::encode,
                S2CSharedUploadWantPacket::new,
                S2CSharedUploadWantPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2SSharedVeinsPacket.class,
                C2SSharedVeinsPacket::encode,
                C2SSharedVeinsPacket::new,
                C2SSharedVeinsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CSharedVeinsPacket.class,
                S2CSharedVeinsPacket::encode,
                S2CSharedVeinsPacket::new,
                S2CSharedVeinsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2SRemoveServerWaypointPacket.class,
                C2SRemoveServerWaypointPacket::encode,
                C2SRemoveServerWaypointPacket::new,
                C2SRemoveServerWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
