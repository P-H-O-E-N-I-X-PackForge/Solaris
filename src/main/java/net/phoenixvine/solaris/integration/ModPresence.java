package net.phoenixvine.solaris.integration;

import net.neoforged.fml.ModList;

/** Loader-specific "is this mod loaded" check, kept in one place for the marker integrations. */
public final class ModPresence {

    private ModPresence() {}

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
