package net.phoenixvine.solaris.server;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.common.SharedChunkFormat;
import net.phoenixvine.solaris.util.FileReplace;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildMapStore {

    private static final int MAGIC = 0x53474D50;
    private static final int VERSION = 1;

    public record Stored(byte[] data, long stamp) {}

    public static final class DimStore {

        final UUID guild;
        final String dimension;
        public final ConcurrentHashMap<Long, Stored> chunks = new ConcurrentHashMap<>();
        volatile boolean ready = false;
        volatile boolean dirty = false;

        DimStore(UUID guild, String dimension) {
            this.guild = guild;
            this.dimension = dimension;
        }
    }

    private static final Map<String, DimStore> STORES = new ConcurrentHashMap<>();

    private GuildMapStore() {}

    public static DimStore get(MinecraftServer server, UUID guild, String dimension) {
        String id = guild + "|" + dimension;
        DimStore store = STORES.computeIfAbsent(id, k -> {
            DimStore created = new DimStore(guild, dimension);
            Path file = fileFor(server, guild, dimension);
            if (!Files.exists(file)) {
                
                created.ready = true;
                return created;
            }
            Util.ioPool().execute(() -> {
                load(file, created);
                created.ready = true;
            });
            return created;
        });
        return store.ready ? store : null;
    }

    public static boolean putIfAbsent(DimStore store, int x, int z, byte[] data, int cap) {
        if (store.chunks.size() >= cap) return false;
        Stored previous = store.chunks.putIfAbsent(ChunkPos.asLong(x, z), new Stored(data, System.currentTimeMillis()));
        if (previous != null) return false;
        store.dirty = true;
        return true;
    }

    public static void saveDirty(MinecraftServer server, boolean synchronous) {
        for (DimStore store : STORES.values()) {
            if (!store.ready || !store.dirty) continue;
            store.dirty = false;

            List<Map.Entry<Long, Stored>> snapshot = new ArrayList<>(store.chunks.entrySet());
            Path file = fileFor(server, store.guild, store.dimension);
            if (synchronous) {
                write(file, snapshot);
            } else {
                Util.ioPool().execute(() -> write(file, snapshot));
            }
        }
    }

    public static void clear() {
        STORES.clear();
    }

    private static Path fileFor(MinecraftServer server, UUID guild, String dimension) {
        String safeDimension = dimension.replaceAll("[^a-zA-Z0-9._-]", "_");
        return server.getWorldPath(LevelResource.ROOT).resolve("solaris").resolve("guildmaps")
                .resolve(guild.toString()).resolve(safeDimension + ".dat");
    }

    private static void load(Path file, DimStore store) {
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                PhoenixSolaris.LOGGER.warn("[Solaris] Ignoring guild map file {} (unknown format)", file);
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int z = in.readInt();
                long stamp = in.readLong();
                int length = in.readInt();
                if (length < 0 || length > SharedChunkFormat.MAX_BLOB_BYTES) {
                    throw new IOException("Corrupt chunk length " + length);
                }
                byte[] data = new byte[length];
                in.readFully(data);
                store.chunks.put(ChunkPos.asLong(x, z), new Stored(data, stamp));
            }
            PhoenixSolaris.LOGGER.info("[Solaris] Loaded {} shared chunk(s) for guild {} in {}", store.chunks.size(),
                    store.guild, store.dimension);
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("[Solaris] Failed to fully load guild map file {}", file, e);
        }
    }

    private static void write(Path file, List<Map.Entry<Long, Stored>> snapshot) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(snapshot.size());
                for (Map.Entry<Long, Stored> e : snapshot) {
                    out.writeInt(ChunkPos.getX(e.getKey()));
                    out.writeInt(ChunkPos.getZ(e.getKey()));
                    out.writeLong(e.getValue().stamp());
                    out.writeInt(e.getValue().data().length);
                    out.write(e.getValue().data());
                }
            }
            FileReplace.replace(temp, file);
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("[Solaris] Failed to save guild map file {}", file, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {}
        }
    }
}
