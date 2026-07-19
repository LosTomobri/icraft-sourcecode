package com.icraft.client;

import com.icraft.entity.PhotoFrameEntity;
import com.icraft.init.ModEntityTypes;
import com.icraft.init.ModItems;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "icraft", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // ── Propiedad del modelo de smartphone (abierto/cerrado) ──────────
            ItemProperties.register(
                    ModItems.SMARTPHONE.get(),
                    ResourceLocation.fromNamespaceAndPath("icraft", "open"),
                    (stack, level, entity, seed) -> {
                        if (!(entity instanceof Player player)) return 0f;
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        boolean isLocalPlayer = player == mc.player;
                        boolean open = isLocalPlayer
                                ? mc.screen instanceof PhoneScreen
                                : PhoneOpenTracker.isOpen(player.getUUID());
                        return open ? 1f : 0f;
                    }
            );

            // ── Renderer para el cuadro de foto ───────────────────────────────
            EntityRenderers.register(
                    ModEntityTypes.PHOTO_FRAME.get(),
                    PhotoFrameRenderer::new
            );
        });
    }
}
