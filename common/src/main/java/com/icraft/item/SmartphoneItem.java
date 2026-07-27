package com.icraft.item;

import com.icraft.client.PhoneScreen;
import com.icraft.server.PhoneServerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public class SmartphoneItem extends Item {

    public static final int USE_DURATION = 72000;

    public SmartphoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack cursorStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (!(slot.getItem().getItem() instanceof PhoneCaseItem caseItem)) return false;
        if (!slot.mayPickup(player)) return false;

        if (player instanceof ServerPlayer serverPlayer) {
            PhoneServerHandler.swapPhoneCase(serverPlayer, slot.getItem(), caseItem.getCaseId());
        } else if (player.level().isClientSide()) {
            PhoneScreen.applyCaseFromItem(caseItem.getCaseId());
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (player.getItemInHand(otherHand).getItem() instanceof PhoneCaseItem caseItem) {
            if (level.isClientSide()) {
                if (!PhoneScreen.applyCaseFromItem(caseItem.getCaseId())) {
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            } else if (player instanceof ServerPlayer serverPlayer) {
                if (!PhoneServerHandler.swapPhoneCase(serverPlayer, player.getItemInHand(otherHand), caseItem.getCaseId())) {
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }

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

    @Environment(EnvType.CLIENT)
    private void openPhoneScreen() {
        Minecraft.getInstance().setScreen(new PhoneScreen());
    }
}
