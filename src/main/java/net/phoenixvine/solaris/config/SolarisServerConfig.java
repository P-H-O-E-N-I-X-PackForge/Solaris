package net.phoenixvine.solaris.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.phoenixvine.solaris.api.SolarisFeatureState;

import java.util.List;

public final class SolarisServerConfig {

    public enum PlayerVisibility {

        OFF,

        GUILD,

        ALL
    }

    public enum PingAudience {
        OFF,

        GUILD,

        ALL
    }

    public enum GuildWaypointEditors {
        ALL_MEMBERS,
        HOOK
    }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<PlayerVisibility> PLAYER_VISIBILITY;
    public static final ForgeConfigSpec.IntValue PLAYER_UPDATE_TICKS;
    public static final ForgeConfigSpec.IntValue PLAYER_MAX_RANGE;
    public static final ForgeConfigSpec.BooleanValue HIDE_SNEAKING;
    public static final ForgeConfigSpec.BooleanValue HIDE_INVISIBLE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAYER_BLOCKED_DIMENSIONS;

    public static final ForgeConfigSpec.BooleanValue SERVER_DEATH_MARKERS;
    public static final ForgeConfigSpec.BooleanValue SHARE_DEATHS_WITH_GUILD;

    public static final ForgeConfigSpec.EnumValue<PingAudience> PING_AUDIENCE;
    public static final ForgeConfigSpec.IntValue PING_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue PING_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PING_MAX_DISTANCE;

    public static final ForgeConfigSpec.BooleanValue STRUCTURE_WAYPOINTS_ENABLED;

    public static final ForgeConfigSpec.BooleanValue SHARED_EXPLORATION_ENABLED;
    public static final ForgeConfigSpec.IntValue SHARED_EXPLORATION_MAX_CHUNKS;
    public static final ForgeConfigSpec.IntValue SHARED_EXPLORATION_UPLOAD_PER_SECOND;
    public static final ForgeConfigSpec.IntValue SHARED_EXPLORATION_DOWNLOAD_PER_SECOND;
    public static final ForgeConfigSpec.IntValue SHARED_EXPLORATION_RELAY_RADIUS;
    public static final ForgeConfigSpec.IntValue SHARED_EXPLORATION_SAVE_MINUTES;

    public static final ForgeConfigSpec.BooleanValue SHARED_VEINS_ENABLED;
    public static final ForgeConfigSpec.IntValue SHARED_VEINS_MAX;

    public static final ForgeConfigSpec.BooleanValue CLAIMS_ENABLED;
    public static final ForgeConfigSpec.IntValue CLAIMS_UPDATE_TICKS;
    public static final ForgeConfigSpec.IntValue CLAIMS_RADIUS_CHUNKS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROGRESSION_GATES;
    public static final ForgeConfigSpec.EnumValue<SolarisFeatureState> GATE_LOCKED_STATE;
    public static final ForgeConfigSpec.EnumValue<SolarisFeatureState> GATE_UNLOCKED_STATE;
    public static final ForgeConfigSpec.IntValue PROGRESSION_POLL_TICKS;

    public static final ForgeConfigSpec.BooleanValue GUILD_WAYPOINTS_ENABLED;
    public static final ForgeConfigSpec.IntValue GUILD_WAYPOINTS_MAX;
    public static final ForgeConfigSpec.EnumValue<GuildWaypointEditors> GUILD_WAYPOINT_EDIT_POLICY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("playerPositions");
        PLAYER_VISIBILITY = builder
                .comment("Who a player can see on their map, beyond normal render distance. OFF = nobody " +
                        "(default), GUILD = online guild-mates only, ALL = every other player in the same " +
                        "dimension. The server decides this and only sends what is allowed, so a modified client " +
                        "cannot see more. ALL is effectively a player radar; think twice on PvP servers.")
                .defineEnum("visibility", PlayerVisibility.OFF);
        PLAYER_UPDATE_TICKS = builder
                .comment("How often positions are sent, in ticks (20 = once per second). Higher values use less " +
                        "bandwidth; with ALL the cost grows with the square of the player count.")
                .defineInRange("updateTicks", 20, 5, 100);
        PLAYER_MAX_RANGE = builder
                .comment("Only share players within this many blocks of the viewer. 0 = unlimited.")
                .defineInRange("maxRangeBlocks", 0, 0, 30000000);
        HIDE_SNEAKING = builder
                .comment("Do not share the position of players who are sneaking.")
                .define("hideSneaking", true);
        HIDE_INVISIBLE = builder
                .comment("Do not share the position of players with the invisibility effect. Spectators are " +
                        "never shared. Players can also hide themselves with /solaris privacy hide.")
                .define("hideInvisible", true);
        PLAYER_BLOCKED_DIMENSIONS = builder
                .comment("Dimension ids where player positions are never shared and never shown, e.g. " +
                        "[\"minecraft:the_nether\"]. Applies to viewers and to the players being viewed.")
                .defineList("blockedDimensions", List.of(), o -> o instanceof String);
        builder.pop();

