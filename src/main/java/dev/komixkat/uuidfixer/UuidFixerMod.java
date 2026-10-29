package dev.komixkat.uuidfixer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UuidFixerMod implements ModInitializer {
    public static final String MOD_ID = "uuidfixer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> UuidCache.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> UuidCache.save());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (server instanceof DedicatedServer dedicated && dedicated.getProperties().onlineMode) {
                LOGGER.warn("[uuidfixer] the server is running with online-mode=true; this mod has no effect. Set online-mode=false in server.properties to use it.");
            }
        });
        LOGGER.info("[{}] loaded", MOD_ID);
    }
}
