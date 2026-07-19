package com.icraft.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suprime los mensajes vanilla de "X joined the game" y "X left the game"
 * que Minecraft manda al chat cuando un jugador entra o sale del servidor.
 *
 * iCraft ya envía sus propios mensajes de join/leave al grupo Global del
 * celular (via PhoneServerHandler.broadcastToGlobalGroup con sender "Sistema"),
 * así que el mensaje vanilla queda redundante y molesto.
 *
 * == CÓMO FUNCIONA ==
 * Minecraft envía el mensaje de join/leave llamando a
 * PlayerList#broadcastSystemMessage con un Component que contiene la clave
 * de traducción "multiplayer.player.joined" o "multiplayer.player.left".
 * Este Mixin intercepta esa llamada y la cancela si el mensaje coincide
 * con alguna de esas dos claves, dejando pasar todo lo demás intacto.
 *
 * == REGISTRO ==
 * Añadir en icraft.mixins.json (array "mixins"):
 *   "MixinPlayerList"
 */
@Mixin(PlayerList.class)
public class MixinPlayerList {

    @Inject(
        method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void icraft_suppressJoinLeaveMessage(Component message, boolean overlay, CallbackInfo ci) {
        // Obtener la clave de traducción del componente (si la tiene)
        String key = message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                ? t.getKey()
                : null;

        if ("multiplayer.player.joined".equals(key)
                || "multiplayer.player.joined.renamed".equals(key)
                || "multiplayer.player.left".equals(key)) {
            ci.cancel(); // Suprimir — iCraft ya lo avisa en el celular
        }
    }
}
