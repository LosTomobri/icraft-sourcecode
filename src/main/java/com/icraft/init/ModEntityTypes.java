package com.icraft.init;

import com.icraft.ICraftMod;
import com.icraft.entity.PhotoFrameEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ICraftMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<PhotoFrameEntity>> PHOTO_FRAME =
            ENTITY_TYPES.register("photo_frame", () ->
                    EntityType.Builder.<PhotoFrameEntity>of(PhotoFrameEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)  // hitbox (HangingEntity la ajusta sola en setDirection)
                            .clientTrackingRange(10)
                            .updateInterval(Integer.MAX_VALUE)  // no necesita updates de movimiento
                            .build("photo_frame")
            );
}
