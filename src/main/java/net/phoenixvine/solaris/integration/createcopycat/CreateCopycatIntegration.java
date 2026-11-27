package net.phoenixvine.solaris.integration.createcopycat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.lang.reflect.Method;

/**
 * Create's own copycat blocks (copycat_step, copycat_panel, ...). Their model is built at render time from the
 * stored material, so the block has no texture of its own to average; the map has to look at the material instead.
 * Copycats+ blocks go through {@code CopycatsIntegration}. Reflection because Create's class drags in Ponder
 * types we don't compile against.
 */
public final class CreateCopycatIntegration {

    public static final String MOD_ID = "create";
    private static final String BLOCK_ENTITY_CLASS = "com.simibubi.create.content.decoration.copycat.CopycatBlockEntity";

    private static boolean broken = false;
    private static Class<?> copycatBlockEntityClass;
    private static Method hasCustomMaterialMethod;
    private static Method getMaterialMethod;

    private CreateCopycatIntegration() {}

    public static boolean isAvailable() {
        return !broken && ModList.get().isLoaded(MOD_ID);
    }

    public static BlockState getMaterialState(Level level, BlockPos pos) {
        if (broken) return null;
        try {
            ensureReflectionReady();
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity == null || !copycatBlockEntityClass.isInstance(entity)) return null;
            if (!(boolean) hasCustomMaterialMethod.invoke(entity)) return null;
            return (BlockState) getMaterialMethod.invoke(entity);
        } catch (Throwable t) {
            broken = true;
            PhoenixSolaris.LOGGER.error(
                    "Create is present but reading a copycat's material failed. Create copycats will use their own " +
                            "default color for the rest of this session.",
                    t);
            return null;
        }
    }

    private static void ensureReflectionReady() throws ReflectiveOperationException {
        if (copycatBlockEntityClass != null) return;
        copycatBlockEntityClass = Class.forName(BLOCK_ENTITY_CLASS);
        hasCustomMaterialMethod = copycatBlockEntityClass.getMethod("hasCustomMaterial");
        getMaterialMethod = copycatBlockEntityClass.getMethod("getMaterial");
    }
}
