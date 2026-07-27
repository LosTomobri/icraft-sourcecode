package com.icraft.event;

import net.minecraft.resources.ResourceLocation;

/**
 * Paso 4 de la migración — contrato común para los eventos de renderizado que
 * hoy están suscriptos directo en {@code ICraftMod} (neoforge) vía
 * {@code @SubscribeEvent}/{@code NeoForge.EVENT_BUS}.
 * <p>
 * Esta interfaz vive en {@code common} porque solo usa tipos vanilla
 * ({@link ResourceLocation}), sin nada de NeoForge. La lógica real (que hoy
 * depende de {@code PhoneScreen}, todavía en {@code neoforge/} — ver paso 6)
 * la sigue implementando cada loader por separado:
 * <ul>
 *   <li>{@code neoforge/.../event/ICraftClientEventsNeoForge.java} — la
 *       lógica movida tal cual desde {@code ICraftMod}, sin cambios de
 *       comportamiento.</li>
 *   <li>{@code fabric/.../event/ICraftClientEventsFabric.java} — pendiente:
 *       necesita que {@code PhoneScreen} exista del lado de Fabric/common
 *       (paso 6) antes de poder implementar algo real.</li>
 * </ul>
 * Cada método corresponde 1:1 a un handler que hoy vive en {@code ICraftMod}:
 * <ul>
 *   <li>{@link #onRenderAfterLevel()} ↔ {@code onRenderLevelStage} (stage
 *       {@code AFTER_LEVEL})</li>
 *   <li>{@link #onRenderGuiLayer(ResourceLocation)} ↔ {@code onRenderGuiLayer}
 *       ({@code RenderGuiLayerEvent.Pre})</li>
 *   <li>{@link #onRenderHand()} ↔ {@code onRenderHand}
 *       ({@code RenderHandEvent})</li>
 *   <li>{@link #onComputeFov()} ↔ {@code onComputeFov}
 *       ({@code ViewportEvent.ComputeFov})</li>
 * </ul>
 */
public interface ICraftClientEvents {

    /**
     * Se llama después de renderizar el nivel (stage AFTER_LEVEL). Dispara la
     * captura de foto programada si hay una pendiente.
     */
    void onRenderAfterLevel();

    /**
     * Se llama antes de renderizar una capa vanilla del HUD.
     *
     * @param layerId id vanilla de la capa (namespace "minecraft"), p.ej.
     *                "hotbar", "crosshair", "vignette".
     * @return {@code true} si hay que cancelar el renderizado de esa capa.
     */
    boolean onRenderGuiLayer(ResourceLocation layerId);

    /**
     * Se llama antes de renderizar la mano/item en primera persona.
     *
     * @return {@code true} si hay que cancelar ese renderizado.
     */
    boolean onRenderHand();

    /**
     * Se llama para calcular el FOV del jugador.
     *
     * @return el FOV a aplicar, o {@code null} si no hay que modificarlo.
     */
    Float onComputeFov();
}
