package com.icraft.client.neoforge;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * Implementación NeoForge de ClientSetupExpectPlatform (common/).
 * NeoForge vuelve públicos ItemProperties.register y EntityRenderers.register
 * vía Access Transformer, así que acá se puede llamar directo a las clases
 * vanilla.
 */
public final class ClientSetupExpectPlatformImpl {

    private ClientSetupExpectPlatformImpl() {}

    public static void registerItemModelProperty(Item item, ResourceLocation id, ClampedItemPropertyFunction function) {
        ItemProperties.register(item, id, function);
    }

    public static <T extends Entity> void registerEntityRenderer(EntityType<T> type, EntityRendererProvider<T> provider) {
        EntityRenderers.register(type, provider);
    }
}
