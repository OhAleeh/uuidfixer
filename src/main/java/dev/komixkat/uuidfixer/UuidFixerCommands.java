package dev.komixkat.uuidfixer;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class UuidFixerCommands {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private UuidFixerCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("uuidfixer")
                .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.ADMINS)))

                .then(Commands.literal("status")
                    .executes(ctx -> statusRoot(ctx.getSource()))
                    .then(Commands.argument("username", StringArgumentType.word())
                        .executes(ctx -> statusPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "username")))))

                .then(Commands.literal("check")
                    .then(Commands.argument("username", StringArgumentType.word())
                        .executes(ctx -> check(ctx.getSource(), StringArgumentType.getString(ctx, "username")))))

                .then(Commands.literal("set")
                    .then(Commands.argument("username", StringArgumentType.word())
                        .then(Commands.argument("uuid", StringArgumentType.word())
                            .executes(ctx -> set(ctx.getSource(),
                                StringArgumentType.getString(ctx, "username"),
                                StringArgumentType.getString(ctx, "uuid"))))))

                .then(Commands.literal("clear")
                    .then(Commands.argument("username", StringArgumentType.word())
                        .executes(ctx -> clear(ctx.getSource(), StringArgumentType.getString(ctx, "username")))))

                .then(Commands.literal("reload")
                    .executes(ctx -> reload(ctx.getSource())))

                .then(Commands.literal("playerdata")
                    .then(Commands.literal("list")
                        .executes(ctx -> listPlayerData(ctx.getSource(), null))
                        .then(Commands.argument("prefix", StringArgumentType.word())
                            .executes(ctx -> listPlayerData(ctx.getSource(), StringArgumentType.getString(ctx, "prefix")))))
                    .then(Commands.literal("delete")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                            .executes(ctx -> deletePlayerData(ctx.getSource(), StringArgumentType.getString(ctx, "uuid")))))));
        });
    }

    private static int statusRoot(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal(
            "uuidfixer: " + UuidCache.size() + " cache entr" + (UuidCache.size() == 1 ? "y" : "ies")
                + " (" + UuidCache.manualCount() + " manual, " + UuidCache.negativeCount() + " negative)"), false);
        return 1;
    }

    private static int statusPlayer(CommandSourceStack src, String username) {
        UuidCache.Entry e = UuidCache.peek(username);
        if (e == null) {
            src.sendFailure(Component.literal("uuidfixer: no cache entry for '" + username + "'. It will be resolved from the Mojang API on next join."));
            return 0;
        }
        String age = formattedAge(e.timestamp);
        if (e.uuid != null) {
            src.sendSuccess(() -> Component.literal("uuidfixer: '" + username + "' is bound to " + e.uuid
                + " (" + age + ", " + (Boolean.TRUE.equals(e.manual) ? "manual override" : "verified via Mojang API") + ")"), false);
        } else {
            long left = TimeUnit.HOURS.toMillis(1) - (System.currentTimeMillis() - e.timestamp);
            src.sendSuccess(() -> Component.literal("uuidfixer: '" + username + "' is cached as NOT FOUND on Mojang"
                + " (" + age + ", treated as offline for another " + Math.max(1, TimeUnit.MILLISECONDS.toMinutes(left)) + " min)."
                + " Use /uuidfixer set to force a UUID."), false);
        }
        return 1;
    }

    private static int check(CommandSourceStack src, String username) {
        UUID manual = null;
        UuidCache.Entry e = UuidCache.peek(username);
        if (e != null && e.uuid != null) manual = e.uuid;

        src.sendSuccess(() -> Component.literal("uuidfixer: querying Mojang API for '" + username + "'..."), false);
        UuidLookupService.CheckResult r;
        try {
            r = UuidLookupService.check(username);
        } catch (Exception ex) {
            src.sendFailure(Component.literal("uuidfixer: check failed: " + ex));
            return 0;
        }
        if (r.error() != null) {
            src.sendFailure(Component.literal("uuidfixer: Mojang API error for '" + username + "': " + r.error()
                + " (temporary failure, nothing cached)"));
            return 0;
        }
        boolean manualHold = r.manualPreserved();
        if (r.notFound()) {
            src.sendSuccess(() -> Component.literal("uuidfixer: Mojang returned 404 (name unknown or rate limited)."
                + (manualHold ? " Your manual override stays untouched." : " No positive cache entry existed before, so the player keeps the offline UUID.")), false);
        } else {
            UUID found = r.uuid();
            UuidLookupService.CheckResult rr = r;
            src.sendSuccess(() -> Component.literal("uuidfixer: Mojang maps '" + username + "' -> " + found
                + (manualHold ? " (a manual/cached verified UUID exists; it was NOT overwritten)" : " (now cached)") ), false);
            if (manualHold && manual != null && rr.uuid() != null && !manual.equals(rr.uuid())) {
                src.sendFailure(Component.literal("uuidfixer: WARNING the stored UUID (" + manual + ") differs from what Mojang returns now (" + rr.uuid() + ")"));
            }
        }
        return 1;
    }

    private static int set(CommandSourceStack src, String username, String uuidArg) {
        UUID uuid = PlayerData.parseUuid(uuidArg);
        if (uuid == null) {
            src.sendFailure(Component.literal("uuidfixer: invalid UUID '" + uuidArg + "'. Use dashed or dashed-less form."));
            return 0;
        }
        UuidCache.putManual(username, uuid);
        src.sendSuccess(() -> Component.literal("uuidfixer: '" + username + "' is now forced to " + uuid
            + " (manual, saved to cache.json and survives restarts). Effective on the player's NEXT join."), false);
        return 1;
    }

    private static int clear(CommandSourceStack src, String username) {
        if (UuidCache.remove(username)) {
            src.sendSuccess(() -> Component.literal("uuidfixer: removed the cache entry for '" + username + "'; it will be resolved again on next join."), false);
            return 1;
        }
        src.sendFailure(Component.literal("uuidfixer: no cache entry for '" + username + "'"));
        return 0;
    }

    private static int reload(CommandSourceStack src) {
        UuidCache.reload();
        src.sendSuccess(() -> Component.literal("uuidfixer: cache reloaded from disk: " + UuidCache.size() + " entries"), false);
        return 1;
    }

    private static int listPlayerData(CommandSourceStack src, String prefix) {
        List<Path> files = PlayerData.listDatFiles();
        String filterHex = null;
        if (prefix != null && !prefix.isBlank()) {
            String p = prefix.trim();
            if (PlayerData.looksLikeUuid(p)) {
                UUID u = PlayerData.parseUuid(p);
                filterHex = PlayerData.hexNoDashes(u);
            } else {
                // treat the arg as a username and use its offline uuid
                filterHex = PlayerData.hexNoDashes(PlayerData.offlineUuid(p.toLowerCase(Locale.ROOT)));
            }
        }
        final String hex = filterHex;
        src.sendSuccess(() -> Component.literal("uuidfixer: playerdata files" + (hex != null ? " (filter " + hex + ".dat)" : "") + ":"), false);
        List<Path> shown = files.stream()
            .filter(p -> hex == null || p.getFileName().toString().startsWith(hex + "."))
            .sorted(Comparator.comparing(UuidFixerCommands::lastModified).reversed())
            .limit(30)
            .toList();
        for (Path f : shown) {
            long kb = 0;
            try {
                kb = Files.size(f) / 1024;
            } catch (Exception ignored) {}
            final long kbFinal = kb;
            final Path fFinal = f;
            src.sendSuccess(() -> Component.literal("  - " + fFinal.getFileName() + " (" + kbFinal + " KB, "
                + formattedAge(lastModified(fFinal)) + ")"), false);
        }
        final long shownCount = shown.size();
        final long totalCount = files.stream().filter(p -> hex == null || p.getFileName().toString().startsWith(hex + ".")).count();
        final long allCount = files.size();
        src.sendSuccess(() -> Component.literal("uuidfixer: shown " + shownCount + " of " + totalCount
            + " matching files (playerdata has " + allCount + " .dat files in total)"), false);
        return 1;
    }

    private static int deletePlayerData(CommandSourceStack src, String uuidArg) {
        UUID uuid = PlayerData.parseUuid(uuidArg);
        if (uuid == null) {
            src.sendFailure(Component.literal("uuidfixer: invalid UUID '" + uuidArg + "'"));
            return 0;
        }
        String hex = PlayerData.hexNoDashes(uuid);
        List<Path> files = PlayerData.findFilesForHex(hex);
        if (files.isEmpty()) {
            src.sendFailure(Component.literal("uuidfixer: no playerdata files for " + uuid + " (" + hex + ".dat/.json)"));
            return 0;
        }
        int deleted = 0;
        for (Path f : files) {
            try {
                Files.delete(f);
                deleted++;
            } catch (Exception ex) {
                src.sendFailure(Component.literal("uuidfixer: could not delete " + f.getFileName() + ": " + ex));
            }
        }
        final int deletedFinal = deleted;
        src.sendSuccess(() -> Component.literal("uuidfixer: deleted " + deletedFinal + " playerdata file(s) for " + uuid
            + ". Make a world backup before doing this next time."), false);
        return deleted > 0 ? 1 : 0;
    }

    // helpers

    private static long lastModified(Path p) {
        try {
            Path f = p;
            return Files.getLastModifiedTime(f).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formattedAge(long timestamp) {
        if (timestamp <= 0) return "unknown time";
        String formatted;
        try {
            formatted = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault()).format(TS);
        } catch (DateTimeException e) {
            formatted = "?";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - timestamp);
        long hours = minutes / 60;
        return (hours > 0 ? hours + "h " : "") + (minutes % 60) + "m ago (" + formatted + ")";
    }
}
