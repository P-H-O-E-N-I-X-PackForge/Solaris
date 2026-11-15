package net.phoenixvine.solaris.api;

import net.phoenixvine.solaris.api.teleport.WaypointTeleportPermission;

public final class SolarisTeleportAPI {

    private static volatile WaypointTeleportPermission permission = null;

    private SolarisTeleportAPI() {}

    public static void setPermission(WaypointTeleportPermission hook) {
        permission = hook;
    }

    public static WaypointTeleportPermission permission() {
        return permission;
    }
}
