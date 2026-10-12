package net.phoenixvine.solaris.api.claims;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface ClaimProvider {

    void collect(ServerPlayer viewer, ResourceLocation dimension, int minChunkX, int minChunkZ, int maxChunkX,
                 int maxChunkZ, ClaimSink sink);
}
