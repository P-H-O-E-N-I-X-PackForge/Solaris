package net.phoenixvine.solaris.api;

import net.phoenixvine.solaris.api.claims.ClaimProvider;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SolarisClaimsAPI {

    private static final Map<String, ClaimProvider> PROVIDERS = new ConcurrentHashMap<>();

    private SolarisClaimsAPI() {}

    public static void registerProvider(String id, ClaimProvider provider) {
        PROVIDERS.put(id, provider);
    }

    public static void unregisterProvider(String id) {
        PROVIDERS.remove(id);
    }

    public static Collection<ClaimProvider> providers() {
        return PROVIDERS.values();
    }
}
