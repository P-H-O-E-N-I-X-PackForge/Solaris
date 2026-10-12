package net.phoenixvine.solaris.server.exploration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class RateLimiter {

    private static final double BURST_SECONDS = 5.0;

    private final Map<UUID, double[]> buckets = new HashMap<>();

    int take(UUID player, int wanted, int nowTick, double perSecond) {
        double capacity = perSecond * BURST_SECONDS;
        double[] bucket = buckets.computeIfAbsent(player, p -> new double[] { capacity, nowTick });

        double elapsedSeconds = Math.max(0, nowTick - bucket[1]) / 20.0;
        bucket[0] = Math.min(capacity, bucket[0] + elapsedSeconds * perSecond);
        bucket[1] = nowTick;

        int granted = (int) Math.min(wanted, Math.floor(bucket[0]));
        bucket[0] -= granted;
        return granted;
    }

    void forget(UUID player) {
        buckets.remove(player);
    }
}
