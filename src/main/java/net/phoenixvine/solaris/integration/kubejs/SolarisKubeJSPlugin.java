package net.phoenixvine.solaris.integration.kubejs;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.api.event.WaypointEvent;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.render.UnexploredStyle;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.config.SolarisConfig;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class SolarisKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow(SolarisAPI.class);
        filter.allow(SolarisAPI.ScriptOverlay.class);
        filter.allow(SolarisFeatureState.class);
        filter.allow(UnexploredStyle.class);
        filter.allow(Waypoint.class);
        filter.allow(SolarisOverlay.class);

        filter.allow(WaypointEvent.class);
        filter.allow(WaypointEvent.Added.class);
        filter.allow(WaypointEvent.Removed.class);
        filter.allow(WaypointEvent.Reached.class);

        filter.allow(SolarisConfig.class);
        filter.allow(ModConfigSpec.ConfigValue.class);
        filter.allow(ModConfigSpec.BooleanValue.class);
        filter.allow(ModConfigSpec.IntValue.class);
        filter.allow(ModConfigSpec.DoubleValue.class);
        filter.allow(ModConfigSpec.EnumValue.class);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("SolarisAPI", SolarisAPI.class);
        registry.add("SolarisFeatureState", SolarisFeatureState.class);
        registry.add("UnexploredStyle", UnexploredStyle.class);
        registry.add("SolarisConfig", SolarisConfig.class);
    }
}
