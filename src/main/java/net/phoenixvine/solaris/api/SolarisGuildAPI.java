package net.phoenixvine.solaris.api;

import net.phoenixvine.solaris.api.guild.GuildWaypointPermission;

public final class SolarisGuildAPI {

    private static volatile GuildWaypointPermission waypointPermission = null;

    private SolarisGuildAPI() {}

    public static void setWaypointPermission(GuildWaypointPermission permission) {
        waypointPermission = permission;
    }

    public static GuildWaypointPermission waypointPermission() {
        return waypointPermission;
    }
}
