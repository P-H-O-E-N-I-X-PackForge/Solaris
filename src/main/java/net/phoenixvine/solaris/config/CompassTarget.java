package net.phoenixvine.solaris.config;

public enum CompassTarget {
    /** The explicitly tracked waypoint, or the nearest one if none is tracked. */
    TRACKED,
    NEAREST,
    /** The most recently added waypoint in the current dimension. */
    NEWEST
}
