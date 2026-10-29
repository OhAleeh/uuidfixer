# UUID Fixer v2.0.1 - 26.3

**Requires:** Minecraft Java 26.3 · Fabric Loader ≥ 0.19.0 · Fabric API

## What's new
- Added support for Minecraft 26.3.
- Persistent cache is now saved on every lookup, so verified UUIDs survive crashes and forced restarts.
- Logs a clear warning at startup when the server runs with `online-mode=true`, where this mod has no effect.

## Installation
Drop `uuidfixer-2.0.1 - 26.3.jar` into your server's `mods` folder, replacing any previous version. Your existing `config/uuidfixer/cache.json` is kept and compatible.
