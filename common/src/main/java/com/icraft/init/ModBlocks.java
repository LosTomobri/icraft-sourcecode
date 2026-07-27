package com.icraft.init;

import com.icraft.ICraftConstants;
import com.icraft.block.PrinterBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Paso 2 de la migración: ver nota en ModItems.java — ya vive en common/
 * (PrinterBlock ya no depende de nada NeoForge-específico tras el paso 6).
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ICraftConstants.MODID, Registries.BLOCK);

    public static final RegistrySupplier<PrinterBlock> PRINTER =
            BLOCKS.register("printer", () -> new PrinterBlock(
                    BlockBehaviour.Properties.of()
                            .strength(2.0f, 4.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            ));
}
