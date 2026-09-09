package net.phoenixvine.solaris.mixin.gtceu;

import com.gregtechceu.gtceu.integration.map.WaypointManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WaypointManager.class)
public interface WaypointManagerAccessor {

    @Accessor(value = "active", remap = false)
    static void setActive(boolean active) {
        throw new AssertionError();
    }
}
