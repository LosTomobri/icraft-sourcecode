package com.icraft.client;

import com.icraft.item.SmartphoneItem;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paso 2/4 de la migración: antes se auto-registraba contra el event bus
 * nativo de NeoForge (@EventBusSubscriber + ClientPlayerNetworkEvent /
 * ClientTickEvent). Ahora expone un register() cross-loader (Architectury
 * ClientPlayerEvent + ClientTickEvent) que cada loader llama una sola vez
 * durante su init de cliente.
 */
public final class PhoneOpenTracker {

    private static final Set<UUID> OPEN_PLAYERS = ConcurrentHashMap.newKeySet();
    private static boolean registered = false;

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

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> OPEN_PLAYERS.clear());
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> PhoneScreen.ensureReadIdsLoaded());
        ClientTickEvent.CLIENT_POST.register(PhoneOpenTracker::onClientTick);
    }

    private static void onClientTick(Minecraft mc) {
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
