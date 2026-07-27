package com.icraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class PrintedPhotoItem extends Item {

    public PrintedPhotoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level     = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        Direction face  = ctx.getClickedFace();
        Player player   = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        if (face == Direction.DOWN || face == Direction.UP) {
            return InteractionResult.FAIL;
        }

        String filename = getFilename(stack);
        if (filename.isEmpty()) {
            if (player != null)
                player.displayClientMessage(Component.translatable("icraft.photo.no_image_msg"), true);
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {

            BlockPos framePos = clicked.relative(face);

            if (!level.getBlockState(framePos).isAir() &&
                !level.getBlockState(framePos).canBeReplaced()) {
                return InteractionResult.FAIL;
            }

            BlockPos anchorPos = framePos.relative(face.getOpposite());
            if (!level.getBlockState(anchorPos).isFaceSturdy(level, anchorPos,
                    face, net.minecraft.world.level.block.SupportType.RIGID)) {

                if (!level.getBlockState(anchorPos).isFaceSturdy(level, anchorPos,
                        face, net.minecraft.world.level.block.SupportType.FULL)) {
                    if (player != null)
                        player.displayClientMessage(
                            Component.translatable("icraft.photo.need_wall_msg"), true);
                    return InteractionResult.FAIL;
                }
            }

            com.icraft.entity.PhotoFrameEntity frame =
                    new com.icraft.entity.PhotoFrameEntity(level, framePos, face, filename);

            frame.moveTo(framePos.getX() + 0.5,
                         framePos.getY() + 0.5,
                         framePos.getZ() + 0.5);

            if (level.addFreshEntity(frame)) {
                frame.playPlacementSound();
                if (player != null && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.displayClientMessage(
                    Component.translatable("icraft.photo.hung_msg"), true);
            } else {
                if (player != null)
                    player.displayClientMessage(
                        Component.translatable("icraft.photo.cant_place_msg"), true);
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, lines, flag);
        String filename = getFilename(stack);
        if (!filename.isEmpty()) {
            lines.add(Component.translatable("icraft.photo.tooltip_hang"));
        } else {
            lines.add(Component.translatable("icraft.photo.tooltip_undeveloped"));
        }
    }

    public static String getFilename(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag tag = data.copyTag();
        return tag.contains("photoFilename") ? tag.getString("photoFilename") : "";
    }
}
