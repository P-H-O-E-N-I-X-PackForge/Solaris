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
        migrateLegacyConfigFiles();
        modContainer.registerConfig(ModConfig.Type.CLIENT, SolarisConfig.SPEC, "solaris/solaris-client.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, SolarisServerConfig.SPEC, "solaris/solaris-server.toml");

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(SolarisNetwork::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PhoenixSolarisClient.init(modEventBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void migrateLegacyConfigFiles() {
        try {
            java.nio.file.Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            java.nio.file.Path target = configDir.resolve("solaris");
            java.nio.file.Files.createDirectories(target);
            for (String name : new String[] { "solaris-client.toml", "solaris-server.toml",
                    "solaris_mob_icons.json" }) {
                java.nio.file.Path old = configDir.resolve(name);
                java.nio.file.Path dest = target.resolve(name);
                if (java.nio.file.Files.exists(old) && !java.nio.file.Files.exists(dest)) {
                    java.nio.file.Files.move(old, dest);
                    LOGGER.info("[Solaris] Moved legacy config {} -> config/solaris/{}", name, name);
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            LOGGER.warn("[Solaris] Couldn't migrate legacy config files into config/solaris", e);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("Solaris initializing..."));
        SolarisNetwork.init();
    }
}
