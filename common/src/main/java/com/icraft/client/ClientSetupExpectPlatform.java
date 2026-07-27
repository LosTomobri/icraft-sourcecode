package com.icraft.client;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * ItemProperties.register y EntityRenderers.register son `private` en el
 * vanilla "limpio" contra el que compila common/. NeoForge los vuelve
 * públicos vía Access Transformer y Fabric expone sus propios wrappers
 * (FabricModelPredicateProviderRegistry / EntityRendererRegistry), así que
 * cada loader implementa esto por su cuenta en su propio módulo.
 */
public final class ClientSetupExpectPlatform {

    private ClientSetupExpectPlatform() {}

    @ExpectPlatform
    public static void registerItemModelProperty(Item item, ResourceLocation id, ClampedItemPropertyFunction function) {
        throw new AssertionError("No implementado para esta plataforma");
    }

    @ExpectPlatform
    public static <T extends Entity> void registerEntityRenderer(EntityType<T> type, EntityRendererProvider<T> provider) {
        throw new AssertionError("No implementado para esta plataforma");
    }
}
