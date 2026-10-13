package net.phoenixvine.solaris.server.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.api.SolarisGatesAPI;
import net.phoenixvine.solaris.api.SolarisMarkers;
import net.phoenixvine.solaris.server.SolarisServerAPI;
import net.phoenixvine.solaris.server.exploration.SharedExplorationService;
import net.phoenixvine.solaris.server.features.ProgressionGates;
import net.phoenixvine.solaris.server.guild.GuildWaypointService;
import net.phoenixvine.solaris.server.waypoint.ServerWaypointData;
import net.phoenixvine.solaris.server.waypoint.ServerWaypointService;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

@EventBusSubscriber()
public class SolarisCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();

        var featureSet = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("feature", StringArgumentType.word())
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .executes(ctx -> featureSet(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                DimensionArgument.getDimension(ctx, "dimension"),
                                                StringArgumentType.getString(ctx, "feature"),
                                                StringArgumentType.getString(ctx, "state"))))));

        var featureGet = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("feature", StringArgumentType.word())
                                .executes(ctx -> featureGet(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        DimensionArgument.getDimension(ctx, "dimension"),
                                        StringArgumentType.getString(ctx, "feature")))));

        var waypointPlaceLocked = Commands.argument("locked", BoolArgumentType.bool())
                .executes(ctx -> waypointPlace(ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player"),
                        StringArgumentType.getString(ctx, "name"),
                        DimensionArgument.getDimension(ctx, "dimension"),
                        IntegerArgumentType.getInteger(ctx, "x"),
                        IntegerArgumentType.getInteger(ctx, "y"),
                        IntegerArgumentType.getInteger(ctx, "z"),
                        StringArgumentType.getString(ctx, "color"),
                        BoolArgumentType.getBool(ctx, "locked")));

        var waypointPlaceColor = Commands.argument("color", StringArgumentType.word())
                .executes(ctx -> waypointPlace(ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player"),
                        StringArgumentType.getString(ctx, "name"),
                        DimensionArgument.getDimension(ctx, "dimension"),
                        IntegerArgumentType.getInteger(ctx, "x"),
                        IntegerArgumentType.getInteger(ctx, "y"),
                        IntegerArgumentType.getInteger(ctx, "z"),
                        StringArgumentType.getString(ctx, "color"),
                        false))
                .then(waypointPlaceLocked);

        var waypointPlaceXyz = Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(waypointPlaceColor)));

        var waypointPlace = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .then(waypointPlaceXyz)));

        var waypointRemove = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> waypointRemove(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "name"))));

        var chunkForceUpdate = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> chunkForceUpdate(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                DimensionArgument.getDimension(ctx, "dimension"),
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z"))))));

        var swAddTail = Commands.argument("color", StringArgumentType.word())
                .executes(ctx -> serverWaypointAdd(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                        DimensionArgument.getDimension(ctx, "dimension").dimension().location(),
                        IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
                        IntegerArgumentType.getInteger(ctx, "z"), StringArgumentType.getString(ctx, "color"),
                        "Server"))
                .then(Commands.argument("category", StringArgumentType.string())
                        .executes(ctx -> serverWaypointAdd(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                DimensionArgument.getDimension(ctx, "dimension").dimension().location(),
                                IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
                                IntegerArgumentType.getInteger(ctx, "z"), StringArgumentType.getString(ctx, "color"),
                                StringArgumentType.getString(ctx, "category"))));

        var swAdd = Commands.argument("name", StringArgumentType.string())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> serverWaypointAdd(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        DimensionArgument.getDimension(ctx, "dimension").dimension()
                                                                .location(),
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z"), "FFFFFF", "Server"))
                                                .then(swAddTail)))));

        var swAddHere = Commands.argument("name", StringArgumentType.string())
                .executes(ctx -> serverWaypointAddHere(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                        "FFFFFF", "Server"))
                .then(Commands.argument("color", StringArgumentType.word())
                        .executes(ctx -> serverWaypointAddHere(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "color"),
                                "Server"))
                        .then(Commands.argument("category", StringArgumentType.string())
                                .executes(ctx -> serverWaypointAddHere(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "color"),
                                        StringArgumentType.getString(ctx, "category")))));

        var swRemove = Commands.argument("name", StringArgumentType.string())
                .executes(ctx -> serverWaypointRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")));

        var markerAddColor = Commands.argument("color", StringArgumentType.word())
                .executes(ctx -> markerAdd(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                        StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "name"),
                        DimensionArgument.getDimension(ctx, "dimension").dimension().location(),
                        IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
                        IntegerArgumentType.getInteger(ctx, "z"), IntegerArgumentType.getInteger(ctx, "seconds"),
                        StringArgumentType.getString(ctx, "color")));

        var markerAdd = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("key", StringArgumentType.string())
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .then(Commands.argument("seconds",
                                                                        IntegerArgumentType.integer(0, 86400))
                                                                        .executes(ctx -> markerAdd(ctx.getSource(),
                                                                                EntityArgument.getPlayer(ctx,
                                                                                        "player"),
                                                                                StringArgumentType.getString(ctx,
                                                                                        "key"),
                                                                                StringArgumentType.getString(ctx,
                                                                                        "name"),
                                                                                DimensionArgument
                                                                                        .getDimension(ctx,
                                                                                                "dimension")
                                                                                        .dimension().location(),
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                        "x"),
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                        "y"),
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                        "z"),
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                        "seconds"),
                                                                                "FFFFFF"))
                                                                        .then(markerAddColor))))))));

        var markerRemove = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("key", StringArgumentType.string())
                        .executes(ctx -> markerRemove(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "key"))));

        var markerClear = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("prefix", StringArgumentType.string())
                        .executes(ctx -> markerClear(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "prefix"))));

        var sharedMapStatus = Commands.argument("player", EntityArgument.player())
                .executes(ctx -> sharedMapStatus(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")));

        var gateGrant = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("gate", StringArgumentType.string())
                        .executes(ctx -> gateGrant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "gate"))));

        var gateRevoke = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("gate", StringArgumentType.string())
                        .executes(ctx -> gateRevoke(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "gate"))));

        var gateCheck = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("gate", StringArgumentType.string())
                        .executes(ctx -> gateCheck(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "gate"))));

        var gateList = Commands.argument("player", EntityArgument.player())
                .executes(ctx -> gateList(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")));

        var gwAddHere = Commands.argument("name", StringArgumentType.string())
                .executes(ctx -> guildWaypointAddHere(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                        "FFFFFF"))
                .then(Commands.argument("color", StringArgumentType.word())
                        .executes(ctx -> guildWaypointAddHere(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "color"))));

        var gwRemove = Commands.argument("name", StringArgumentType.string())
                .executes(ctx -> guildWaypointRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")));

        d.register(Commands.literal("solaris")
                .then(Commands.literal("feature")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set").then(featureSet))
                        .then(Commands.literal("get").then(featureGet)))
                .then(Commands.literal("waypoint")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("place").then(waypointPlace))
                        .then(Commands.literal("remove").then(waypointRemove)))
                .then(Commands.literal("chunk")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("forceupdate").then(chunkForceUpdate)))
                .then(Commands.literal("serverwaypoint")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("add").then(swAdd))
                        .then(Commands.literal("addhere").then(swAddHere))
                        .then(Commands.literal("remove").then(swRemove))
                        .then(Commands.literal("list").executes(ctx -> serverWaypointList(ctx.getSource()))))
                .then(Commands.literal("sharedmap")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("status").then(sharedMapStatus)))
                .then(Commands.literal("gate")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("grant").then(gateGrant))
                        .then(Commands.literal("revoke").then(gateRevoke))
                        .then(Commands.literal("check").then(gateCheck))
                        .then(Commands.literal("list").then(gateList)))
                .then(Commands.literal("guildwaypoint")
                        .then(Commands.literal("list").executes(ctx -> guildWaypointList(ctx.getSource())))
                        .then(Commands.literal("addhere").then(gwAddHere))
                        .then(Commands.literal("remove").then(gwRemove)))
                .then(Commands.literal("marker")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("add").then(markerAdd))
                        .then(Commands.literal("remove").then(markerRemove))
                        .then(Commands.literal("clear").then(markerClear)))
                .then(Commands.literal("privacy")
                        .then(Commands.literal("hide").executes(ctx -> privacySet(ctx.getSource(), true)))
                        .then(Commands.literal("show").executes(ctx -> privacySet(ctx.getSource(), false)))
                        .then(Commands.literal("status").executes(ctx -> privacyStatus(ctx.getSource())))));
    }

    private static int markerAdd(CommandSourceStack source, ServerPlayer target, String key, String name,
                                 ResourceLocation dimension, int x, int y, int z, int seconds, String color) {
        try {
            SolarisMarkers.marker(key).name(name).at(dimension, x, y, z).color(color)
                    .expiresAfterTicks(seconds * 20).sendTo(target);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Marker '" + key + "' sent to " +
                target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int markerRemove(CommandSourceStack source, ServerPlayer target, String key) {
        try {
            SolarisMarkers.remove(target, key);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Marker '" + key + "' removed for " +
                target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int markerClear(CommandSourceStack source, ServerPlayer target, String prefix) {
        SolarisMarkers.clear(target, prefix);
        source.sendSuccess(() -> Component.literal("Markers starting with '" + prefix + "' cleared for " +
                target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int sharedMapStatus(CommandSourceStack source, ServerPlayer target) {
        for (String line : SharedExplorationService.status(target)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int gateGrant(CommandSourceStack source, ServerPlayer target, String gate) {
        SolarisGatesAPI.grant(target, gate);
        source.sendSuccess(() -> Component.literal("Granted gate '" + gate + "' to " +
                target.getGameProfile().getName() + " (and their guild, if any)."), true);
        return 1;
    }

    private static int gateRevoke(CommandSourceStack source, ServerPlayer target, String gate) {
        SolarisGatesAPI.revoke(target, gate);
        source.sendSuccess(() -> Component.literal("Revoked gate '" + gate + "' from " +
                target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int gateCheck(CommandSourceStack source, ServerPlayer target, String gate) {
        boolean met = SolarisGatesAPI.isUnlocked(target, gate);
        source.sendSuccess(() -> Component.literal("Gate '" + gate + "' is " + (met ? "met" : "not met") + " for " +
                target.getGameProfile().getName() + "."), false);
        return met ? 1 : 0;
    }

    private static int gateList(CommandSourceStack source, ServerPlayer target) {
        var gates = ProgressionGates.manualGates(target);
        source.sendSuccess(() -> Component.literal(gates.isEmpty() ?
                "No manually granted gates for " + target.getGameProfile().getName() + "." :
                "Granted gates: " + String.join(", ", gates)), false);
        return gates.size();
    }

    private static int guildWaypointAddHere(CommandSourceStack source, String name, String color)
                                                                                                  throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return GuildWaypointService.upsert(player, "", name, player.level().dimension().location().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), color, "DOT", "Guild") ? 1 : 0;
    }

    private static int guildWaypointRemove(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return GuildWaypointService.remove(player, ServerWaypointService.slug(name)) ? 1 : 0;
    }

    private static int guildWaypointList(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var entries = GuildWaypointService.list(player);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Your guild has no shared waypoints (or you're not in a " +
                    "guild)."), false);
            return 0;
        }
        for (var e : entries) {
            source.sendSuccess(() -> Component.literal(e.key() + ": " + e.name() + " @ " + e.x() + ", " + e.y() +
                    ", " + e.z() + " (" + e.dimension() + ")"), false);
        }
        return entries.size();
    }

    private static String limit(String s, int max) {
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static int serverWaypointAdd(CommandSourceStack source, String name, ResourceLocation dimension, int x,
                                         int y, int z, String color, String category) {
        String cleanName = limit(name, 48);
        if (cleanName.isEmpty()) {
            source.sendFailure(Component.literal("A server waypoint needs a name."));
            return 0;
        }
        String key = ServerWaypointService.slug(cleanName);
        String hex = color.matches("[0-9A-Fa-f]{6,8}") ? color.toUpperCase(java.util.Locale.ROOT) : "FFFFFF";
        String cat = limit(category, 32);
        ServerWaypointData.Entry entry = new ServerWaypointData.Entry(key, cleanName, dimension.toString(), x, y, z,
                hex, "DOT", cat.isEmpty() ? "Server" : cat);
        ServerWaypointService.put(source.getServer(), entry);
        source.sendSuccess(() -> Component.literal("Server waypoint '" + cleanName + "' saved as '" + key +
                "' and sent to online players."), true);
        return 1;
    }

    private static int serverWaypointAddHere(CommandSourceStack source, String name, String color, String category)
                                                                                                                    throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return serverWaypointAdd(source, name, player.level().dimension().location(), player.getBlockX(),
                player.getBlockY(), player.getBlockZ(), color, category);
    }

    private static int serverWaypointRemove(CommandSourceStack source, String name) {
        String key = ServerWaypointService.slug(name);
        if (!ServerWaypointService.remove(source.getServer(), key)) {
            source.sendFailure(Component.literal("No server waypoint named '" + key + "'."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed server waypoint '" + key + "'."), true);
        return 1;
    }

    private static int serverWaypointList(CommandSourceStack source) {
        var entries = ServerWaypointService.all(source.getServer());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No server waypoints."), false);
            return 0;
        }
        for (ServerWaypointData.Entry e : entries) {
            source.sendSuccess(() -> Component.literal(e.key() + ": " + e.name() + " @ " + e.x() + ", " + e.y() +
                    ", " + e.z() + " (" + e.dimension() + ", " + e.category() + ")"), false);
        }
        return entries.size();
    }

    private static int privacySet(CommandSourceStack source, boolean hidden) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SolarisServerAPI.setHiddenFromMap(player, hidden);
        source.sendSuccess(() -> Component.literal(hidden ?
                "Your position is now hidden from other players' maps." :
                "Your position can be shown on other players' maps again (if the server allows it)."), false);
        return 1;
    }

    private static int privacyStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean hidden = SolarisServerAPI.isHiddenFromMap(player);
        source.sendSuccess(() -> Component.literal(
                hidden ? "You are hidden from other players' maps." : "You are not hidden from other players' maps."),
                false);
        return hidden ? 1 : 0;
    }

    private static int featureSet(CommandSourceStack source, ServerPlayer target, ServerLevel dimensionLevel,
                                  String featureName, String stateName) {
        String featureId = featureId(featureName);
        if (featureId == null) {
            source.sendFailure(Component.literal("Unknown feature '" + featureName + "'."));
            return 0;
        }
        SolarisFeatureState state;
        try {
            state = SolarisFeatureState.valueOf(stateName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown state '" + stateName +
                    "' Expected disabled, visible, or enabled."));
            return 0;
        }

        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisServerAPI.setFeatureState(target, featureId, dimension, state);
        source.sendSuccess(() -> Component.literal(
                featureName + " for " + target.getGameProfile().getName() + " in " + dimension + " set to " +
                        state.name().toLowerCase(java.util.Locale.ROOT) + "."),
                true);
        return 1;
    }

    private static int featureGet(CommandSourceStack source, ServerPlayer target, ServerLevel dimensionLevel,
                                  String featureName) {
        String featureId = featureId(featureName);
        if (featureId == null) {
            source.sendFailure(Component.literal("Unknown feature '" + featureName + "'."));
            return 0;
        }

        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisFeatureState state = SolarisServerAPI.getFeatureState(target, featureId, dimension);
        source.sendSuccess(() -> Component.literal(
                featureName + " for " + target.getGameProfile().getName() + " in " + dimension + " is " +
                        state.name().toLowerCase(java.util.Locale.ROOT) + "."),
                false);
        return 1;
    }

    private static int waypointPlace(CommandSourceStack source, ServerPlayer target, String name,
                                     ServerLevel dimensionLevel, int x, int y, int z, String color,
                                     boolean locked) {
        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisServerAPI.placeWaypoint(target, name, dimension, x, y, z, color, locked);
        source.sendSuccess(() -> Component.literal(
                "Placed waypoint '" + name + "' for " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int waypointRemove(CommandSourceStack source, ServerPlayer target, String name) {
        SolarisServerAPI.removeWaypoint(target, name);
        source.sendSuccess(() -> Component.literal(
                "Removed waypoint '" + name + "' from " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int chunkForceUpdate(CommandSourceStack source, ServerPlayer target, ServerLevel dimensionLevel,
                                        int chunkX, int chunkZ) {
        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisServerAPI.forceUpdateChunk(target, dimension, chunkX, chunkZ);
        source.sendSuccess(() -> Component.literal(
                "Requested force-update of chunk (" + chunkX + ", " + chunkZ + ") for " +
                        target.getGameProfile().getName() + "."),
                true);
        return 1;
    }

    private static String featureId(String featureName) {
        return switch (featureName.toLowerCase(java.util.Locale.ROOT)) {
            case "waypoints" -> SolarisAPI.FEATURE_WAYPOINTS;
            case "minimap" -> SolarisAPI.FEATURE_MINIMAP;
            case "worldmap" -> SolarisAPI.FEATURE_WORLD_MAP;
            case "undergroundmap" -> SolarisAPI.FEATURE_UNDERGROUND_MAP;
            case "players" -> SolarisAPI.FEATURE_SHOW_PLAYERS;
            case "pings" -> SolarisAPI.FEATURE_PINGS;
            case "claims" -> SolarisAPI.FEATURE_CLAIMS;
            case "sharedmap" -> SolarisAPI.FEATURE_SHARED_EXPLORATION;
            case "sharedveins" -> SolarisAPI.FEATURE_SHARED_VEINS;
            default -> null;
        };
    }
}
