package com.icraft.init;

import com.icraft.ICraftMod;
import com.icraft.item.PrintedPhotoItem;
import com.icraft.item.SmartphoneItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ICraftMod.MODID);

    // === MAIN PHONE ITEM ===
    public static final DeferredItem<SmartphoneItem> SMARTPHONE =
            ITEMS.register("smartphone", () -> new SmartphoneItem(
                    new Item.Properties()
                            .stacksTo(1)
                            .durability(0)
            ));

    // === FOTO IMPRESA (colocable como cuadro) ===
    public static final DeferredItem<PrintedPhotoItem> PRINTED_PHOTO =
            ITEMS.register("printed_photo", () -> new PrintedPhotoItem(
                    new Item.Properties()
                            .stacksTo(16)
            ));

    // === BLOCK ITEM DE LA IMPRESORA ===
    public static final DeferredItem<BlockItem> PRINTER =
            ITEMS.register("printer", () -> new BlockItem(
                    ModBlocks.PRINTER.get(),
                    new Item.Properties()
            ));
}
