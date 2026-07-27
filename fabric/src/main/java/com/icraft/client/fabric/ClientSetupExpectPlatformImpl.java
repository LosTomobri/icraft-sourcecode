package com.icraft.client.fabric;

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
 * <p>
 * IMPORTANTE: el paquete {@code com.icraft.client.fabric} no es opcional —
 * es la convención que usa Architectury Transformer para resolver
 * {@code @ExpectPlatform}: si la interfaz vive en {@code com.icraft.client},
 * la implementación de Fabric DEBE vivir en {@code com.icraft.client.fabric}
 * con sufijo {@code Impl} en el nombre de clase, o el compilador genera una
 * llamada a una clase que no existe en runtime (NoClassDefFoundError, que es
 * justo lo que estaba pasando: {@code ClientSetup.register()} explotaba acá
 * y tiraba abajo todo el entrypoint "client", así que el juego nunca
 * llegaba a abrir ventana).
 * <p>
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
