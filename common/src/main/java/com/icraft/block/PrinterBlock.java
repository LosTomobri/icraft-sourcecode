package com.icraft.block;

import com.icraft.client.PrinterScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public class PrinterBlock extends Block {

    public PrinterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            openPrinterScreen();
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Environment(EnvType.CLIENT)
    private void openPrinterScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new PrinterScreen());
    }
}
