package dev.komixkat.uuidfixer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class UuidCache {
    private static final long POSITIVE_TTL_MILLIS = Long.MAX_VALUE; // verified UUIDs never expire
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
    private static final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    private UuidCache() {}

    static final class Entry {
        final java.util.UUID uuid;
        final long timestamp;
        final Boolean manual; // nullable for entries written before this field existed
        Entry(java.util.UUID uuid, long timestamp) {
            this(uuid, timestamp, Boolean.FALSE);
        }
        Entry(java.util.UUID uuid, long timestamp, Boolean manual) {
            this.uuid = uuid;
            this.timestamp = timestamp;
            this.manual = manual == null ? Boolean.FALSE : manual;
        }
    }

    static Entry peek(String username) {
        return cache.get(key(username));
    }

    static java.util.UUID get(String username) {
        String key = key(username);
        Entry e = cache.get(key);
        if (e == null) return null;
        if (e.uuid != null) return e.uuid; // previously verified UUID persists
        if (System.currentTimeMillis() - e.timestamp <= NEGATIVE_TTL_MILLIS) return null; // cached miss
        return null; // expired negative; treated as unknown
    }

    static boolean has(String username) {
        String key = key(username);
        Entry e = cache.get(key);
        if (e == null) return false;
        if (e.uuid != null) return true;
        return System.currentTimeMillis() - e.timestamp <= NEGATIVE_TTL_MILLIS;
    }

    static void put(String username, java.util.UUID uuid) {
        cache.put(key(username), new Entry(uuid, System.currentTimeMillis()));
        save();
    }

    static void putManual(String username, java.util.UUID uuid) {
        cache.put(key(username), new Entry(uuid, System.currentTimeMillis(), Boolean.TRUE));
        save();
    }

    static boolean remove(String username) {
        return cache.remove(key(username)) != null;
    }

    static void reload() {
        cache.clear();
        load();
    }

    static int size() {
        return cache.size();
    }

    static int manualCount() {
        int n = 0;
        for (Entry e : cache.values()) {
            if (e.uuid != null && Boolean.TRUE.equals(e.manual)) n++;
        }
        return n;
    }

    static int negativeCount() {
        int n = 0;
        for (Entry e : cache.values()) {
            if (e.uuid == null) n++;
        }
        return n;
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static Path cacheFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("uuidfixer").resolve("cache.json");
    }

    static void load() {
        Path file = cacheFile();
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Entry> loaded = GSON.fromJson(r, MAP_TYPE);
            if (loaded != null) cache.putAll(loaded);
        } catch (Exception e) {
            UuidFixerMod.LOGGER.warn("[uuidfixer] failed to read cache.json: {}", e.toString());
        }
    }

    static void save() {
        Path file = cacheFile();
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(cache, MAP_TYPE, w);
            }
        } catch (Exception e) {
            UuidFixerMod.LOGGER.warn("[uuidfixer] failed to save cache.json: {}", e.toString());
        }
    }
}
