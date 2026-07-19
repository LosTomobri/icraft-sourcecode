package com.icraft.init;

import com.icraft.ICraftMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ICraftMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ICRAFT_TAB =
            CREATIVE_MODE_TABS.register("icraft_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.icraft"))
                            .icon(() -> ModItems.SMARTPHONE.get().getDefaultInstance())
                            .displayItems((params, output) -> {
                                output.accept(ModItems.SMARTPHONE.get());
                                output.accept(ModItems.PRINTER.get());
                                output.accept(ModItems.PRINTED_PHOTO.get());
                            })
                            .build()
            );
}
