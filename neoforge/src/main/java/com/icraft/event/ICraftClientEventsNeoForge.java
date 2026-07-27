package com.icraft.event;

import com.icraft.client.PhoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Implementación NeoForge de {@link ICraftClientEvents}.
 * <p>
 * Paso 4 de la migración: esta es exactamente la misma lógica que antes
 * vivía inline en los métodos {@code @SubscribeEvent} de {@code ICraftMod}
 * — solo se movió detrás de la interfaz común. No cambia ningún
 * comportamiento.
 * <p>
 * Sigue viviendo en {@code neoforge/} (y no en {@code common/}) porque
 * depende de {@code PhoneScreen}, que todavía no migró (paso 6).
 */
public final class ICraftClientEventsNeoForge implements ICraftClientEvents {

    @Override
    public void onRenderAfterLevel() {
        if (!PhoneScreen.hasPendingPhoto()) return;

        PhoneScreen.takeScheduledPhoto();
    }

    @Override
    public boolean onRenderGuiLayer(ResourceLocation layerId) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof PhoneScreen ps)) return false;

        if (layerId == null || !"minecraft".equals(layerId.getNamespace())) return false;

        String path = layerId.getPath();

        if ("crosshair".equals(path) && ps.isCameraActive()) {
            return true;
        }

        return switch (path) {
            case "hotbar",
                 "health",
                 "armor",
                 "food",
                 "air",
                 "experience_bar",
                 "jump_bar",
                 "effects_indicator",
                 "sleep_fade",
                 "vignette",
                 "spyglass",
                 "helmet_overlay",
                 "frostbite",
                 "record_overlay",
                 "subtitles",
                 "scoreboard_sidebar",
                 "boss_overlay" -> true;
            default -> false;
        };
    }

    @Override
    public boolean onRenderHand() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof PhoneScreen;
    }

    @Override
    public Float onComputeFov() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof PhoneScreen ps && ps.isCameraActive()) {
            return ps.getCameraFov();
        }

        if (PhoneScreen.isCaptureFovActive()) {
            return PhoneScreen.getCaptureFov();
        }

        return null;
    }
}
