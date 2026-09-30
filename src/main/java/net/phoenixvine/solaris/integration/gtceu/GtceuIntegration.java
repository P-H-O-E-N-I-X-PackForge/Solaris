package net.phoenixvine.solaris.integration.gtceu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.util.List;

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
