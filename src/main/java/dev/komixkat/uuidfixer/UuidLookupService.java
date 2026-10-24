package dev.komixkat.uuidfixer;

import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UuidLookupService {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private UuidLookupService() {}

    public static UUID lookup(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            UUID cached = UuidCache.get(username);
            if (cached != null) {
                UuidFixerMod.LOGGER.info("[uuidfixer] cache hit for '{}': {}", username, cached);
                return cached;
            }
            if (UuidCache.has(username)) {
                UuidFixerMod.LOGGER.info("[uuidfixer] cache hit (negative) for '{}': using offline UUID", username);
                return null;
            }

            UuidFixerMod.LOGGER.info("[uuidfixer] resolving '{}' via Mojang API", username);
            FetchResult result;
            try {
                result = fetchFromMojang(username);
            } catch (Exception e) {
                UuidFixerMod.LOGGER.warn("[uuidfixer] lookup failed for '{}', using previously verified UUID if available: {}", username, e.toString());
                // No new cache entry is written on temporary failure.
                return null;
            }

            if (result.notFound) {
                UUID existing = UuidCache.get(username);
                if (existing != null) {
                    UuidFixerMod.LOGGER.warn("[uuidfixer] Mojang API returned 404/204 for '{}', but a verified UUID exists; keeping {}", username, existing);
                    return existing;
                }
                UuidFixerMod.LOGGER.info("[uuidfixer] Mojang API returned 404/204 for '{}'; name not found, using offline UUID", username);
                UuidCache.put(username, null);
                return null;
            }

            if (result.uuid == null) {
                UuidFixerMod.LOGGER.warn("[uuidfixer] Mojang API returned an unusable response for '{}'; no UUID cached", username);
                return null;
            }

            UuidFixerMod.LOGGER.info("[uuidfixer] Mojang API resolved '{}' to {}", username, result.uuid);
            UuidCache.put(username, result.uuid);
            return result.uuid;
        }
    }

    private static FetchResult fetchFromMojang(String username) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 200) {
            return new FetchResult(parseUuid(response.body()), false);
        }
        if (status == 404 || status == 204) {
            return new FetchResult(null, true);
        }
        UuidFixerMod.LOGGER.warn("[uuidfixer] Mojang API returned {} for '{}'", status, username);
        return new FetchResult(null, false);
    }

    private static UUID parseUuid(String body) {
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            String id = obj.get("id").getAsString();
            String formatted = id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
            return UUID.fromString(formatted);
        } catch (RuntimeException e) {
            UuidFixerMod.LOGGER.warn("[uuidfixer] unexpected Mojang API response: {}", body);
            return null;
        }
    }

    private static final class FetchResult {
        final UUID uuid;
        final boolean notFound;
        FetchResult(UUID uuid, boolean notFound) {
            this.uuid = uuid;
            this.notFound = notFound;
        }
    }
}
