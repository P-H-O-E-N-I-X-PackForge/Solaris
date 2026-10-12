package net.phoenixvine.solaris.client.color;

import net.phoenixvine.solaris.sharing.SharedChunkFormat;

import java.nio.ByteBuffer;

public final class SharedChunkCodec {

    private SharedChunkCodec() {}

    public static byte[] encode(PersistentChunkStore.Entry entry) {
        ByteBuffer buf = ByteBuffer.allocate(SharedChunkFormat.RAW_SIZE);
        buf.put((byte) SharedChunkFormat.VERSION);
        for (int v : entry.pixels) buf.putInt(v);
        for (int v : entry.heights) buf.putInt(v);
        putBits(buf, entry.water);
        for (int v : entry.waterTint) buf.putInt(v);
        for (int v : entry.waterDepth) buf.putInt(v);
        putBits(buf, entry.waterOcean);
        putBits(buf, entry.foliage);
        return SharedChunkFormat.deflate(buf.array());
    }

    public static PersistentChunkStore.Entry decode(byte[] blob) {
        byte[] raw = SharedChunkFormat.inflateChecked(blob);
        if (raw == null) return null;

        ByteBuffer buf = ByteBuffer.wrap(raw);
        buf.get();
        int[] pixels = readInts(buf);
        int[] heights = readInts(buf);
        boolean[] water = readBits(buf);
        int[] waterTint = readInts(buf);
        int[] waterDepth = readInts(buf);
        boolean[] waterOcean = readBits(buf);
        boolean[] foliage = readBits(buf);
        return new PersistentChunkStore.Entry(pixels, heights, water, waterTint, waterDepth, waterOcean, foliage,
                System.currentTimeMillis());
    }

    private static int[] readInts(ByteBuffer buf) {
        int[] out = new int[256];
        for (int i = 0; i < 256; i++) out[i] = buf.getInt();
        return out;
    }

    private static void putBits(ByteBuffer buf, boolean[] bits) {
        for (int p = 0; p < 256; p += 8) {
            int packed = 0;
            for (int bit = 0; bit < 8; bit++) {
                if (bits[p + bit]) packed |= 1 << bit;
            }
            buf.put((byte) packed);
        }
    }

    private static boolean[] readBits(ByteBuffer buf) {
        boolean[] out = new boolean[256];
        for (int p = 0; p < 256; p += 8) {
            int packed = buf.get() & 0xFF;
            for (int bit = 0; bit < 8; bit++) out[p + bit] = (packed & (1 << bit)) != 0;
        }
        return out;
    }
}
