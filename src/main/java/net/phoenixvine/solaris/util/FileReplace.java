package net.phoenixvine.solaris.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileReplace {

    private FileReplace() {}

    public static void replace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (IOException ignored) {

        }

        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(40L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        try {
            Files.copy(temp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            if (last != null) e.addSuppressed(last);
            throw e;
        }
    }
}
