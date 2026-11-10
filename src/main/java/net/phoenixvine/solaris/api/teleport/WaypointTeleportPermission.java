package net.phoenixvine.solaris.api.teleport;

import net.minecraft.server.level.ServerPlayer;

/**
 * Lets a permissions mod (LuckPerms, FTB Ranks, ...) grant waypoint teleporting to players who are not operators.
 * Only consulted when {@code waypointTeleport.requirePermission} is true. A player is allowed if they meet the
 * configured permission level OR this hook returns true, so it can only add access, never take an op's away.
 */
@FunctionalInterface
public interface WaypointTeleportPermission {

    boolean canTeleport(ServerPlayer player);
}
