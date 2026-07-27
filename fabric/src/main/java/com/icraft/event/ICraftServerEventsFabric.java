package com.icraft.event;

import com.icraft.ICraftConstants;
import com.icraft.server.PhoneServerHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;

/**
 * Implementación Fabric de {@link ICraftServerEvents}.
 * <p>
 * Paso 4 de la migración (lado servidor) — misma lógica que
 * {@code ICraftServerEventsNeoForge}, ahora que {@code PhoneServerHandler}
 * ya vive en {@code common/} (paso 6 resuelto). Cableada en
 * {@code ICraftModFabric.onInitialize()} contra los 4 eventos estables de
 * Fabric API:
 * <ul>
 *   <li>{@code ServerPlayConnectionEvents.JOIN}/{@code DISCONNECT} ↔
 *       {@link #onPlayerJoin}/{@link #onPlayerLeave}</li>
 *   <li>{@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} ↔
 *       {@link #onServerChat} (el callback devuelve {@code true} para
 *       PERMITIR el mensaje vanilla, al revés que
 *       {@code ICraftServerEvents#onServerChat}, que devuelve {@code true}
 *       para CANCELARLO — el adaptador en {@code ICraftModFabric} invierte
 *       el booleano)</li>
 *   <li>{@code ServerTickEvents.END_SERVER_TICK} ↔ {@link #onServerTick}</li>
 *   <li>{@code ServerLifecycleEvents.SERVER_STOPPING} ↔
 *       {@link #onServerStopping}</li>
 * </ul>
 */
public final class ICraftServerEventsFabric implements ICraftServerEvents {

    private static final Logger LOGGER = ICraftConstants.LOGGER;
    private static final int SAVE_INTERVAL_TICKS = 6000;

    private int tickCounter = 0;

    @Override
    public void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        PhoneServerHandler.onPlayerJoin(player, server);
        LOGGER.info("[iCraft] {} entró — datos cargados y unido al grupo Mundial", player.getGameProfile().getName());
    }

    @Override
    public void onPlayerLeave(ServerPlayer player, MinecraftServer server) {
        PhoneServerHandler.onPlayerLeave(player, server);
        LOGGER.info("[iCraft] {} salió — datos guardados", player.getGameProfile().getName());
    }

    @Override
    public boolean onServerChat(ServerPlayer sender) {
        MinecraftServer server = sender.getServer();
        if (server != null && PhoneServerHandler.isVanillaChatEnabled(server)) {
            return false;
        }

        sender.sendSystemMessage(Component.translatable("icraft.chat.use_phone_msg"));
        LOGGER.debug("[iCraft] Chat vanilla bloqueado para {}", sender.getGameProfile().getName());
        return true;
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        PhoneServerHandler.tickPhoneArmAnimations(server);
        PhoneServerHandler.tickCallRinging(server);

        tickCounter++;
        if (tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            PhoneServerHandler.saveAllPlayerData(server);
            LOGGER.debug("[iCraft] Auto-guardado de datos completado.");
        }
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        PhoneServerHandler.saveAllPlayerData(server);
        LOGGER.info("[iCraft] Servidor deteniéndose — todos los datos guardados.");
    }
}
