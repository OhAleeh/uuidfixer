package dev.komixkat.uuidfixer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UuidFixerMod implements ModInitializer {
    public static final String MOD_ID = "uuidfixer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> UuidCache.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> UuidCache.save());
        LOGGER.info("[{}] loaded", MOD_ID);
    }
}
