package net.phoenixvine.solaris.client.render;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-dimension backdrops for {@link UnexploredStyle#BACKDROP}. Client side. */
public final class UnexploredBackdrops {

    private static final Map<ResourceLocation, UnexploredBackdrop> BACKDROPS = new ConcurrentHashMap<>();

    private UnexploredBackdrops() {}

    public static void set(ResourceLocation dimension, UnexploredBackdrop backdrop) {
        BACKDROPS.put(dimension, backdrop);
    }

    public static void clear(ResourceLocation dimension) {
        BACKDROPS.remove(dimension);
    }

    public static @Nullable UnexploredBackdrop get(ResourceLocation dimension) {
        return BACKDROPS.get(dimension);
    }
}
