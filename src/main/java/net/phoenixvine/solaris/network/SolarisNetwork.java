package net.phoenixvine.solaris.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.phoenixvine.solaris.network.packets.*;

public class SolarisNetwork {

    private static final String PROTOCOL = "2";

    private SolarisNetwork() {}

    public static void init() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(PROTOCOL);

        r.playToServer(C2SShareWaypointPacket.TYPE, C2SShareWaypointPacket.STREAM_CODEC,
                C2SShareWaypointPacket::handle);
        r.playToServer(C2SPingPacket.TYPE, C2SPingPacket.STREAM_CODEC,
                C2SPingPacket::handle);
        r.playToServer(C2SGuildWaypointPacket.TYPE, C2SGuildWaypointPacket.STREAM_CODEC,
                C2SGuildWaypointPacket::handle);
        r.playToServer(C2SRemoveServerWaypointPacket.TYPE, C2SRemoveServerWaypointPacket.STREAM_CODEC,
                C2SRemoveServerWaypointPacket::handle);
        r.playToServer(C2SSharedChunksPacket.TYPE, C2SSharedChunksPacket.STREAM_CODEC,
                C2SSharedChunksPacket::handle);
        r.playToServer(C2SSharedSyncRequestPacket.TYPE, C2SSharedSyncRequestPacket.STREAM_CODEC,
                C2SSharedSyncRequestPacket::handle);
        r.playToServer(C2SSharedVeinsPacket.TYPE, C2SSharedVeinsPacket.STREAM_CODEC,
                C2SSharedVeinsPacket::handle);

        r.playToClient(S2CReceiveWaypointPacket.TYPE, S2CReceiveWaypointPacket.STREAM_CODEC,
                S2CReceiveWaypointPacket::handle);
        r.playToClient(S2CSyncFeatureStatePacket.TYPE, S2CSyncFeatureStatePacket.STREAM_CODEC,
                S2CSyncFeatureStatePacket::handle);
        r.playToClient(S2CForceUpdateChunkPacket.TYPE, S2CForceUpdateChunkPacket.STREAM_CODEC,
                S2CForceUpdateChunkPacket::handle);
        r.playToClient(S2CRemoveWaypointPacket.TYPE, S2CRemoveWaypointPacket.STREAM_CODEC,
                S2CRemoveWaypointPacket::handle);
        r.playToClient(S2CStructureDiscoveredPacket.TYPE, S2CStructureDiscoveredPacket.STREAM_CODEC,
                S2CStructureDiscoveredPacket::handle);
        r.playToClient(S2CPlayerPositionsPacket.TYPE, S2CPlayerPositionsPacket.STREAM_CODEC,
                S2CPlayerPositionsPacket::handle);
        r.playToClient(S2CServerCapabilitiesPacket.TYPE, S2CServerCapabilitiesPacket.STREAM_CODEC,
                S2CServerCapabilitiesPacket::handle);
        r.playToClient(S2CDeathMarkerPacket.TYPE, S2CDeathMarkerPacket.STREAM_CODEC,
                S2CDeathMarkerPacket::handle);
        r.playToClient(S2CServerWaypointsPacket.TYPE, S2CServerWaypointsPacket.STREAM_CODEC,
                S2CServerWaypointsPacket::handle);
        r.playToClient(S2CPingPacket.TYPE, S2CPingPacket.STREAM_CODEC,
                S2CPingPacket::handle);
        r.playToClient(S2CMarkerPacket.TYPE, S2CMarkerPacket.STREAM_CODEC,
                S2CMarkerPacket::handle);
        r.playToClient(S2CClaimsPacket.TYPE, S2CClaimsPacket.STREAM_CODEC,
                S2CClaimsPacket::handle);
        r.playToClient(S2CGuildWaypointsPacket.TYPE, S2CGuildWaypointsPacket.STREAM_CODEC,
                S2CGuildWaypointsPacket::handle);
        r.playToClient(S2CSharedChunksPacket.TYPE, S2CSharedChunksPacket.STREAM_CODEC,
                S2CSharedChunksPacket::handle);
        r.playToClient(S2CSharedUploadWantPacket.TYPE, S2CSharedUploadWantPacket.STREAM_CODEC,
                S2CSharedUploadWantPacket::handle);
        r.playToClient(S2CSharedVeinsPacket.TYPE, S2CSharedVeinsPacket.STREAM_CODEC,
                S2CSharedVeinsPacket::handle);
    }
}
