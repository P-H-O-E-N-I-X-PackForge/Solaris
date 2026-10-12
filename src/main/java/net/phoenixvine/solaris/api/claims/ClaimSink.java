package net.phoenixvine.solaris.api.claims;

@FunctionalInterface
public interface ClaimSink {

    void claim(int chunkX, int chunkZ, String ownerName, int argb);
}
