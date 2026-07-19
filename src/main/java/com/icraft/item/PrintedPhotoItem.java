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

        // Solo paredes
        if (face == Direction.DOWN || face == Direction.UP) {
            return InteractionResult.FAIL;
        }

        String filename = getFilename(stack);
        if (filename.isEmpty()) {
            if (player != null)
                player.displayClientMessage(Component.literal("§cEsta foto no tiene imagen."), true);
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            // La posición del cuadro es el bloque adyacente a la cara clickeada
            BlockPos framePos = clicked.relative(face);

            // Verificar que el espacio esté libre y que haya un bloque sólido detrás
            if (!level.getBlockState(framePos).isAir() &&
                !level.getBlockState(framePos).canBeReplaced()) {
                return InteractionResult.FAIL;
            }

            // Verificar que el bloque de anclaje tenga cara sólida
            BlockPos anchorPos = framePos.relative(face.getOpposite());
            if (!level.getBlockState(anchorPos).isFaceSturdy(level, anchorPos,
                    face, net.minecraft.world.level.block.SupportType.RIGID)) {
                // Intentar igual con FULL como fallback
                if (!level.getBlockState(anchorPos).isFaceSturdy(level, anchorPos,
                        face, net.minecraft.world.level.block.SupportType.FULL)) {
                    if (player != null)
                        player.displayClientMessage(
                            Component.literal("§cNecesita una pared sólida detrás."), true);
                    return InteractionResult.FAIL;
                }
            }

            com.icraft.entity.PhotoFrameEntity frame =
                    new com.icraft.entity.PhotoFrameEntity(level, framePos, face, filename);

            // Forzar la posición exacta antes de agregar al mundo
            frame.moveTo(framePos.getX() + 0.5,
                         framePos.getY() + 0.5,
                         framePos.getZ() + 0.5);

            if (level.addFreshEntity(frame)) {
                frame.playPlacementSound();
                if (player != null && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.displayClientMessage(
                    Component.literal("§aFoto colgada."), true);
            } else {
                if (player != null)
                    player.displayClientMessage(
                        Component.literal("§cNo se pudo colocar el cuadro."), true);
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
            lines.add(Component.literal("§8Click derecho en una pared para colgar"));
        } else {
            lines.add(Component.literal("§7Papel fotográfico sin revelar"));
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
