package net.phoenixvine.solaris.api;

import net.phoenixvine.solaris.api.teleport.WaypointTeleportPermission;

public final class SolarisTeleportAPI {

    private static volatile WaypointTeleportPermission permission = null;

    private SolarisTeleportAPI() {}

    /** Register (or pass null to clear) a permission check for waypoint teleporting. Call on the server. */
    public static void setPermission(WaypointTeleportPermission hook) {
        permission = hook;
    }

    public static WaypointTeleportPermission permission() {
        return permission;
    }
}
