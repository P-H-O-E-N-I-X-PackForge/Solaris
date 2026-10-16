package net.phoenixvine.solaris.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class SolarisKeybinds {

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.solaris.map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.solaris");

    public static final KeyMapping NEW_WAYPOINT = new KeyMapping(
            "key.solaris.new_waypoint",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.solaris");

    public static final KeyMapping PING = new KeyMapping(
            "key.solaris.ping",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.solaris");

    public static final KeyMapping TOGGLE_COMPASS = new KeyMapping(
            "key.solaris.toggle_compass",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.solaris");

    public static final KeyMapping TOGGLE_MINIMAP_ROTATE = new KeyMapping(
            "key.solaris.toggle_minimap_rotate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.solaris");

    public static final KeyMapping CYCLE_MINIMAP_STYLE = new KeyMapping(
            "key.solaris.cycle_minimap_style",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.solaris");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
        event.register(NEW_WAYPOINT);
        event.register(PING);
        event.register(TOGGLE_COMPASS);
        event.register(TOGGLE_MINIMAP_ROTATE);
        event.register(CYCLE_MINIMAP_STYLE);
    }
}
