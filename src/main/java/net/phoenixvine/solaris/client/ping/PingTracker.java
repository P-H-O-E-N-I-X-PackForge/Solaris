package net.phoenixvine.solaris.client.ping;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.network.C2SPingPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PingTracker {

    public record Ping(String sender, ResourceLocation dimension, int x, int y, int z, long createdAtMillis,
                       long expiresAtMillis) {}

    private static final List<Ping> PINGS = new ArrayList<>();

    private PingTracker() {}

    public static void add(String sender, ResourceLocation dimension, int x, int y, int z, long durationMillis) {
        long now = System.currentTimeMillis();
        
        PINGS.removeIf(p -> p.sender().equals(sender));
        PINGS.add(new Ping(sender, dimension, x, y, z, now, now + durationMillis));
    }

    public static List<Ping> collect(Minecraft mc) {
        long now = System.currentTimeMillis();
        PINGS.removeIf(p -> p.expiresAtMillis() <= now);
        if (mc.level == null || PINGS.isEmpty()) return List.of();

        ResourceLocation dimension = mc.level.dimension().location();
        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_PINGS, dimension).atLeast(SolarisFeatureState.VISIBLE)) {
            return List.of();
        }
        List<Ping> out = new ArrayList<>();
        for (Ping p : PINGS) {
            if (p.dimension().equals(dimension)) out.add(p);
        }
        return out;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PINGS.clear();
    }

    public static void sendPing(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        ResourceLocation dimension = mc.level.dimension().location();
        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_PINGS, dimension).atLeast(SolarisFeatureState.ENABLED)) {
            mc.player.displayClientMessage(Component.literal("Pings aren't available here."), true);
            return;
        }
        if (!SolarisNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) {
            mc.player.displayClientMessage(Component.literal("Pings need Solaris on the server."), true);
            return;
        }

        HitResult hit = mc.player.pick(256.0D, 0.0F, false);
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block ?
                block.getBlockPos() : mc.player.blockPosition();
        SolarisNetwork.CHANNEL.sendToServer(new C2SPingPacket(pos.getX(), pos.getY(), pos.getZ()));
    }
}
