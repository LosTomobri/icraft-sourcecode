package com.icraft.init;

import com.icraft.ICraftConstants;
import com.icraft.item.PhoneCaseItem;
import com.icraft.item.PrintedPhotoItem;
import com.icraft.item.SmartphoneItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * Paso 2 de la migración: registro vía Architectury Registry API
 * (dev.architectury.registry.registries.DeferredRegister) en vez del
 * DeferredRegister nativo de NeoForge.
 *
 * Ya vive en common/ (movido tras resolverse el paso 6): SmartphoneItem y
 * PhoneCaseItem dependían de PhoneScreen/PhoneServerHandler y de
 * @OnlyIn(Dist.CLIENT) de NeoForge, pero esas dependencias ya están
 * resueltas — @OnlyIn se reemplazó por @Environment(EnvType.CLIENT) donde
 * hacía falta. Registrada desde ICraftMod (NeoForge) e ICraftModFabric
 * (Fabric).
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ICraftConstants.MODID, Registries.ITEM);

    public static final RegistrySupplier<SmartphoneItem> SMARTPHONE =
            ITEMS.register("smartphone", () -> new SmartphoneItem(
                    new Item.Properties()
                            .stacksTo(1)
                            .durability(0)
            ));

    public static final RegistrySupplier<PrintedPhotoItem> PRINTED_PHOTO =
            ITEMS.register("printed_photo", () -> new PrintedPhotoItem(
                    new Item.Properties()
                            .stacksTo(16)
            ));

    public static final RegistrySupplier<BlockItem> PRINTER =
            ITEMS.register("printer", () -> new BlockItem(
                    ModBlocks.PRINTER.get(),
                    new Item.Properties()
            ));

    public static final RegistrySupplier<PhoneCaseItem> PHONE_CASE_DEFAULT =
            ITEMS.register("phone_case_default", () -> new PhoneCaseItem("default",
                    new Item.Properties()
            ));

    public static final RegistrySupplier<PhoneCaseItem> PHONE_CASE_BLACK =
            ITEMS.register("phone_case_black", () -> new PhoneCaseItem("black",
                    new Item.Properties()
            ));

    public static final RegistrySupplier<PhoneCaseItem> PHONE_CASE_WHITE =
            ITEMS.register("phone_case_white", () -> new PhoneCaseItem("white",
                    new Item.Properties()
            ));

    public static final RegistrySupplier<PhoneCaseItem> PHONE_CASE_NEON =
            ITEMS.register("phone_case_neon", () -> new PhoneCaseItem("neon",
                    new Item.Properties()
            ));

    public static final RegistrySupplier<PhoneCaseItem> PHONE_CASE_DIAMOND =
            ITEMS.register("phone_case_diamond", () -> new PhoneCaseItem("diamond",
                    new Item.Properties()
            ));
}
