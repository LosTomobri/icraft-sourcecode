package com.icraft.init;

import com.icraft.ICraftConstants;
import com.icraft.entity.PhotoFrameEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Paso 2 de la migración: ver nota en ModItems.java — ya vive en common/
 * junto con ModItems y PhotoFrameEntity.
 */
public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ICraftConstants.MODID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<PhotoFrameEntity>> PHOTO_FRAME =
            ENTITY_TYPES.register("photo_frame", () ->
                    EntityType.Builder.<PhotoFrameEntity>of(PhotoFrameEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .updateInterval(Integer.MAX_VALUE)
                            .build("photo_frame")
            );
}
