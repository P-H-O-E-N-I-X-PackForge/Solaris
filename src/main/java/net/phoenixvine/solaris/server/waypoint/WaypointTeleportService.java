package net.phoenixvine.solaris.server.waypoint;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.api.SolarisTeleportAPI;
import net.phoenixvine.solaris.api.teleport.WaypointTeleportPermission;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.packets.C2STeleportToWaypointPacket;
import net.phoenixvine.solaris.network.packets.S2CServerCapabilitiesPacket;
import net.phoenixvine.solaris.server.CapabilityService;
import net.phoenixvine.solaris.server.SolarisServerAPI;
import net.phoenixvine.solaris.server.SolarisServerUtil;
import net.phoenixvine.solaris.server.guild.GuildWaypointData;
import net.phoenixvine.solaris.server.guild.GuildWaypointService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of "teleport to waypoint". Every request is re-validated here (enabled, permission, feature state,
 * source rules, dimension rules, distance, cooldown, safe landing); the client's capability flags only decide
 * whether the button is shown.
 */
@EventBusSubscriber()
public final class WaypointTeleportService {

    private static final int RECHECK_TICKS = 40;
    private static final int SAFE_SEARCH_RANGE = 16;

    private static final Map<UUID, Integer> LAST_USE_TICK = new HashMap<>();
    private static final Map<UUID, Integer> LAST_FLAGS = new HashMap<>();
    private static int counter = 0;

    private WaypointTeleportService() {}

    public static void forget(UUID player) {
        LAST_USE_TICK.remove(player);
        LAST_FLAGS.remove(player);
    }

    /* ---------------------------------------------------------------- permission */

    private static boolean hasPermission(ServerPlayer player) {
        if (player.hasPermissions(SolarisServerConfig.TELEPORT_PERMISSION_LEVEL.get())) return true;
        WaypointTeleportPermission hook = SolarisTeleportAPI.permission();
        if (hook == null) return false;
        try {
            return hook.canTeleport(player);
        } catch (Exception e) {
            PhoenixSolaris.LOGGER.warn("[Solaris] Waypoint teleport permission hook threw; denying", e);
            return false;
        }
    }

    /** Whether this player may use waypoint teleporting right now (before looking at a specific waypoint). */
    public static boolean canUse(ServerPlayer player) {
        if (!SolarisServerConfig.SPEC.isLoaded() || !SolarisServerConfig.TELEPORT_ENABLED.get()) return false;
        if (SolarisServerConfig.TELEPORT_REQUIRE_PERMISSION.get() && !hasPermission(player)) return false;

        if (!SolarisServerAPI.isFeatureEnabled(player, SolarisAPI.FEATURE_WAYPOINT_TELEPORT)) return false;
        ResourceLocation dimension = player.level().dimension().location();
        return SolarisServerAPI.getFeatureState(player, SolarisAPI.FEATURE_WAYPOINT_TELEPORT, dimension)
                .atLeast(SolarisFeatureState.ENABLED);
    }

    /** The teleport bits of the capability flags sent to this player's client. */
    public static int capabilityFlags(ServerPlayer player) {
        if (!canUse(player)) return 0;
        int flags = S2CServerCapabilitiesPacket.WAYPOINT_TELEPORT;
        if (SolarisServerConfig.TELEPORT_ALLOW_PERSONAL.get()) {
            flags |= S2CServerCapabilitiesPacket.WAYPOINT_TELEPORT_PERSONAL;
        }
        if (SolarisServerConfig.TELEPORT_ALLOW_CROSS_DIMENSION.get()) {
            flags |= S2CServerCapabilitiesPacket.WAYPOINT_TELEPORT_CROSS_DIMENSION;
        }
        return flags;
    }

    public static void remember(ServerPlayer player, int teleportFlags) {
        LAST_FLAGS.put(player.getUUID(), teleportFlags);
    }

    /** Keeps the button honest when someone is op'd/de-op'd, a gate opens, or they change dimension. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++counter < RECHECK_TICKS) return;
        counter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!SolarisServerUtil.hasSolaris(player)) continue;
            int now = capabilityFlags(player);
            Integer last = LAST_FLAGS.put(player.getUUID(), now);
            if (last != null && last != now) CapabilityService.send(player);
        }
    }

    /* ---------------------------------------------------------------- handling */

    private record Target(String name, ServerLevel level, int x, int y, int z) {}

