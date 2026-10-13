package net.phoenixvine.solaris.client.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.network.S2CPlayerPositionsPacket;
import net.phoenixvine.solaris.server.ServerCapabilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RemotePlayerTracker {

    private static final long EXPIRY_MS = 15_000L;

    public record MapPlayer(UUID id, String name, double x, double z, float yaw, ResourceLocation skin) {}

    private static ResourceLocation snapshotDimension = null;
    private static List<S2CPlayerPositionsPacket.Entry> snapshot = List.of();
    private static long snapshotReceivedAtMillis = 0L;

    private RemotePlayerTracker() {}

    public static void apply(ResourceLocation dimension, List<S2CPlayerPositionsPacket.Entry> entries) {
        snapshotDimension = dimension;
        snapshot = entries;
        snapshotReceivedAtMillis = System.currentTimeMillis();
    }

    public static void clear() {
        snapshotDimension = null;
        snapshot = List.of();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static List<MapPlayer> collect(Minecraft mc) {
        List<MapPlayer> out = new ArrayList<>();
        LocalPlayer self = mc.player;
        if (mc.level == null || self == null) return out;
        if (!SolarisConfig.SHOW_PLAYERS.get()) return out;

        ResourceLocation dimension = mc.level.dimension().location();
        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_SHOW_PLAYERS, dimension)
                .atLeast(SolarisFeatureState.VISIBLE)) {
            return out;
        }

        boolean fresh = System.currentTimeMillis() - snapshotReceivedAtMillis < EXPIRY_MS &&
                dimension.equals(snapshotDimension);

        boolean authoritative = ServerCapabilities.playerPositions();
        Set<UUID> permitted = new HashSet<>();
        if (authoritative && fresh) {
            for (S2CPlayerPositionsPacket.Entry e : snapshot) permitted.add(e.id());
        }

        Set<UUID> seen = new HashSet<>();
        seen.add(self.getUUID());

        for (AbstractClientPlayer other : mc.level.players()) {
            if (!seen.add(other.getUUID())) continue;
            if (authoritative && !permitted.contains(other.getUUID())) continue;
            out.add(new MapPlayer(other.getUUID(), other.getGameProfile().getName(), other.getX(), other.getZ(),
                    other.getYRot(), other.getSkinTextureLocation()));
        }

        if (fresh) {
            for (S2CPlayerPositionsPacket.Entry e : snapshot) {
                if (!seen.add(e.id())) continue;
                PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(e.id()) : null;
                ResourceLocation skin = info != null ? info.getSkinLocation() :
                        DefaultPlayerSkin.getDefaultSkin(e.id());
                out.add(new MapPlayer(e.id(), e.name(), e.x() + 0.5, e.z() + 0.5, e.yaw(), skin));
            }
        }
        return out;
    }
}
