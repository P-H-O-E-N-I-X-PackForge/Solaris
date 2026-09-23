package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.client.render.CaveTileCache;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.MobIconOverrides;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class PhoenixSolarisClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.register(SolarisKeybinds.class);
        MobIconOverrides.loadConfig();

        modEventBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event));
        modEventBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event));
        modEventBus.addListener(PhoenixSolarisClient::clientSetup);

        PhoenixTheme.registerMod("net.phoenixvine.solaris", PhoenixSolaris.MOD_ID);

        PhoenixTheme.addChangeListener(PhoenixSolarisClient::onThemeChanged);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        SuiteHudBar.register(PhoenixSolaris.MOD_ID, SuiteHudBar.PRIORITY_SOLARIS,
                new ResourceLocation(PhoenixSolaris.MOD_ID, "textures/gui/suite_bar_icon.png"),
                Component.literal("§fOpen Map"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    Screen current = mc.screen;
                    mc.setScreen(null);
                    SolarisAPI.openMap(current);
                });
    }

    private static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SolarisConfig.SPEC) return;
        SolarisTexture.invalidateAll();
        MapTileCache.clearAll();
        CaveTileCache.clearAll();
    }

    private static void onThemeChanged() {
        SolarisThemeUtils.refreshCache();
        SolarisTexture.invalidateAll();
        MapTileCache.clearAll();
        CaveTileCache.clearAll();
    }
}
