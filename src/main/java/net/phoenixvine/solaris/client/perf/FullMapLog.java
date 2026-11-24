package net.phoenixvine.solaris.client.perf;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;

import org.apache.logging.log4j.message.ParameterizedMessage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
public final class FullMapLog {

    private static final long SUMMARY_INTERVAL_MS = 10_000L;
    private static final long START_NANOS = System.nanoTime();

    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();
    private static BufferedWriter writer;
    private static boolean writerFailed = false;
    private static long lastSummaryMillis = System.currentTimeMillis();
    private static long lastSnapshotTotal = 0;

    private FullMapLog() {}

    public static boolean on() {
        return SolarisConfig.SPEC.isLoaded() && SolarisConfig.MAP_DEBUG_LOGGING.get();
    }

    public static void log(String category, String pattern, Object... args) {
        if (!on()) return;
        String body = args == null || args.length == 0 ? pattern : ParameterizedMessage.format(pattern, args);
        long ms = (System.nanoTime() - START_NANOS) / 1_000_000L;
        String line = "[+" + ms + "ms][" + Thread.currentThread().getName() + "][" + category + "] " + body;
        PhoenixSolaris.LOGGER.info("[Solaris map/{}] {}", category, body);
        write(line);
    }

    public static void count(String key) {
        if (!on()) return;
        COUNTERS.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public static void count(String key, long amount) {
        if (!on()) return;
        COUNTERS.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(amount);
    }

    private static synchronized void write(String line) {
        if (writerFailed) return;
        try {
            if (writer == null) {
                Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
                Files.createDirectories(dir);
                writer = Files.newBufferedWriter(dir.resolve("solaris-map-debug.log"), StandardCharsets.UTF_8);
                writer.write("# Solaris map debug log opened " + LocalDateTime.now() + "\n");
            }
            writer.write(line);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            writerFailed = true;
            PhoenixSolaris.LOGGER.warn("[Solaris map] could not write solaris-map-debug.log, file logging disabled",
                    e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!on()) return;
        long now = System.currentTimeMillis();
        if (now - lastSummaryMillis < SUMMARY_INTERVAL_MS) return;
        lastSummaryMillis = now;

        long total = 0;
        for (AtomicLong v : COUNTERS.values()) total += v.get();
        if (total == lastSnapshotTotal) return;
        lastSnapshotTotal = total;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AtomicLong> e : new TreeMap<>(COUNTERS).entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue().get()).append(' ');
        }
        log("summary", "counters (cumulative): {}", sb.toString().trim());
    }
}