        builder.push("deathMarkers");
        SERVER_DEATH_MARKERS = builder
                .comment("The server records deaths and tells the client where to put the marker. This is more " +
                        "reliable than clients detecting their own death (it also works with instant respawn). " +
                        "Players can still turn death markers off in their own config. When false, clients that " +
                        "have the mod place their own marker as before.")
                .define("serverAuthoritative", true);
        SHARE_DEATHS_WITH_GUILD = builder
                .comment("Also give online guild-mates a marker where a player died. Needs the Guilds mod. " +
                        "These show up in the mates' waypoint lists under 'Guild Deaths'.")
                .define("shareWithGuild", false);
        builder.pop();

        builder.push("pings");
        PING_AUDIENCE = builder
                .comment("Who sees a map ping. OFF = pings disabled, GUILD = the sender and online guild-mates " +
                        "(default), ALL = everyone in the sender's dimension.")
                .defineEnum("audience", PingAudience.GUILD);
        PING_DURATION_SECONDS = builder
                .comment("How long a ping stays on the map.")
                .defineInRange("durationSeconds", 30, 5, 300);
        PING_COOLDOWN_TICKS = builder
                .comment("Minimum time between pings from the same player, in ticks (20 = one second).")
                .defineInRange("cooldownTicks", 20, 5, 200);
        PING_MAX_DISTANCE = builder
                .comment("A ping is rejected if it is further than this many blocks from the player sending it.")
                .defineInRange("maxDistanceBlocks", 512, 16, 4096);
        builder.pop();

        builder.push("structureWaypoints");
        STRUCTURE_WAYPOINTS_ENABLED = builder
                .comment("Tell players when they first set foot in a generated structure (village, mineshaft, " +
                        "stronghold...) so their client can add a waypoint. Players can still turn the waypoints " +
                        "off in their own config (autoStructureWaypoints).")
                .define("enabled", true);
        builder.pop();

        builder.push("sharedExploration");
        SHARED_EXPLORATION_ENABLED = builder
                .comment("Guild members share explored map chunks: what one player uncovers shows on their " +
                        "guild-mates' maps. The server stores the data (world/solaris/guildmaps), so this costs " +
                        "disk space and bandwidth; off by default. Needs the Guilds mod. Players can opt out in " +
                        "their own config, and the shared_exploration feature can be switched per player.")
                .define("enabled", false);
        SHARED_EXPLORATION_MAX_CHUNKS = builder
                .comment("Most chunks stored per guild per dimension (about 1 KB each). Uploads past this are " +
                        "refused.")
                .defineInRange("maxChunksPerGuildDimension", 20000, 100, 200000);
        SHARED_EXPLORATION_UPLOAD_PER_SECOND = builder
                .comment("Most chunks one player may upload per second (short bursts of five times this are " +
                        "allowed).")
                .defineInRange("uploadChunksPerSecond", 60, 5, 400);
        SHARED_EXPLORATION_DOWNLOAD_PER_SECOND = builder
                .comment("Most chunks streamed to one player per second when catching them up.")
                .defineInRange("downloadChunksPerSecond", 60, 5, 600);
        SHARED_EXPLORATION_RELAY_RADIUS = builder
                .comment("A newly uploaded chunk is pushed live to online guild-mates within this many chunks; " +
                        "others pick it up when they get near or open that part of the map.")
                .defineInRange("relayRadiusChunks", 160, 16, 1000);
        SHARED_EXPLORATION_SAVE_MINUTES = builder
                .comment("How often changed shared map data is written to disk.")
                .defineInRange("saveMinutes", 5, 1, 60);
        builder.pop();

