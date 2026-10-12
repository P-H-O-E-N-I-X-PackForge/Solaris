package net.phoenixvine.solaris.api.gates;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface GateCondition {

    boolean test(ServerPlayer player);
}
