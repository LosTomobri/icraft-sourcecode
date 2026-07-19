package com.icraft.item;

import com.icraft.client.PhoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class SmartphoneItem extends Item {

    /**
     * Duración "infinita" del uso del ítem. No se deja que termine sola:
     * se corta a mano (player.stopUsingItem()) cuando se cierra la pantalla
     * del celular — ver PhoneScreen#removed() y
     * PhoneServerHandler#broadcastPhoneOpenState(). Mientras tanto el brazo
     * se mantiene levantado, igual que pasa con el catalejo mientras se
     * sostiene el click derecho.
     */
    public static final int USE_DURATION = 72000;

    public SmartphoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Igual que SpyglassItem.use() en vanilla: arranca la animación de
        // "usar item" para levantar el brazo. Como esto corre en ambos lados
        // (cliente y servidor), el estado se sincroniza solo a los demás
        // jugadores a través del entity data estándar de LivingEntity — no
        // hace falta mandar un paquete custom para esto.
        player.startUsingItem(hand);
        if (level.isClientSide()) {
            openPhoneScreen();
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @OnlyIn(Dist.CLIENT)
    private void openPhoneScreen() {
        Minecraft.getInstance().setScreen(new PhoneScreen());
    }
}
