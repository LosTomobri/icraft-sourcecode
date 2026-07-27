package com.icraft.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Paso 4 de la migración — contrato común para los eventos de servidor que
 * hoy están suscriptos directo en {@code ICraftMod} (neoforge) vía
 * {@code @SubscribeEvent}/{@code NeoForge.EVENT_BUS}.
 * <p>
 * Solo usa tipos vanilla ({@link ServerPlayer}, {@link MinecraftServer}), por
 * eso vive en {@code common}. La lógica real (que hoy depende de
 * {@code PhoneServerHandler}, todavía en {@code neoforge/} — ver paso 6) la
 * sigue implementando cada loader:
 * <ul>
 *   <li>{@code neoforge/.../event/ICraftServerEventsNeoForge.java} — la
 *       lógica movida tal cual desde {@code ICraftMod}, sin cambios de
 *       comportamiento.</li>
 *   <li>{@code fabric/.../event/ICraftServerEventsFabric.java} — pendiente:
 *       necesita que {@code PhoneServerHandler} exista del lado de
 *       Fabric/common (paso 6) antes de poder implementar algo real.</li>
 * </ul>
 * Cada método corresponde 1:1 a un handler que hoy vive en {@code ICraftMod}:
 * <ul>
 *   <li>{@link #onPlayerJoin} ↔ {@code onPlayerLogin}
 *       ({@code PlayerEvent.PlayerLoggedInEvent})</li>
 *   <li>{@link #onPlayerLeave} ↔ {@code onPlayerLogout}
 *       ({@code PlayerEvent.PlayerLoggedOutEvent})</li>
 *   <li>{@link #onServerChat} ↔ {@code onServerChat}
 *       ({@code ServerChatEvent})</li>
 *   <li>{@link #onServerTick} ↔ {@code onServerTick}
 *       ({@code ServerTickEvent.Post}), incluye el auto-guardado periódico</li>
 *   <li>{@link #onServerStopping} ↔ {@code onServerStopping}
 *       ({@code ServerStoppingEvent})</li>
 * </ul>
 */
public interface ICraftServerEvents {

    /** Se llama cuando un jugador entra al servidor. */
    void onPlayerJoin(ServerPlayer player, MinecraftServer server);

    /** Se llama cuando un jugador sale del servidor. */
    void onPlayerLeave(ServerPlayer player, MinecraftServer server);

    /**
     * Se llama cuando un jugador manda un mensaje de chat vanilla.
     * <p>
     * Si el chat vanilla está deshabilitado para ese servidor, la
     * implementación debe avisarle al jugador (mensaje traducible
     * {@code icraft.chat.use_phone_msg}) y devolver {@code true} para que el
     * adaptador de cada loader cancele el evento.
     *
     * @return {@code true} si hay que cancelar el mensaje de chat vanilla.
     */
    boolean onServerChat(ServerPlayer sender);

    /**
     * Se llama en cada tick de servidor. Incluye las animaciones de brazo del
     * teléfono, el ring de llamadas, y el auto-guardado periódico de datos.
     */
    void onServerTick(MinecraftServer server);

    /** Se llama cuando el servidor se está deteniendo. Guarda todos los datos. */
    void onServerStopping(MinecraftServer server);
}
