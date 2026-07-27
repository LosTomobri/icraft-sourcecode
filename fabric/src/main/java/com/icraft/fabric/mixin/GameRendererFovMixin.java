package com.icraft.fabric.mixin;

import com.icraft.event.ICraftClientEventsFabric;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Equivalente Fabric de {@code ViewportEvent.ComputeFov} de NeoForge.
 * <p>
 * Target: {@code GameRenderer#getFov(Camera, float, boolean)} — privado,
 * mapeado igual en Mojang official mappings para 1.21.1 (confirmado contra
 * los javadocs de Yarn/Mojmap 1.21.x, que usan el mismo nombre; el proyecto
 * ya compila con {@code loom.officialMojangMappings()} según el comentario
 * original en ICraftClientEventsFabric). Devuelve {@code double} en 1.21.1
 * (pasa a {@code float} recién en 1.21.4+) — el cast de vuelta a double es
 * intencional.
 * <p>
 * NO COMPILADO/TESTEADO EN ESTE ENTORNO (sin toolchain de Loom acá). Antes
 * de mergear: compilar contra tu dev environment y confirmar que el nombre
 * "getFov" no cambió de firma. Si Loom tira "target method not found",
 * pegame el error y lo ajusto — no es un mixin complejo, así que el riesgo
 * de que rompa el arranque del cliente es bajo, pero quiero que lo veas
 * correr antes de darlo por bueno.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void icraft$overrideFov(Camera camera, float tickDelta, boolean changingFov,
                                     CallbackInfoReturnable<Double> cir) {
        ICraftClientEventsFabric events = ICraftClientEventsFabric.getInstance();
        if (events == null) return;

        Float fov = events.onComputeFov();
        if (fov != null) {
            cir.setReturnValue(fov.doubleValue());
        }
    }
}
