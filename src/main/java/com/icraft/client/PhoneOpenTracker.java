package com.icraft.client;

import com.icraft.item.SmartphoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado cliente: guarda qué jugadores (por UUID) tienen el celular abierto
 * en este momento, según lo que el servidor nos va informando vía
 * {@link com.icraft.network.PhoneOpenSyncPacket}.
 *
 * Lo usa la item property "icraft:open" (ver ClientSetup) para decidir
 * si tiene que dibujar el modelo "abierto" en la mano de OTROS jugadores.
 * Para el jugador local no se usa este set: se chequea directo si
 * Minecraft.getInstance().screen es un PhoneScreen, así no hay delay de red.
 */
public final class PhoneOpenTracker {

    private static final Set<UUID> OPEN_PLAYERS = ConcurrentHashMap.newKeySet();

    private PhoneOpenTracker() {}

    public static void markOpen(UUID playerId) {
        OPEN_PLAYERS.add(playerId);
    }

    public static void markClosed(UUID playerId) {
        OPEN_PLAYERS.remove(playerId);
    }

    public static boolean isOpen(UUID playerId) {
        return OPEN_PLAYERS.contains(playerId);
    }

    @EventBusSubscriber(modid = "icraft", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class Events {
        // Al desconectar (cambiar de mundo/servidor), limpiamos todo para
        // no arrastrar estados viejos de una sesión anterior.
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            OPEN_PLAYERS.clear();
        }

        /**
         * Cada tick del cliente: si el celular local está abierto pero,
         * por la lógica vanilla de soltar el click derecho, el jugador
         * dejó de estar "usando" el ítem, lo reactivamos para que el
         * brazo se mantenga levantado (animación tipo catalejo, ver
         * SmartphoneItem) mientras la pantalla siga abierta. Casi siempre
         * es un no-op: startUsingItem() no hace nada si ya está en uso.
         */
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;
            if (!(mc.screen instanceof PhoneScreen)) return;
            if (player.isUsingItem()) return;

            InteractionHand hand = handHoldingSmartphone(player);
            if (hand != null) {
                player.startUsingItem(hand);
            }
        }

        private static InteractionHand handHoldingSmartphone(Player player) {
            if (player.getMainHandItem().getItem() instanceof SmartphoneItem) return InteractionHand.MAIN_HAND;
            if (player.getOffhandItem().getItem() instanceof SmartphoneItem) return InteractionHand.OFF_HAND;
            return null;
        }
    }
}
