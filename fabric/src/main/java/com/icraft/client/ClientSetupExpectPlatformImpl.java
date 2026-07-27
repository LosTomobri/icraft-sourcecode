package com.icraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * Implementación Fabric de ClientSetupExpectPlatform (common/).
 * Fabric no expone ItemProperties.register/EntityRenderers.register de
 * forma pública para compilar: usa sus propios wrappers de fabric-api.
 */
public final class ClientSetupExpectPlatformImpl {

    private ClientSetupExpectPlatformImpl() {}

    public static void registerItemModelProperty(Item item, ResourceLocation id, ClampedItemPropertyFunction function) {
        FabricModelPredicateProviderRegistry.register(item, id, function);
    }

    public static <T extends Entity> void registerEntityRenderer(EntityType<T> type, EntityRendererProvider<T> provider) {
        EntityRendererRegistry.register(type, provider);
    }
}
