package net.phoenixvine.solaris.api.guild;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

@FunctionalInterface
public interface GuildWaypointPermission {

    boolean canEdit(ServerPlayer player, UUID guildId);
}
