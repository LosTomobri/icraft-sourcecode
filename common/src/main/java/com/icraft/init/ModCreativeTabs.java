package com.icraft.init;

import com.icraft.ICraftConstants;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Paso 2 de la migración: ver nota en ModItems.java — ya vive en common/
 * junto con ModItems.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(ICraftConstants.MODID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> ICRAFT_TAB =
            CREATIVE_MODE_TABS.register("icraft_tab", () ->
                    CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                            .title(Component.translatable("itemGroup.icraft"))
                            .icon(() -> ModItems.SMARTPHONE.get().getDefaultInstance())
                            .displayItems((params, output) -> {
                                output.accept(ModItems.SMARTPHONE.get());
                                output.accept(ModItems.PRINTER.get());
                                output.accept(ModItems.PRINTED_PHOTO.get());
                                output.accept(ModItems.PHONE_CASE_DEFAULT.get());
                                output.accept(ModItems.PHONE_CASE_BLACK.get());
                                output.accept(ModItems.PHONE_CASE_WHITE.get());
                                output.accept(ModItems.PHONE_CASE_NEON.get());
                                output.accept(ModItems.PHONE_CASE_DIAMOND.get());
                            })
                            .build()
            );
}