    private static void tell(ServerPlayer player, String message, boolean ok) {
        player.sendSystemMessage(Component.literal(message).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public static void handle(ServerPlayer player, C2STeleportToWaypointPacket packet) {
        MinecraftServer server = player.getServer();
        if (server == null || !SolarisServerConfig.SPEC.isLoaded()) return;

        if (!canUse(player)) {
            tell(player, "You can't teleport to waypoints.", false);
            return;
        }

        ResourceLocation fromDimension = player.level().dimension().location();
        if (blocked(fromDimension)) {
            tell(player, "Waypoint teleporting is disabled in this dimension.", false);
            return;
        }

        Target target = resolve(player, server, packet);
        if (target == null) return;

        ResourceLocation toDimension = target.level().dimension().location();
        boolean sameDimension = target.level() == player.level();

        if (blocked(toDimension)) {
            tell(player, "Waypoint teleporting to that dimension is disabled.", false);
            return;
        }
        if (!sameDimension && !SolarisServerConfig.TELEPORT_ALLOW_CROSS_DIMENSION.get()) {
            tell(player, "Teleporting between dimensions is disabled on this server.", false);
            return;
        }

        int maxDistance = SolarisServerConfig.TELEPORT_MAX_DISTANCE.get();
        if (sameDimension && maxDistance > 0 &&
                player.distanceToSqr(target.x() + 0.5, target.y(), target.z() + 0.5) >
                        (double) maxDistance * maxDistance) {
            tell(player, "That waypoint is too far away (limit " + maxDistance + " blocks).", false);
            return;
        }

        ServerLevel level = target.level();
        BlockPos requested = new BlockPos(target.x(), target.y(), target.z());
        if (target.y() < level.getMinBuildHeight() || target.y() >= level.getMaxBuildHeight() ||
                !level.getWorldBorder().isWithinBounds(requested)) {
            tell(player, "That waypoint is outside the world.", false);
            return;
        }

        int cooldownTicks = SolarisServerConfig.TELEPORT_COOLDOWN_SECONDS.get() * 20;
        int now = server.getTickCount();
        if (cooldownTicks > 0 && !player.hasPermissions(2)) {
            Integer last = LAST_USE_TICK.get(player.getUUID());
            if (last != null && now - last < cooldownTicks) {
                int seconds = (cooldownTicks - (now - last) + 19) / 20;
                tell(player, "Wait " + seconds + "s before teleporting again.", false);
                return;
            }
        }

        BlockPos safe = findSafe(level, target.x(), target.y(), target.z());
        if (safe == null) {
            tell(player, "Couldn't find a safe spot at that waypoint.", false);
            return;
        }

        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(),
                player.getXRot());
        player.fallDistance = 0.0F;
        LAST_USE_TICK.put(player.getUUID(), now);

        PhoenixSolaris.LOGGER.info("[Solaris] {} teleported to waypoint '{}' ({} {}, {}, {})",
                player.getGameProfile().getName(), target.name(), toDimension, safe.getX(), safe.getY(),
                safe.getZ());
        tell(player, "Teleported to " + target.name() + ".", true);
    }

    private static boolean blocked(ResourceLocation dimension) {
        return SolarisServerConfig.TELEPORT_BLOCKED_DIMENSIONS.get().contains(dimension.toString());
    }

    private static Target resolve(ServerPlayer player, MinecraftServer server, C2STeleportToWaypointPacket packet) {
        String name;
        String dimension;
        int x;
        int y;
        int z;

        switch (packet.source()) {
            case SERVER -> {
                ServerWaypointData.Entry entry = null;
                for (ServerWaypointData.Entry e : ServerWaypointService.all(server)) {
                    if (e.key().equals(packet.key())) {
                        entry = e;
                        break;
                    }
                }
                if (entry == null) {
                    tell(player, "That server waypoint no longer exists.", false);
                    return null;
                }
                name = entry.name();
                dimension = entry.dimension();
                x = entry.x();
                y = entry.y();
                z = entry.z();
            }
            case GUILD -> {
                if (!GuildWaypointService.inGuild(player)) {
                    tell(player, "You're not in a guild.", false);
                    return null;
                }
                UUID guild = GuildsIntegration.ownerTokenFor(player);
                GuildWaypointData.Entry entry = null;
                for (GuildWaypointData.Entry e : GuildWaypointData.get(server.overworld()).entries(guild)) {
                    if (e.key().equals(packet.key())) {
                        entry = e;
                        break;
                    }
                }
                if (entry == null) {
                    tell(player, "That guild waypoint no longer exists.", false);
                    return null;
                }
                name = entry.name();
                dimension = entry.dimension();
                x = entry.x();
                y = entry.y();
                z = entry.z();
            }
            default -> {
                if (!SolarisServerConfig.TELEPORT_ALLOW_PERSONAL.get()) {
                    tell(player, "This server only allows teleporting to server and guild waypoints.", false);
                    return null;
                }
                name = packet.name().isBlank() ? "waypoint" : packet.name();
                dimension = packet.dimension();
                x = packet.x();
                y = packet.y();
                z = packet.z();
            }
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        ServerLevel level = dimensionId == null ? null :
                server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            tell(player, "That waypoint's dimension isn't available.", false);
            return null;
        }
        return new Target(name, level, x, y, z);
    }

    /* ---------------------------------------------------------------- landing */

    /** Nearest standable spot in the waypoint's column, searching up and down from the saved Y. */
    private static BlockPos findSafe(ServerLevel level, int x, int y, int z) {
        level.getChunk(x >> 4, z >> 4);

        int min = level.getMinBuildHeight() + 1;
        int max = level.getMaxBuildHeight() - 2;
        for (int d = 0; d <= SAFE_SEARCH_RANGE; d++) {
            int up = y + d;
            if (up >= min && up <= max && isSafe(level, new BlockPos(x, up, z))) return new BlockPos(x, up, z);
            int down = y - d;
            if (d != 0 && down >= min && down <= max && isSafe(level, new BlockPos(x, down, z))) {
                return new BlockPos(x, down, z);
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        if (floor.getCollisionShape(level, below).isEmpty()) return false;
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(BlockTags.FIRE)) return false;

        AABB box = EntityType.PLAYER.getDimensions().makeBoundingBox(feet.getX() + 0.5, feet.getY(),
                feet.getZ() + 0.5);
        if (!level.noCollision(box)) return false;

        for (BlockPos p : new BlockPos[] { feet, feet.above() }) {
            if (level.getFluidState(p).is(FluidTags.LAVA)) return false;
            BlockState state = level.getBlockState(p);
            if (state.is(BlockTags.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) ||
                    state.is(Blocks.WITHER_ROSE)) {
                return false;
            }
        }
        return true;
    }
}
