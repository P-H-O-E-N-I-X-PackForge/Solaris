package net.phoenixvine.solaris.integration.gtceu;

import net.phoenixvine.solaris.PhoenixSolaris;

import java.lang.reflect.Field;

/**
 * Holds every direct reference to GTCEu's waypoint-map types, isolated into its own class so
 * that class is never loaded — and never needs verifying — unless GTCEu is already confirmed
 * present. {@link GtceuIntegration#isAvailable()} is called unconditionally every client tick
 * from the moment the game starts, regardless of whether GTCEu is installed; if these
 * gtceu-referencing members lived in {@code GtceuIntegration} itself (as {@code init()} used to),
 * simply loading that class to call {@code isAvailable()} would force the JVM to verify every
 * method on it, including this one — and bytecode verification requires resolving the interface
 * a {@code new SolarisWaypointHandler()} is checked against, throwing NoClassDefFoundError for
 * {@code IWaypointHandler} even when GTCEu isn't installed at all. That crashed the game on
 * every single tick, on instances with AND without GTCEu, since the failure happened before
 * {@code isAvailable()}'s own result was ever consulted. Only call {@link #init()} from behind
 * an {@code isAvailable()} check.
 */
final class GtceuWaypointBridge {

    private static boolean initialized = false;
    private static boolean initBroken = false;

    private GtceuWaypointBridge() {}

    static void init() {
        if (initialized || initBroken) return;
        try {
            com.gregtechceu.gtceu.integration.map.WaypointManager
                    .registerWaypointHandler(new SolarisWaypointHandler());
            Field activeField = com.gregtechceu.gtceu.integration.map.WaypointManager.class
                    .getDeclaredField("active");
            activeField.setAccessible(true);
            activeField.set(null, true);
            initialized = true;
        } catch (Throwable t) {
            initBroken = true;
            PhoenixSolaris.LOGGER.error(
                    "GTCEu is present but registering Solaris as a prospector waypoint target failed. " +
                            "Clicking veins in the prospector's map won't pin Solaris waypoints this session.",
                    t);
        }
    }
}
