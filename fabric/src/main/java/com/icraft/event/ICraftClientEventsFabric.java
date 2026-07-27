package com.icraft.event;

import com.icraft.client.PhoneScreen;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Implementación Fabric de {@link ICraftClientEvents}.
 * <p>
 * Paso 4 (retomado): la lógica de negocio es idéntica a
 * {@code ICraftClientEventsNeoForge} — {@code PhoneScreen} ya vive en
 * {@code common/} y es multi-loader, así que no hay razón para que
 * difiera. Lo único que cambia entre loaders es el *cableado*:
 * <ul>
 *   <li>{@link #onRenderAfterLevel()} se cablea acá mismo contra
 *       {@code WorldRenderEvents.LAST} (fabric-rendering-v1), que es el
 *       evento público equivalente a
 *       {@code RenderLevelStageEvent.Stage.AFTER_LEVEL} de NeoForge — no
 *       requiere Mixin.</li>
 *   <li>{@link #onRenderGuiLayer}, {@link #onRenderHand} y
 *       {@link #onComputeFov} SÍ requieren Mixin en 1.21.1 (no hay
 *       evento público en Fabric API para esto todavía). Quedan
 *       implementados acá (mismo comportamiento que NeoForge) pero solo
 *       se van a ejecutar cuando existan los Mixins que los llamen — ver
 *       {@code com.icraft.fabric.mixin.GameRendererFovMixin} (FOV, ya
 *       incluido) y el TODO para mano/capas del HUD.</li>
 * </ul>
 */
public final class ICraftClientEventsFabric implements ICraftClientEvents {

    private static ICraftClientEventsFabric INSTANCE;

    /** Llamado una sola vez desde {@code ICraftModFabricClient#onInitializeClient()}. */
    public static void register() {
        INSTANCE = new ICraftClientEventsFabric();
        WorldRenderEvents.LAST.register(context -> INSTANCE.onRenderAfterLevel());
    }

    /**
     * Usado por los Mixins (que no pueden depender de una instancia inyectada)
     * para llegar a la lógica común. Null hasta que {@link #register()} corrió
     * (es decir, siempre no-null en el momento en que estos Mixins disparan,
     * porque disparan durante el render del cliente, que es posterior al
     * entrypoint "client").
     */
    public static ICraftClientEventsFabric getInstance() {
        return INSTANCE;
    }

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
