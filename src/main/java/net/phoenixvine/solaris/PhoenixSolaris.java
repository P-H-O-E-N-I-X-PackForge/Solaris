package net.phoenixvine.solaris;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;
import net.phoenixvine.solaris.client.PhoenixSolarisClient;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.network.SolarisNetwork;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixSolaris.MOD_ID)
public class PhoenixSolaris {

    public static final String MOD_ID = "solaris";
    public static final Logger LOGGER = LogManager.getLogger();

    public PhoenixSolaris() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        SolarisConfig.register();

        modEventBus.addListener(this::commonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PhoenixSolarisClient.init(modEventBus));

        // Everything that actually needs the server (structure waypoints, waypoint sharing, the
        // feature-state gating system) just silently does nothing without it — getFeatureState
        // defaults to ENABLED when no sync has ever been received, so the map/minimap/waypoints
        // all work normally. Without this, Forge's default mod-list handshake would refuse to let
        // a Solaris client even connect to a server that doesn't have Solaris installed at all,
        // which defeats the entire point of being usable as "just a client-side map mod."
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY,
                        (remote, isServer) -> true));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("Solaris initializing..."));
        SolarisNetwork.init();
    }
}
