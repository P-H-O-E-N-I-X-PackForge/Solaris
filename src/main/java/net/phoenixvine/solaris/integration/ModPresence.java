package net.phoenixvine.solaris.integration;

import net.neoforged.fml.ModList;

public final class ModPresence {

    private ModPresence() {}

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
