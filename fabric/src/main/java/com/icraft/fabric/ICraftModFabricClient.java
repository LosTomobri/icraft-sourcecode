package com.icraft.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * Paso 2/4 de la migración: entrypoint "client" de Fabric (declarado en
 * fabric.mod.json). A diferencia de ModInitializer.onInitialize() (que
 * corre en cliente Y en servidor dedicado), ClientModInitializer.onInitializeClient()
 * SOLO corre del lado cliente — es el lugar correcto para cablear
 * GlobalImagesState/PhoneToast/PhoneOpenTracker/ClientSetup, que ahora
 * viven en common/ y exponen un register() cross-loader (Architectury),
 * igual que se hace en ICraftMod#clientSetup del lado NeoForge.
 */
public final class ICraftModFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        com.icraft.client.GlobalImagesState.register();
        com.icraft.client.PhoneToast.register();
        com.icraft.client.PhoneOpenTracker.register();
        com.icraft.client.ClientSetup.register();

        // Paso 4 (retomado): cablea onRenderAfterLevel (captura de foto
        // pendiente) contra WorldRenderEvents.LAST y deja la instancia
        // disponible para que los Mixins (FOV, y a futuro mano/HUD) puedan
        // llegar a la lógica común. Ver com.icraft.event.ICraftClientEventsFabric.
        com.icraft.event.ICraftClientEventsFabric.register();
    }
}
