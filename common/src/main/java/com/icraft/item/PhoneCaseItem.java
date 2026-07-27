package com.icraft.item;

import com.icraft.client.PhoneScreen;
import com.icraft.server.PhoneServerHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PhoneCaseItem extends Item {

    private final String caseId;

    public PhoneCaseItem(String caseId, Properties properties) {
        super(properties);
        this.caseId = caseId;
    }

    public String getCaseId() {
        return caseId;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack cursorStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (!(slot.getItem().getItem() instanceof SmartphoneItem)) return false;
        if (!slot.mayPickup(player)) return false;

        if (player instanceof ServerPlayer serverPlayer) {
            PhoneServerHandler.swapPhoneCase(serverPlayer, cursorStack, caseId);
        } else if (player.level().isClientSide()) {
            PhoneScreen.applyCaseFromItem(caseId);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (!(player.getItemInHand(otherHand).getItem() instanceof SmartphoneItem)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        if (level.isClientSide()) {
            if (!PhoneScreen.applyCaseFromItem(caseId)) {
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
        } else if (player instanceof ServerPlayer serverPlayer) {
            if (!PhoneServerHandler.swapPhoneCase(serverPlayer, player.getItemInHand(hand), caseId)) {
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
