package net.phoenixvine.solaris.integration.gtceu;

import com.gregtechceu.gtceu.integration.map.WaypointManager;

import net.phoenixvine.solaris.PhoenixSolaris;

import java.lang.reflect.Field;

final class GtceuWaypointBridge {

    private static boolean initialized = false;
    private static boolean initBroken = false;

    private GtceuWaypointBridge() {}

    static void init() {
        if (initialized || initBroken) return;

        try {
            WaypointManager.registerWaypointHandler(new SolarisWaypointHandler());
            setActive(true);
            initialized = true;
        } catch (Throwable t) {
            initBroken = true;
            PhoenixSolaris.LOGGER.error(
                    "GTCEu is present but registering Solaris as a prospector waypoint target failed. " +
                            "Clicking veins in the prospector's map won't pin Solaris waypoints this session.",
                    t);
        }
    }

    private static void setActive(boolean active) throws ReflectiveOperationException {
        Field field = WaypointManager.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, active);
    }
}
