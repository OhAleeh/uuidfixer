# UUID Fixer — Fork (Persistent Cache Fix)

A non-official fork of [komixkat/uuidfixer](https://github.com/komixkat/uuidfixer) for Minecraft 26.2 (Fabric).

## What this fork fixes

The original mod could replace a previously verified Mojang UUID with an offline UUID when the Mojang API timed out or returned a transient error. This caused duplicate player `.dat` files and apparently empty inventories.

This fork changes the caching strategy so that:

- A verified Mojang UUID is **never expired** and is reused even when the API is unreachable.
- Temporary failures (timeout, HTTP 5xx/429, network errors) **do not overwrite** the cached UUID and are not cached as negative results.
- Valid `404`/`204` responses are distinguished from temporary errors. If a verified UUID already exists, it is preserved even on `404`/`204`.
- UUID resolution is serialized per username, so simultaneous logins of the same player always use the same canonical identity.
- Clear log lines distinguish cache hits, timeouts, negative API responses, and successful resolutions.

## Compatibility

- Minecraft Java 26.2
- Fabric Loader >= 0.19.0
- Fabric API
- Java 21+

## Usage

Drop `uuidfixer-26.2.jar` (patched) into your `mods` folder, replacing the original. Existing `config/uuidfixer/cache.json` files are compatible; verified UUIDs will now persist across restarts and API outages.

## License

MIT (same as upstream).