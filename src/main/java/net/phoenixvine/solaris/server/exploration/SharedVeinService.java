package net.phoenixvine.solaris.server.exploration;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.common.SharedVeinEntry;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.packets.S2CSharedVeinsPacket;
import net.phoenixvine.solaris.server.SolarisServerAPI;
import net.phoenixvine.solaris.server.SolarisServerUtil;
import net.phoenixvine.solaris.server.guild.GuildWaypointService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SharedVeinService {

    private static final int SNAPSHOT_BATCH = 100;
    private static final double UPLOADS_PER_SECOND = 20;

    private static final RateLimiter LIMITER = new RateLimiter();

    private SharedVeinService() {}

    public static void forget(UUID player) {
        LIMITER.forget(player);
    }

    private static boolean allowed(ServerPlayer player, ResourceLocation dimension) {
        if (!SolarisServerConfig.SPEC.isLoaded() || !SolarisServerConfig.SHARED_VEINS_ENABLED.get()) return false;
        if (!SolarisServerUtil.hasSolaris(player) || !GuildWaypointService.inGuild(player)) return false;
        if (!SolarisServerAPI.isFeatureEnabled(player, SolarisAPI.FEATURE_SHARED_VEINS)) return false;
        return SolarisServerAPI.getFeatureState(player, SolarisAPI.FEATURE_SHARED_VEINS, dimension)
                .atLeast(SolarisFeatureState.ENABLED);
    }

    private static String limit(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static void handleUpload(ServerPlayer player, List<SharedVeinEntry> entries) {
        MinecraftServer server = player.getServer();
        if (server == null || !SolarisServerConfig.SPEC.isLoaded()) return;

        int permitted = LIMITER.take(player.getUUID(), entries.size(), server.getTickCount(), UPLOADS_PER_SECOND);
        UUID guild = GuildsIntegration.ownerTokenFor(player);
        GuildVeinData data = GuildVeinData.get(server.overworld());
        int max = SolarisServerConfig.SHARED_VEINS_MAX.get();

        List<SharedVeinEntry> added = new ArrayList<>();
        for (int i = 0; i < permitted; i++) {
            SharedVeinEntry e = entries.get(i);

            ResourceLocation dimension = ResourceLocation.tryParse(e.dimension());
            if (dimension == null ||
                    server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)) == null) {
                continue;
            }
            if (!allowed(player, dimension)) continue;
            if (e.y() < -2048 || e.y() > 2048 || Math.abs(e.x()) > 30_000_000 || Math.abs(e.z()) > 30_000_000) continue;

            String item = limit(e.itemId(), 128);
            if (!item.isEmpty() && ResourceLocation.tryParse(item) == null) item = "";
            SharedVeinEntry clean = new SharedVeinEntry(dimension.toString(), e.x(), e.y(), e.z(),
                    limit(e.name(), 96), e.argb(), item);

            if (data.count(guild) >= max) break;
            if (data.add(guild, clean)) added.add(clean);
        }
        if (added.isEmpty()) return;

        for (ServerPlayer mate : GuildsIntegration.getOnlineGuildmates(player)) {
            if (mate == player || !SolarisServerUtil.hasSolaris(mate)) continue;
            for (int from = 0; from < added.size(); from += S2CSharedVeinsPacket.MAX_ENTRIES) {
                List<SharedVeinEntry> batch = added.subList(from,
                        Math.min(added.size(), from + S2CSharedVeinsPacket.MAX_ENTRIES));
                PacketDistributor.sendToPlayer(mate,
                        new S2CSharedVeinsPacket(S2CSharedVeinsPacket.FIRST | S2CSharedVeinsPacket.LAST,
                                new ArrayList<>(batch)));
            }
        }
    }

    public static void sendAll(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !SolarisServerConfig.SPEC.isLoaded() || !SolarisServerConfig.SHARED_VEINS_ENABLED.get()) {
            return;
        }
        if (!SolarisServerUtil.hasSolaris(player) || !GuildWaypointService.inGuild(player)) return;

        List<SharedVeinEntry> all = GuildVeinData.get(server.overworld())
                .entries(GuildsIntegration.ownerTokenFor(player));
        int batches = Math.max(1, (all.size() + SNAPSHOT_BATCH - 1) / SNAPSHOT_BATCH);
        for (int i = 0; i < batches; i++) {
            List<SharedVeinEntry> batch = new ArrayList<>(all.subList(i * SNAPSHOT_BATCH,
                    Math.min(all.size(), (i + 1) * SNAPSHOT_BATCH)));
            int flags = (i == 0 ? S2CSharedVeinsPacket.FIRST : 0) | (i == batches - 1 ? S2CSharedVeinsPacket.LAST : 0);
            PacketDistributor.sendToPlayer(player,
                    new S2CSharedVeinsPacket(flags, batch));
        }
    }
}
