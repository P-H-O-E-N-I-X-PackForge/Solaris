package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;

/** Client-only map opening, kept out of SolarisAPI so scripting on a dedicated server never loads client classes. */
public final class SolarisMapOpener {

    private SolarisMapOpener() {}

    public static boolean openMap(Screen previousScreen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        ResourceLocation dimension = mc.level.dimension().location();
        boolean gatesOpen = SolarisAPI.isFeatureEnabled(SolarisAPI.FEATURE_FULLSCREEN_MAP, dimension) &&
                SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WORLD_MAP, dimension)
                        .atLeast(SolarisFeatureState.VISIBLE);
        if (!gatesOpen) return false;

        mc.setScreen(new SolarisMapScreen(previousScreen));
        return true;
    }

    public static boolean closeMap() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof SolarisMapScreen)) return false;

        mc.setScreen(null);
        return true;
    }
}
