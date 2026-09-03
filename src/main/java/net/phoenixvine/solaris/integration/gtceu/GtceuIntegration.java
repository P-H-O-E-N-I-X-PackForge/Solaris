package net.phoenixvine.solaris.integration.gtceu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.util.List;

// Deliberately free of any direct or fully-qualified reference to a com.gregtechceu.gtceu.* type
// — see GtceuWaypointBridge's doc for why. isAvailable() runs unconditionally every client tick
// from the moment the game starts, on instances with and without GTCEu installed alike, so this
// class must stay loadable (and verifiable) with GTCEu completely absent. GtVeinRegistry (used
// below by getVeinsInArea) DOES reference GTCEu types directly, same as GtceuWaypointBridge does —
// but unlike this class, it's never touched by anything that runs unconditionally: it's only
// reached from SolarisMapScreen's already-isAvailable()-gated, already-try/catch(Throwable)-wrapped
// ore-vein rendering path, so loading it can't crash an instance that never gets that far.
public final class GtceuIntegration {

    public static final String GTCEU_MOD_ID = "gtceu";

    private GtceuIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(GTCEU_MOD_ID);
    }

    public static void init() {
        GtceuWaypointBridge.init();
    }

    public static List<GtOreVein> getVeinsInArea(ResourceLocation dimension, int minX, int minZ, int width,
                                                 int depth) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        return GtVeinRegistry.getInArea(dimKey, minX, minZ, width, depth);
    }

    public record GtOreVein(BlockPos center, String name, int colorArgb, ItemStack icon) {}
}
