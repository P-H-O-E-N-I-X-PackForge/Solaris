package net.phoenixvine.solaris.server.players;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.config.SolarisServerConfig;
import net.phoenixvine.solaris.config.SolarisServerConfig.PingAudience;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.S2CPingPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;
import net.phoenixvine.solaris.server.SolarisServerAPI;
import net.phoenixvine.solaris.server.SolarisServerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PingService {

    private static final Map<UUID, Integer> LAST_PING_TICK = new HashMap<>();

    private PingService() {}

    public static void forget(UUID player) {
        LAST_PING_TICK.remove(player);
    }

    public static void handle(ServerPlayer sender, int x, int y, int z) {
        if (!SolarisServerConfig.SPEC.isLoaded()) return;
        MinecraftServer server = sender.getServer();
        if (server == null) return;

        PingAudience audience = SolarisServerConfig.PING_AUDIENCE.get();
        if (audience == PingAudience.OFF) {
            sender.sendSystemMessage(Component.literal("Pings are disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        ResourceLocation dimension = sender.level().dimension().location();
        if (!allowed(sender, dimension, SolarisFeatureState.ENABLED)) return;

        int now = server.getTickCount();
        Integer last = LAST_PING_TICK.get(sender.getUUID());
        if (last != null && now - last < SolarisServerConfig.PING_COOLDOWN_TICKS.get()) return;

        double max = SolarisServerConfig.PING_MAX_DISTANCE.get();
        if (sender.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > max * max) return;
        LAST_PING_TICK.put(sender.getUUID(), now);

        List<ServerPlayer> recipients = new ArrayList<>();
        if (audience == PingAudience.ALL) {
            recipients.addAll(server.getPlayerList().getPlayers());
        } else {
            List<ServerPlayer> mates = GuildsIntegration.getOnlineGuildmates(sender);
            if (mates.isEmpty()) {
                sender.sendSystemMessage(Component.literal("No online guild-mates to ping.")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
            recipients.add(sender);
            recipients.addAll(mates);
        }

        S2CPingPacket packet = new S2CPingPacket(sender.getGameProfile().getName(), dimension.toString(), x, y, z,
                SolarisServerConfig.PING_DURATION_SECONDS.get() * 20);
        for (ServerPlayer receiver : recipients) {
            if (!receiver.level().dimension().location().equals(dimension)) continue;
            if (!SolarisServerUtil.hasSolaris(receiver)) continue;
            if (!allowed(receiver, dimension, SolarisFeatureState.VISIBLE)) continue;
            SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver), packet);
        }
    }

    private static boolean allowed(ServerPlayer player, ResourceLocation dimension, SolarisFeatureState minimum) {
        if (!SolarisServerAPI.isFeatureEnabled(player, SolarisAPI.FEATURE_PINGS)) return false;
        return SolarisServerAPI.getFeatureState(player, SolarisAPI.FEATURE_PINGS, dimension).atLeast(minimum);
    }
}
