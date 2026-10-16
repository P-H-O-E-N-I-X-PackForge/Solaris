package net.phoenixvine.solaris;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.phoenixvine.solaris.client.PhoenixSolarisClient;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.network.SolarisNetwork;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixSolaris.MOD_ID)
public class PhoenixSolaris {

    public static final String MOD_ID = "solaris";
    public static final Logger LOGGER = LogManager.getLogger();

    public PhoenixSolaris(IEventBus modEventBus, ModContainer modContainer) {
        
        modContainer.registerConfig(ModConfig.Type.CLIENT, SolarisConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, SolarisServerConfig.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(SolarisNetwork::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PhoenixSolarisClient.init(modEventBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("Solaris initializing..."));
        SolarisNetwork.init();
    }
}
