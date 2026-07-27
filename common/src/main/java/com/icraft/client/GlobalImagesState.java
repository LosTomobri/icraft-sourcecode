package com.icraft.client;

import dev.architectury.event.events.client.ClientPlayerEvent;

/**
 * Paso 2/4 de la migración: antes se auto-registraba contra el event bus
 * nativo de NeoForge (@EventBusSubscriber). Ahora expone un register()
 * cross-loader (Architectury ClientPlayerEvent) que cada loader llama una
 * sola vez durante su init de cliente (ver ICraftMod#clientSetup en
 * neoforge/ e ICraftModFabricClient en fabric/).
 */
public final class GlobalImagesState {

    private static volatile boolean enabled = false;
    private static boolean registered = false;

    private GlobalImagesState() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> enabled = false);
    }
}
