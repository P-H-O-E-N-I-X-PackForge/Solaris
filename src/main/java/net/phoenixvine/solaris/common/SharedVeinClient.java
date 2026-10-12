package net.phoenixvine.solaris.common;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtVeinRegistry;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;
import net.phoenixvine.solaris.network.C2SSharedVeinsPacket;
import net.phoenixvine.solaris.network.S2CSharedVeinsPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;
import net.phoenixvine.solaris.server.ServerCapabilities;
import net.phoenixvine.solaris.sharing.SharedVeinEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SharedVeinClient {

    private static final Set<String> SERVER_KEYS = new HashSet<>();

    private SharedVeinClient() {}

    private static boolean enabledByUserAndServer() {
        return GtceuIntegration.isAvailable() && ServerCapabilities.sharedVeins() &&
                SolarisConfig.SHARE_EXPLORATION.get();
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !enabledByUserAndServer()) return false;
        return SolarisAPI.getFeatureState(SolarisAPI.FEATURE_SHARED_VEINS, mc.level.dimension().location())
                .atLeast(SolarisFeatureState.ENABLED);
    }

    public static void onLocalVein(String dimension, BlockPos center, String name, int argb, String itemId) {
        if (!active()) return;
        List<SharedVeinEntry> one = new ArrayList<>();
        one.add(new SharedVeinEntry(dimension, center.getX(), center.getY(), center.getZ(), name, argb,
                itemId == null ? "" : itemId));
        SolarisNetwork.CHANNEL.sendToServer(new C2SSharedVeinsPacket(one));
    }

    public static void receive(int flags, List<SharedVeinEntry> entries) {
        if (!enabledByUserAndServer()) return;

        if ((flags & S2CSharedVeinsPacket.FIRST) != 0) SERVER_KEYS.clear();
        for (SharedVeinEntry e : entries) SERVER_KEYS.add(e.key());
        GtVeinRegistry.addSharedBatch(entries);

        if ((flags & S2CSharedVeinsPacket.LAST) != 0) {
            backfill();
            SERVER_KEYS.clear();
        }
    }

    private static void backfill() {
        if (!active()) return;
        List<SharedVeinEntry> missing = new ArrayList<>();
        for (SharedVeinEntry local : GtVeinRegistry.snapshotAll()) {
            if (!SERVER_KEYS.contains(local.key())) missing.add(local);
        }
        for (int from = 0; from < missing.size(); from += C2SSharedVeinsPacket.MAX_ENTRIES) {
            List<SharedVeinEntry> batch = new ArrayList<>(missing.subList(from,
                    Math.min(missing.size(), from + C2SSharedVeinsPacket.MAX_ENTRIES)));
            SolarisNetwork.CHANNEL.sendToServer(new C2SSharedVeinsPacket(batch));
        }
    }
}
