package com.icraft.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    @Inject(
        method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void icraft_suppressJoinLeaveMessage(Component message, boolean overlay, CallbackInfo ci) {

        String key = message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                ? t.getKey()
                : null;

        if ("multiplayer.player.joined".equals(key)
                || "multiplayer.player.joined.renamed".equals(key)
                || "multiplayer.player.left".equals(key)) {
            ci.cancel();
        }
    }
}
