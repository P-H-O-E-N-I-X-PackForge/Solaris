package net.phoenixvine.solaris.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class SolarisMarkerSyncEvent extends Event {

    private final ServerPlayer player;

    public SolarisMarkerSyncEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
