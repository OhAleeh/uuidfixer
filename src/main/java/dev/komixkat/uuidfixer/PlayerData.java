package dev.komixkat.uuidfixer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

final class PlayerData {
    private static final Pattern SAFE_HEX = Pattern.compile("[0-9a-fA-F]{8,64}");

    private PlayerData() {}

    static Path playerDataDir() {
        MinecraftServer server = ServerHolder.get();
        if (server == null) return null;
        try {
            return server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True if the string looks like a UUID or an unpadded UUID hex string
     * (only hex digits, no separators beyond dashes / hyphens spaced out).
     */
    static boolean looksLikeUuid(String s) {
        String t = s.trim().replace("-", "");
        return t.matches("[0-9a-fA-F]{32}");
    }

    /**
     * Accepts uuid with dashes, without dashes, and returns canonical dashed UUID or null.
     */
    static UUID parseUuid(String s) {
        String t = s.trim();
        try {
            return UUID.fromString(t);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        if (t.matches("[0-9a-fA-F]{32}")) {
            String dashed = t.replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})", "$1-$2-$3-$4-$5");
            try {
                return UUID.fromString(dashed);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    static String hexNoDashes(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    /**
     * The offline-mode UUID that vanilla would assign to the given name.
     */
    static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    static List<Path> findFilesForHex(String hexNoDashes) {
        Path dir = playerDataDir();
        List<Path> matches = new ArrayList<>();
        if (dir == null || !SAFE_HEX.matcher(hexNoDashes).matches()) return matches;
        for (String suffix : new String[]{".dat", ".dat_old", ".json"}) {
            Path f = dir.resolve(hexNoDashes + suffix);
            if (Files.exists(f)) matches.add(f);
        }
        return matches;
    }

    static List<Path> listDatFiles() {
        Path dir = playerDataDir();
        List<Path> files = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return files;
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> {
                      String name = p.getFileName().toString();
                      return name.endsWith(".dat") && !name.endsWith(".dat_old");
                  })
                  .forEach(files::add);
        } catch (Exception e) {
            UuidFixerMod.LOGGER.warn("[uuidfixer] could not list playerdata dir: {}", e.toString());
        }
        return files;
    }

    static boolean offlineDataExists(String username) {
        return !findFilesForHex(hexNoDashes(offlineUuid(username))).isEmpty();
    }

    static void warnIfDuplicateRisk(String username, String reason) {
        if (!offlineDataExists(username)) return;
        UuidFixerMod.LOGGER.warn(
            "[uuidfixer] '{}' is being assigned the OFFLINE uuid because {} but a playerdata file for that offline uuid already exists! " +
            "This can duplicate the save / reset their inventory. " +
            "Fix it with /uuidfixer set <username> <mojang-uuid>, or remove the stale file with /uuidfixer playerdata list.",
            username, reason);
    }
}
