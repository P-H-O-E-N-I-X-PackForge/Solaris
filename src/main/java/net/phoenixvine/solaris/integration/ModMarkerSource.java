package net.phoenixvine.solaris.integration;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** A third-party mod whose locations are mirrored onto the Solaris map as markers. */
public interface ModMarkerSource {

    boolean isAvailable();

    void sync(ServerPlayer player);

    void reset(UUID player);
}
