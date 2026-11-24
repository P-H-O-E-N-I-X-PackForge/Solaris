package net.phoenixvine.solaris.client;

import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.color.PersistentCaveStore;
import net.phoenixvine.solaris.client.color.PersistentChunkStore;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SolarisDataCommands {

    private SolarisDataCommands() {}

    private static List<String> exports() {
        Path dir = SolarisStorage.exportsDir();
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".zip")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        SuggestionProvider<CommandSourceStack> names = (ctx, b) -> SharedSuggestionProvider.suggest(exports(), b);
        event.getDispatcher().register(Commands.literal("solarismap").then(Commands.literal("data")
                .then(Commands.literal("where").executes(ctx -> where(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("export").executes(ctx -> export(ctx.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> export(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("import").then(Commands.argument("name", StringArgumentType.string())
                        .suggests(names)
                        .executes(ctx -> importZip(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))
                        .then(Commands.argument("replace", BoolArgumentType.bool())
                                .executes(ctx -> importZip(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                        BoolArgumentType.getBool(ctx, "replace"))))))));
    }

    private static void say(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal("\u00a76[Solaris] \u00a77" + text));
    }

    private static int where(CommandSourceStack source) {
        String key = WaypointManager.currentWorldKey();
        if (key == null) {
            say(source, "You are not in a world.");
            return 0;
        }
        say(source, "This world's map is kept in " + SolarisStorage.rootFor(key).toAbsolutePath().normalize());
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<String> found = exports();
        if (found.isEmpty()) {
            say(source, "No exports yet. They go in " + SolarisStorage.exportsDir().toAbsolutePath().normalize());
            return 0;
        }
        say(source, found.size() + " export(s) in " + SolarisStorage.exportsDir().toAbsolutePath().normalize() + ":");
        found.forEach(n -> say(source, " - " + n));
        return found.size();
    }

    private static int export(CommandSourceStack source, String name) {
        String key = WaypointManager.currentWorldKey();
        if (key == null) {
            say(source, "You are not in a world.");
            return 0;
        }
        String fileName = name != null ? name : SolarisStorage.safe(key) + "-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        PersistentChunkStore.saveDirtyAsync();
        PersistentCaveStore.saveDirtyAsync();
        say(source, "Exporting...");
        Util.ioPool().execute(() -> {
            try {
                Path zip = SolarisStorage.export(key, fileName);
                net.minecraft.client.Minecraft.getInstance().execute(() -> say(source,
                        "Exported to " + zip.toAbsolutePath().normalize() +
                                ". Anything explored in the last few seconds may not be in it yet."));
            } catch (IOException e) {
                PhoenixSolaris.LOGGER.warn("[Solaris] Could not export the map", e);
                net.minecraft.client.Minecraft.getInstance().execute(() -> say(source,
                        "Could not export: " + e.getMessage()));
            }
        });
        return 1;
    }

    private static int importZip(CommandSourceStack source, String name, boolean replace) {
        String key = WaypointManager.currentWorldKey();
        if (key == null) {
            say(source, "You are not in a world.");
            return 0;
        }
        Path zip = SolarisStorage.exportsDir().resolve(name.endsWith(".zip") ? name : name + ".zip");
        if (!Files.isRegularFile(zip)) {
            say(source, "No such export: " + zip.toAbsolutePath().normalize() + " (see /solarismap data list)");
            return 0;
        }
        try {
            SolarisStorage.queueImport(key, zip, replace);
        } catch (IOException e) {
            say(source, "Could not queue the import: " + e.getMessage());
            return 0;
        }
        say(source, "Import queued (" + (replace ? "replacing what is there" : "adding only what is missing") +
                "). Leave this world and open it again to apply it.");
        return 1;
    }
}
