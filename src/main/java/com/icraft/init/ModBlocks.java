package com.icraft.init;

import com.icraft.ICraftMod;
import com.icraft.block.PrinterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ICraftMod.MODID);

    public static final DeferredBlock<PrinterBlock> PRINTER =
            BLOCKS.register("printer", () -> new PrinterBlock(
                    BlockBehaviour.Properties.of()
                            .strength(2.0f, 4.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            ));
}
