package net.phoenixvine.solaris.api.teleport;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface WaypointTeleportPermission {

    boolean canTeleport(ServerPlayer player);
}