        builder.push("sharedVeins");
        SHARED_VEINS_ENABLED = builder
                .comment("Guild members share the GT ore veins they find (needs GregTech on the clients and the " +
                        "Guilds mod). Stored in the world's save data; small.")
                .define("enabled", true);
        SHARED_VEINS_MAX = builder
                .comment("Most veins stored per guild.")
                .defineInRange("maxPerGuild", 2000, 50, 20000);
        builder.pop();

        builder.push("claims");
        CLAIMS_ENABLED = builder
                .comment("Sync territory from mods that register a claim provider (see SolarisClaimsAPI) to " +
                        "players' maps. Does nothing if no mod registers one.")
                .define("enabled", true);
        CLAIMS_UPDATE_TICKS = builder
                .comment("How often claims are re-checked per player, in ticks. Data is only sent when it changed.")
                .defineInRange("updateTicks", 40, 20, 600);
        CLAIMS_RADIUS_CHUNKS = builder
                .comment("Claims are sent for this many chunks around the player in each direction.")
                .defineInRange("radiusChunks", 24, 4, 64);
        builder.pop();

        builder.push("progression");
        PROGRESSION_GATES = builder
                .comment("Unlock map features through gates. Each entry is \"feature@dimension=gate\" (use * for " +
                        "every dimension, or leave out the @ part). A gate is an advancement id, a name you grant " +
                        "with /solaris gate grant, or a condition another mod registers through SolarisGatesAPI. " +
                        "Put | between gates to unlock when any one is met, and write several entries for the " +
                        "same feature to require all of them. Example: [\"minimap@*=minecraft:story/mine_stone\", " +
                        "\"pings=mymod:scout|mymod:veteran\"]. Features include minimap, fullscreen_map, " +
                        "world_map, waypoints, underground_map, globe_view, shape_planner, show_mobs, " +
                        "show_players, pings, claims and guild_share (see /solaris feature for the full list). " +
                        "Until unlocked, a feature is in lockedState. A guild shares feature states, so for a " +
                        "guild member an unlock is permanent (one member meeting a gate unlocks it for all); a " +
                        "player without a guild is locked again if they stop meeting the gate.")
                .defineList("gates", List.of(), o -> o instanceof String);
        GATE_LOCKED_STATE = builder
                .comment("State of a gated feature before its gate is met (DISABLED hides it, VISIBLE shows it " +
                        "greyed out).")
                .defineEnum("lockedState", SolarisFeatureState.DISABLED);
        GATE_UNLOCKED_STATE = builder
                .comment("State of a gated feature once its gate is met.")
                .defineEnum("unlockedState", SolarisFeatureState.ENABLED);
        PROGRESSION_POLL_TICKS = builder
                .comment("How often gates are re-checked for every player, in ticks. This is what lets a mod's " +
                        "own condition unlock a feature without calling refresh. 0 = only on login, when an " +
                        "advancement is earned, and when refresh is called.")
                .defineInRange("pollTicks", 100, 0, 1200);
        builder.pop();

        builder.push("guildWaypoints");
        GUILD_WAYPOINTS_ENABLED = builder
                .comment("Let guilds keep a shared waypoint list on the server (needs the Guilds mod).")
                .define("enabled", true);
        GUILD_WAYPOINTS_MAX = builder
                .comment("Most waypoints a single guild can have in its shared list.")
                .defineInRange("maxPerGuild", 64, 1, 400);
        GUILD_WAYPOINT_EDIT_POLICY = builder
                .comment("Who may add, change or remove entries. ALL_MEMBERS = any guild member. HOOK = only " +
                        "what the Guilds mod allows through SolarisGuildAPI.setWaypointPermission (nobody if it " +
                        "never registers one). Using the feature also needs the guild_share feature enabled.")
                .defineEnum("editPolicy", GuildWaypointEditors.ALL_MEMBERS);
        builder.pop();

        SPEC = builder.build();
    }

    private SolarisServerConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "solaris/solaris-server.toml");
    }
}
