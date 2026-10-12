package net.phoenixvine.solaris.common;

import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class SharedChunkFormat {

    public static final int VERSION = 1;
    public static final int RAW_SIZE = 1 + 256 * 4 + 256 * 4 + 32 + 256 * 4 + 256 * 4 + 32 + 32;
    public static final int MAX_BLOB_BYTES = 4096;

    public static final int WINDOW = 64;

    private SharedChunkFormat() {}

    public static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(6);
        try {
            deflater.setInput(raw);
            deflater.finish();
            byte[] buffer = new byte[raw.length + 64];
            int n = deflater.deflate(buffer);
            return Arrays.copyOf(buffer, n);
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflateChecked(byte[] blob) {
        if (blob == null || blob.length == 0 || blob.length > MAX_BLOB_BYTES) return null;

        Inflater inflater = new Inflater();
        try {
            inflater.setInput(blob);
            byte[] out = new byte[RAW_SIZE + 1];
            int total = 0;
            for (int guard = 0; guard < 64 && !inflater.finished(); guard++) {
                int n = inflater.inflate(out, total, out.length - total);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                total += n;
                if (total > RAW_SIZE) return null;
            }
            if (!inflater.finished() || total != RAW_SIZE || out[0] != VERSION) return null;
            return Arrays.copyOf(out, RAW_SIZE);
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
