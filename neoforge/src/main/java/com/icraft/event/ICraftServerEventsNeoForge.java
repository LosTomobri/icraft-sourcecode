package com.icraft.event;

import com.icraft.ICraftConstants;
import com.icraft.server.PhoneServerHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;

/**
 * Implementación NeoForge de {@link ICraftServerEvents}.
 * <p>
 * Paso 4 de la migración: esta es exactamente la misma lógica que antes
 * vivía inline en los métodos {@code @SubscribeEvent} de {@code ICraftMod}
 * — solo se movió detrás de la interfaz común. No cambia ningún
 * comportamiento, incluido el auto-guardado periódico (que sigue siendo
 * responsabilidad del llamador vía {@link #onServerTick}, igual que antes).
 * <p>
 * Sigue viviendo en {@code neoforge/} (y no en {@code common/}) porque
 * depende de {@code PhoneServerHandler}, que todavía no migró (paso 6).
 */
public final class ICraftServerEventsNeoForge implements ICraftServerEvents {

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
