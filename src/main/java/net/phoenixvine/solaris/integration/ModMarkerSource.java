package net.phoenixvine.solaris.integration;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public interface ModMarkerSource {

    boolean isAvailable();

    void sync(ServerPlayer player);

    void reset(UUID player);
}
