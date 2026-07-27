package com.icraft.client;

import com.icraft.init.ModEntityTypes;
import com.icraft.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Paso 2/4 de la migración: antes se auto-registraba contra el event bus de
 * NeoForge (@EventBusSubscriber + FMLClientSetupEvent). Ahora expone un
 * register() cross-loader que cada loader llama una sola vez durante su
 * init de cliente (onInitializeClient en Fabric, clientSetup en NeoForge).
 *
 * IMPORTANTE — por qué esto ya NO usa ClientLifecycleEvent.CLIENT_SETUP:
 * ese evento de Architectury mapea a FMLClientSetupEvent en NeoForge (corre
 * antes de que vanilla arme el mapa de EntityRenderer del
 * EntityRenderDispatcher) pero mapea a ClientLifecycleEvents.CLIENT_STARTED
 * en Fabric (corre DESPUÉS de que ese mapa ya está construido). Registrar
 * un EntityRenderer ahí funciona en NeoForge y llega tarde en Fabric — la
 * entidad se registra pero se queda sin renderer, y el juego revienta con
 * NullPointerException en EntityRenderDispatcher la primera vez que hay
 * que dibujarla (ver PhotoFrameEntity/photo_frame). Por eso ahora
 * register() ejecuta todo de forma inmediata y síncrona: ambos loaders ya
 * garantizan que su entrypoint de cliente corre antes de esa construcción,
 * así que no hace falta ningún evento intermedio para esto.
 *
 * ItemProperties.register y EntityRenderers.register son `private` en el
 * vanilla contra el que compila common/, así que el registro real se delega
 * a ClientSetupExpectPlatform, con una implementación distinta por loader.
 */
public final class ClientSetup {

    private static boolean registered = false;

    private ClientSetup() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        ClientSetupExpectPlatform.registerItemModelProperty(
                ModItems.SMARTPHONE.get(),
                ResourceLocation.fromNamespaceAndPath("icraft", "open"),
                (stack, level, entity, seed) -> {
                    if (!(entity instanceof Player player)) return 0f;
                    Minecraft minecraft = Minecraft.getInstance();
                    boolean isLocalPlayer = player == minecraft.player;
                    boolean open = isLocalPlayer
                            ? minecraft.screen instanceof PhoneScreen
                            : PhoneOpenTracker.isOpen(player.getUUID());
                    return open ? 1f : 0f;
                }
        );

        ClientSetupExpectPlatform.registerEntityRenderer(
                ModEntityTypes.PHOTO_FRAME.get(),
                PhotoFrameRenderer::new
        );
    }
}
