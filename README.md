# UUID Fixer — Fork (Persistent Cache Fix)

A non-official fork of [komixkat/uuidfixer](https://github.com/komixkat/uuidfixer) for Minecraft 26.3 (Fabric).

**Latest release:** [v2.0.1 - 26.3](https://github.com/OhAleeh/uuidfixer/releases/latest) (`uuidfixer-2.0.1-26.3.jar`)

## What this fork fixes

The original mod could replace a previously verified Mojang UUID with an offline UUID when the Mojang API timed out or returned a transient error. This caused duplicate player `.dat` files and apparently empty inventories.

This fork changes the caching strategy so that:

- A verified Mojang UUID is **never expired** and is reused even when the API is unreachable.
- Temporary failures (timeout, HTTP 5xx/429, network errors) **do not overwrite** the cached UUID and are not cached as negative results.
- Valid `404`/`204` responses are distinguished from temporary errors. If a verified UUID already exists, it is preserved even on `404`/`204`.
- UUID resolution is serialized per username, so simultaneous logins of the same player always use the same canonical identity.
- Clear log lines distinguish cache hits, timeouts, negative API responses, and successful resolutions.

## What's new in 2.0.1

- Added support for **Minecraft 26.3**.
- The cache is now **saved on every lookup**, so verified UUIDs survive crashes and forced restarts (not just graceful `/stop`).
- At startup, the mod **logs a clear warning** when the server runs with `online-mode=true` — in that mode the mod has no effect, since the vanilla login flow never calls offline UUID resolution.

## Compatibility

- Minecraft Java 26.3
- Fabric Loader >= 0.19.0
- Fabric API
- Java 25 (build toolchain; Java 21+ at runtime)

> **Note:** this mod only does something on servers with `online-mode=false`. Behind proxies (Velocity/BungeeCord) UUIDs are usually assigned by the proxy, so the mod must run on the proxy if needed.

## Commands (v2.0.2)

All commands require admin permission (vanilla permission level 3+):

| Command | What it does |
|---|---|
| `/uuidfixer status` | Cache summary (entries, manual overrides, negative entries) |
| `/uuidfixer status <username>` | How is this player currently resolved? |
| `/uuidfixer check <username>` | Query the Mojang API now, ignoring the cache |
| `/uuidfixer set <username> <uuid>` | **Force** username → UUID (manual override, saved to cache.json) |
| `/uuidfixer clear <username>` | Remove the cache entry, force a re-resolve on next join |
| `/uuidfixer reload` | Reload `config/uuidfixer/cache.json` from disk |
| `/uuidfixer playerdata list [prefix]` | List `playerdata` files (filter by uuid or username) |
| `/uuidfixer playerdata delete <uuid>` | Delete a stale player's `.dat`/`.json` files (⚠ back up the world first) |

Typical repair flow when a premium player got an offline UUID: run `/uuidfixer check <name>` to see what Mojang says, then `/uuidfixer set <name> <mojang-uuid>`, then delete the stale offline `.dat` with `/uuidfixer playerdata delete <offline-uuid>`, then let the player rejoin.

The mod also **logs a clear warning** the moment a (possibly premium) player is about to be assigned an offline UUID while an existing `playerdata` file for that offline UUID already exists, with instructions on how to repair it.

## Usage

Drop `uuidfixer-2.0.1-26.3.jar` (from [releases](https://github.com/OhAleeh/uuidfixer/releases/latest)) into your `mods` folder, replacing the original. Existing `config/uuidfixer/cache.json` files are compatible; verified UUIDs persist across restarts and API outages.

## License

MIT (same as upstream).
