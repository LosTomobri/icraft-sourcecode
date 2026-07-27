package com.icraft.fabric;

import com.icraft.command.ICraftCommand;
import com.icraft.event.ICraftClientEventsFabric;
import com.icraft.event.ICraftServerEventsFabric;
import com.icraft.init.ModBlocks;
import com.icraft.init.ModCreativeTabs;
import com.icraft.init.ModEntityTypes;
import com.icraft.init.ModItems;
import com.icraft.network.ModPackets;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Paso 2/3 de la migración: ModItems/ModBlocks/ModEntityTypes/ModCreativeTabs
 * y ModPackets + los 20 paquetes ya viven en common/ (ver MIGRACION.md) y
 * usan APIs cross-loader de Architectury (DeferredRegister/RegistrySupplier,
 * NetworkManager). El registro en sí es idéntico al de ICraftMod en
 * neoforge/ — se llama una sola vez durante la construcción del mod.
 *
 * Paso 4 (lado servidor): {@link ICraftServerEventsFabric} ya cablea lógica
 * real (ver esa clase) contra los 4 eventos estables de Fabric API
 * ({@code ServerPlayConnectionEvents.JOIN/DISCONNECT},
 * {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE},
 * {@code ServerTickEvents.END_SERVER_TICK},
 * {@code ServerLifecycleEvents.SERVER_STOPPING}). Los parámetros de los
 * callbacks de JOIN/DISCONNECT/ALLOW_CHAT_MESSAGE se dejan con tipo
 * implícito (lambda sin anotar) a propósito: sus tipos vienen remapeados a
 * Mojang official mappings (ver {@code loom.officialMojangMappings()} en el
 * build raíz) y no hacía falta nombrarlos — solo se usan sus métodos
 * ({@code handler.getPlayer()}, confirmado contra el javadoc oficial de
 * {@code ServerGamePacketListenerImpl}).
 *
 * Paso 4 (lado cliente): {@link ICraftClientEventsFabric} sigue siendo un
 * placeholder — cancelar el render de mano/HUD y sobrescribir el FOV no
 * tienen equivalente estable en Fabric API para 1.21.1 (requieren Mixin
 * contra clases vanilla, cuyos nombres remapeados no pude confirmar sin
 * compilar acá). Ver Javadoc de esa clase para el detalle.
 */
public final class ICraftModFabric implements ModInitializer {

    private final ICraftClientEventsFabric clientEvents = new ICraftClientEventsFabric();
    private final ICraftServerEventsFabric serverEvents = new ICraftServerEventsFabric();

    @Override
    public void onInitialize() {
        ModBlocks.BLOCKS.register();
        ModItems.ITEMS.register();
        ModCreativeTabs.CREATIVE_MODE_TABS.register();
        ModEntityTypes.ENTITY_TYPES.register();
        ModPackets.register();

        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) ->
                ICraftCommand.register(dispatcher));

        // Paso 4 (servidor): mismo comportamiento que los @SubscribeEvent de
        // ICraftMod en neoforge/, ahora contra Fabric API.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                serverEvents.onPlayerJoin(handler.getPlayer(), server));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                serverEvents.onPlayerLeave(handler.getPlayer(), server));

        // ALLOW_CHAT_MESSAGE devuelve true para PERMITIR el mensaje vanilla;
        // onServerChat devuelve true para CANCELARLO — de ahí la negación.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
                !serverEvents.onServerChat(sender));

        ServerTickEvents.END_SERVER_TICK.register(serverEvents::onServerTick);

        ServerLifecycleEvents.SERVER_STOPPING.register(serverEvents::onServerStopping);
    }
}
