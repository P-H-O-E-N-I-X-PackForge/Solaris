package net.phoenixvine.solaris.api.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.phoenixvine.solaris.client.waypoint.Waypoint;

public class WaypointEvent extends Event {

    private final Waypoint waypoint;

    protected WaypointEvent(Waypoint waypoint) {
        this.waypoint = waypoint;
    }

    public Waypoint getWaypoint() {
        return waypoint;
    }

    public static class Added extends WaypointEvent {

        public Added(Waypoint waypoint) {
            super(waypoint);
        }
    }

    // Implement ICancellableEvent for events that can be canceled
    public static class Removed extends WaypointEvent implements ICancellableEvent {

        public Removed(Waypoint waypoint) {
            super(waypoint);
        }
    }

    public static class Reached extends WaypointEvent {

        private final Player player;

        public Reached(Player player, Waypoint waypoint) {
            super(waypoint);
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }
    }
}
