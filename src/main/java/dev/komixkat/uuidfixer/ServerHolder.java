package dev.komixkat.uuidfixer;

import net.minecraft.server.MinecraftServer;

final class ServerHolder {
    private static volatile MinecraftServer server;

    private ServerHolder() {}

    static void set(MinecraftServer s) {
        server = s;
    }

    static void clear() {
        server = null;
    }

    static MinecraftServer get() {
        return server;
    }
}
