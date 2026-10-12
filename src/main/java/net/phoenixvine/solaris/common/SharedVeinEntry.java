package net.phoenixvine.solaris.sharing;

public record SharedVeinEntry(String dimension, int x, int y, int z, String name, int argb, String itemId) {

    public String key() {
        return dimension + "@" + x + "," + y + "," + z;
    }
}
