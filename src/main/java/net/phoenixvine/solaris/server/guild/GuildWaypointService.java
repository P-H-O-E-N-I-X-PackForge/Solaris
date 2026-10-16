package net.phoenixvine.solaris.server.guild;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.api.SolarisGuildAPI;
import net.phoenixvine.solaris.api.guild.GuildWaypointPermission;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.packets.S2CGuildWaypointsPacket;
import net.phoenixvine.solaris.network.packets.S2CServerWaypointsPacket;
import net.phoenixvine.solaris.server.*;
import net.phoenixvine.solaris.server.exploration.SharedVeinService;
import net.phoenixvine.solaris.server.waypoint.ServerWaypointService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber()
public final class GuildWaypointService {

    private static final int MEMBERSHIP_CHECK_TICKS = 40;

    private static int counter = 0;
    private static final Map<UUID, UUID> LAST_TOKEN = new HashMap<>();

    private GuildWaypointService() {}

    public static void forget(UUID player) {
        LAST_TOKEN.remove(player);
    }

    public static boolean inGuild(ServerPlayer player) {
        return GuildsIntegration.isAvailable() && !GuildsIntegration.ownerTokenFor(player).equals(player.getUUID());
    }

    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !SolarisServerUtil.hasSolaris(player)) return;

        UUID token = GuildsIntegration.ownerTokenFor(player);
        LAST_TOKEN.put(player.getUUID(), token);

        List<S2CServerWaypointsPacket.Entry> out = new ArrayList<>();
        if (inGuild(player)) {
            for (GuildWaypointData.Entry e : GuildWaypointData.get(server.overworld()).entries(token)) {
                out.add(new S2CServerWaypointsPacket.Entry(e.key(), e.name(), e.dimension(), e.x(), e.y(), e.z(),
                        e.color(), e.icon(), e.category()));
            }
        }
        
        PacketDistributor.sendToPlayer(player, new S2CGuildWaypointsPacket(out));
    }

    private static void broadcast(ServerPlayer anyMember) {
        sendTo(anyMember);
        for (ServerPlayer mate : GuildsIntegration.getOnlineGuildmates(anyMember)) sendTo(mate);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!GuildsIntegration.isAvailable()) return;
        if (++counter < MEMBERSHIP_CHECK_TICKS) return;
        counter = 0;

        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID token = GuildsIntegration.ownerTokenFor(player);
            UUID last = LAST_TOKEN.get(player.getUUID());
            if (last != null && !last.equals(token)) {
                PhoenixSolaris.LOGGER.info("[Solaris] {} changed guild ({} -> {}); resyncing guild data",
                        player.getGameProfile().getName(), last, token);
                sendTo(player);
                CapabilityService.send(player);
                SharedVeinService.sendAll(player);
            }
        }
    }

    private static void tell(ServerPlayer player, String message, boolean ok) {
        player.sendSystemMessage(Component.literal(message).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public static boolean canEdit(ServerPlayer player, UUID guild) {
        if (SolarisServerConfig.GUILD_WAYPOINT_EDIT_POLICY.get() ==
                SolarisServerConfig.GuildWaypointEditors.ALL_MEMBERS) {
            return true;
        }
        GuildWaypointPermission hook = SolarisGuildAPI.waypointPermission();
        return hook != null && hook.canEdit(player, guild);
    }

    private static UUID checkAccess(ServerPlayer player) {
        if (!SolarisServerConfig.SPEC.isLoaded() || !SolarisServerConfig.GUILD_WAYPOINTS_ENABLED.get()) {
            tell(player, "Guild waypoint lists are disabled on this server.", false);
            return null;
        }
        ResourceLocation dimension = player.level().dimension().location();
        if (!SolarisServerAPI.isFeatureEnabled(player, SolarisAPI.FEATURE_GUILD_SHARE) ||
                !SolarisServerAPI.getFeatureState(player, SolarisAPI.FEATURE_GUILD_SHARE, dimension)
                        .atLeast(SolarisFeatureState.ENABLED)) {
            tell(player, "Guild sharing isn't available right now.", false);
            return null;
        }
        if (!inGuild(player)) {
            tell(player, "You're not in a guild.", false);
            return null;
        }
        UUID guild = GuildsIntegration.ownerTokenFor(player);
        if (!canEdit(player, guild)) {
            tell(player, "You don't have permission to change your guild's waypoints.", false);
            return null;
        }
        return guild;
    }

    private static String limit(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static boolean upsert(ServerPlayer player, String key, String name, String dimension, int x, int y, int z,
                                 String color, String icon, String category) {
        UUID guild = checkAccess(player);
        if (guild == null) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        String cleanName = limit(name, 48);
        if (cleanName.isEmpty()) {
            tell(player, "A guild waypoint needs a name.", false);
            return false;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        if (dimensionId == null || server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId)) == null) {
            tell(player, "Unknown dimension for that waypoint.", false);
            return false;
        }
        if (y < -2048 || y > 2048) {
            tell(player, "That waypoint's height is out of range.", false);
            return false;
        }

        GuildWaypointData data = GuildWaypointData.get(server.overworld());
        String finalKey;
        String cleanKey = limit(key, 64);
        if (!cleanKey.isEmpty()) {
            if (!data.has(guild, cleanKey)) {
                tell(player, "That guild waypoint no longer exists.", false);
                return false;
            }
            finalKey = cleanKey;
        } else {
            if (data.count(guild) >= SolarisServerConfig.GUILD_WAYPOINTS_MAX.get()) {
                tell(player, "Your guild's waypoint list is full.", false);
                return false;
            }
            String base = ServerWaypointService.slug(cleanName);
            if (base.length() > 36) base = base.substring(0, 36);
            finalKey = base;
            int n = 2;
            while (data.has(guild, finalKey)) finalKey = base + "_" + n++;
        }

        String hex = color != null && color.matches("[0-9A-Fa-f]{6,8}") ? color.toUpperCase(java.util.Locale.ROOT) :
                "FFFFFF";
        String cleanIcon = limit(icon, 32).isEmpty() ? "DOT" : limit(icon, 32);
        String cleanCategory = limit(category, 32).isEmpty() ? "Guild" : limit(category, 32);

        data.put(guild, new GuildWaypointData.Entry(finalKey, cleanName, dimensionId.toString(), x, y, z, hex,
                cleanIcon, cleanCategory));
        tell(player, "Guild waypoint '" + cleanName + "' saved.", true);
        broadcast(player);
        return true;
    }

    public static boolean remove(ServerPlayer player, String key) {
        UUID guild = checkAccess(player);
        if (guild == null) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        if (!GuildWaypointData.get(server.overworld()).remove(guild, limit(key, 64))) {
            tell(player, "That guild waypoint no longer exists.", false);
            return false;
        }
        tell(player, "Guild waypoint removed.", true);
        broadcast(player);
        return true;
    }

    public static List<GuildWaypointData.Entry> list(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !inGuild(player)) return List.of();
        return GuildWaypointData.get(server.overworld()).entries(GuildsIntegration.ownerTokenFor(player));
    }
}
