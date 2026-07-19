package com.icraft.client;

import com.icraft.entity.PhotoFrameEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * PhotoFrameRenderer — renderiza el PhotoFrameEntity como un cuadro
 * de 1×1 bloque colgado en la pared, con la imagen real del archivo PNG.
 *
 * Técnica: igual que ItemFrameRenderer de vanilla pero dibujando la textura
 * del PNG en lugar del ítem.
 */
public class PhotoFrameRenderer extends EntityRenderer<PhotoFrameEntity> {

    // Caché de texturas: filename → ResourceLocation registrado en TextureManager
    private static final Map<String, ResourceLocation> TEX_CACHE    = new HashMap<>();
    private static final Map<String, DynamicTexture>   DYN_TEXTURES = new HashMap<>();

    // Archivos que ya pedimos al servidor (RequestPhotoPacket) y para los que
    // estamos esperando respuesta. Evita mandar el pedido en cada frame
    // mientras el cuadro esté en pantalla (60+ veces por segundo).
    private static final Map<String, Long> REQUESTED_AT = new HashMap<>();
    private static final long REQUEST_COOLDOWN_MS = 8000L; // reintentar cada 8s si sigue sin llegar

    // Tamaño del cuadro en unidades de render (1 bloque = 1.0f)
    private static final float SIZE = 1.0f;

    // Espesor del marco (cuánto sobresale de la pared en Z). Usado tanto para
    // la traslación que pega el cuadro a la pared como para la geometría.
    private static final float FRAME_DEPTH = 0.03f;

    public PhotoFrameRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(PhotoFrameEntity entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {

        pose.pushPose();

        Direction dir = entity.getDirection();
        applyDirectionRotation(pose, dir);

        // Foto (con panel de respaldo para disimular el espacio con la pared)
        String filename = entity.getFilename();
        if (!filename.isEmpty()) {
            ResourceLocation tex = getOrLoadTexture(filename);
            if (tex != null) {
                renderBackPanel(pose, buffers, packedLight);
                renderPhoto(pose, buffers, tex, packedLight);
            }
        }

        pose.popPose();

        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    // ── Rotar según la pared a la que está colgado ───────────────────────────

    private void applyDirectionRotation(PoseStack pose, Direction dir) {
        // IMPORTANTE: a diferencia de los renderers de mobs (que sí rotan
        // por yaw dentro de su propio render()), EntityRenderer.render() y el
        // EntityRenderDispatcher NO aplican ninguna rotación automática para
        // esta clase — el PoseStack que llega acá solo está trasladado a la
        // posición mundial de la entidad, sin ninguna rotación previa.
        // Por eso toda la rotación tiene que salir, de cero, de `dir`
        // (la cara real a la que está pegado el cuadro).
        //
        // Convención de ejes locales antes de rotar: el quad de la foto
        // (ver renderPhoto) está en el plano XY con su normal +Z. Rotamos
        // ese plano para que su normal apunte hacia afuera de cada cara:
        switch (dir) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(0f));    // normal +Z (sur) ✓ sin rotar
            case EAST  -> pose.mulPose(Axis.YP.rotationDegrees(90f));  // +Z rota hacia +X (este)
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180f)); // +Z rota hacia -Z (norte)
            case WEST  -> pose.mulPose(Axis.YP.rotationDegrees(270f)); // +Z rota hacia -X (oeste)
            default -> { /* UP/DOWN no aplican: HangingEntity es horizontal */ }
        }

        // Trasladar para pegar la cara TRASERA del panel exactamente contra
        // la pared. El pivote de la entidad está en el centro del bloque de
        // aire donde cuelga (ver PrintedPhotoItem: frame.moveTo(framePos+0.5)),
        // que está medio bloque (0.5) AFUERA de la pared en dirección `dir`.
        // Retrocedemos ese medio bloque completo (-0.5 en Z local, que tras
        // la rotación coincide con `dir` en el mundo) para que z_local=0
        // (la cara trasera del panel, ver renderBackPanel) quede exactamente
        // sobre la cara de la pared, sin ningún hueco.
        pose.translate(0, 0, -0.5f);
    }

    // ── Panel de respaldo ──────────────────────────────────────────────────────
    // Caja delgada pegada a la pared, detrás de la foto. Sin esto, mirando el
    // cuadro casi de canto se ve un hueco/espacio vacío entre la foto y el
    // bloque (la foto es un plano sin espesor). Este panel rellena ese
    // espacio con geometría sólida y textura propia de marco de madera,
    // igual que el "back" del ItemFrame vanilla.

    private static final ResourceLocation FRAME_BORDER_TEX =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/entity/photo_frame_border.png");

    private void renderBackPanel(PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float h = SIZE / 2f;
        float frontZ = FRAME_DEPTH; // hasta donde llega el panel (justo detrás de la foto)

        // entitySolid: textura opaca con lightmap, sin necesidad de cutout
        // (la textura del marco no tiene transparencia).
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(FRAME_BORDER_TEX));
        Matrix4f m = pose.last().pose();

        int noOverlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        int lU = packedLight & 0xFFFF;
        int lV = (packedLight >> 16) & 0xFFFF;

        // Cara trasera, pegada contra la pared (z=0, normal -Z, mirando hacia la pared)
        addTexturedQuad(m, vc, -h, -h, h, h, 0f, lU, lV, noOverlay, false);
        // Cara frontal del panel (z=frontZ, normal +Z; queda tapada por la foto)
        addTexturedQuad(m, vc, -h, -h, h, h, frontZ, lU, lV, noOverlay, true);

        // Cantos laterales: la textura se repite una vez por canto (UV 0→1)
        // para que se vea como un marco de madera continuo.
        vc.addVertex(m, -h, -h, 0f).setColor(255,255,255,255).setUv(0,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,-1,0);
        vc.addVertex(m,  h, -h, 0f).setColor(255,255,255,255).setUv(1,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,-1,0);
        vc.addVertex(m,  h, -h, frontZ).setColor(255,255,255,255).setUv(1,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,-1,0);
        vc.addVertex(m, -h, -h, frontZ).setColor(255,255,255,255).setUv(0,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,-1,0);

        vc.addVertex(m,  h,  h, 0f).setColor(255,255,255,255).setUv(0,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,1,0);
        vc.addVertex(m, -h,  h, 0f).setColor(255,255,255,255).setUv(1,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,1,0);
        vc.addVertex(m, -h,  h, frontZ).setColor(255,255,255,255).setUv(1,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,1,0);
        vc.addVertex(m,  h,  h, frontZ).setColor(255,255,255,255).setUv(0,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(0,1,0);

        vc.addVertex(m, -h,  h, 0f).setColor(255,255,255,255).setUv(0,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(-1,0,0);
        vc.addVertex(m, -h, -h, 0f).setColor(255,255,255,255).setUv(1,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(-1,0,0);
        vc.addVertex(m, -h, -h, frontZ).setColor(255,255,255,255).setUv(1,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(-1,0,0);
        vc.addVertex(m, -h,  h, frontZ).setColor(255,255,255,255).setUv(0,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(-1,0,0);

        vc.addVertex(m,  h, -h, 0f).setColor(255,255,255,255).setUv(0,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(1,0,0);
        vc.addVertex(m,  h,  h, 0f).setColor(255,255,255,255).setUv(1,0).setOverlay(noOverlay).setUv2(lU,lV).setNormal(1,0,0);
        vc.addVertex(m,  h,  h, frontZ).setColor(255,255,255,255).setUv(1,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(1,0,0);
        vc.addVertex(m,  h, -h, frontZ).setColor(255,255,255,255).setUv(0,1).setOverlay(noOverlay).setUv2(lU,lV).setNormal(1,0,0);
    }

    /** Quad texturizado en un Z fijo, en el plano XY. front=true → normal +Z, front=false → normal -Z (orden de vértices invertido para que el winding quede correcto en cada caso). */
    private void addTexturedQuad(Matrix4f m, VertexConsumer vc,
                              float x0, float y0, float x1, float y1,
                              float z, int lU, int lV, int noOverlay, boolean front) {
        float nz = front ? 1f : -1f;

        if (front) {
            vc.addVertex(m, x0, y0, z).setColor(255,255,255,255).setUv(0, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
            vc.addVertex(m, x1, y0, z).setColor(255,255,255,255).setUv(1, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
            vc.addVertex(m, x1, y1, z).setColor(255,255,255,255).setUv(1, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
            vc.addVertex(m, x0, y1, z).setColor(255,255,255,255).setUv(0, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
        } else {
            vc.addVertex(m, x0, y1, z).setColor(255,255,255,255).setUv(0, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
            vc.addVertex(m, x1, y1, z).setColor(255,255,255,255).setUv(1, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
            vc.addVertex(m, x1, y0, z).setColor(255,255,255,255).setUv(1, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
            vc.addVertex(m, x0, y0, z).setColor(255,255,255,255).setUv(0, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, nz);
        }
    }

    // ── Foto ─────────────────────────────────────────────────────────────────
    // Sin marco: la imagen ocupa toda la cara del bloque (SIZE x SIZE).

    private void renderPhoto(PoseStack pose, MultiBufferSource buffers,
                             ResourceLocation tex, int packedLight) {
        float h    = SIZE / 2f;
        float zOff = FRAME_DEPTH + 0.001f;  // ligeramente por delante de la cara de la pared

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(tex));
        Matrix4f m = pose.last().pose();

        // Quad con UV 0→1
        // entityCutout usa NEW_ENTITY: requiere UV0 (textura), UV1 (overlay) y UV2 (lightmap)
        // Caso base SOUTH (sin rotación extra): el jugador que ve la foto
        // está parado al sur del bloque mirando hacia el norte (-Z), con
        // "arriba" = +Y. Por la regla de la mano derecha, la DERECHA de ese
        // jugador es -X. Para que U=1 (lado derecho de la imagen original)
        // caiga a la derecha del jugador, U=1 va en x=-h y U=0 en x=+h.
        int noOverlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        int lU = packedLight & 0xFFFF;
        int lV = (packedLight >> 16) & 0xFFFF;
        vc.addVertex(m, -h, -h, zOff).setColor(255,255,255,255)
          .setUv(1, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
        vc.addVertex(m,  h, -h, zOff).setColor(255,255,255,255)
          .setUv(0, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
        vc.addVertex(m,  h,  h, zOff).setColor(255,255,255,255)
          .setUv(0, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
        vc.addVertex(m, -h,  h, zOff).setColor(255,255,255,255)
          .setUv(1, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
    }

    // ── Carga de texturas ─────────────────────────────────────────────────────

    private static ResourceLocation getOrLoadTexture(String filename) {
        if (TEX_CACHE.containsKey(filename)) return TEX_CACHE.get(filename);

        Path file = PhoneScreen.getPhotosDirStatic().resolve(filename);
        if (!Files.exists(file)) {
            // No tenemos este archivo localmente — esto pasa para CUALQUIER
            // jugador que no haya sido quien imprimió la foto (el archivo
            // original vive solo en la carpeta iCraft/photos/ de esa persona).
            // En vez de cachear "null" para siempre (lo que hacía que el
            // cuadro quedara en blanco eternamente), le pedimos la imagen al
            // servidor. Si llega, AdminPhotoPacket la guarda en disco e
            // invalida esta caché, y el próximo frame ya la encuentra.
            requestFromServerIfNeeded(filename);
            return null;
        }

        try (InputStream is = Files.newInputStream(file)) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) { TEX_CACHE.put(filename, null); return null; }

            int w = img.getWidth(), h = img.getHeight();
            com.mojang.blaze3d.platform.NativeImage ni =
                    new com.mojang.blaze3d.platform.NativeImage(
                            com.mojang.blaze3d.platform.NativeImage.Format.RGBA, w, h, false);

            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int argb = img.getRGB(px, py);
                    int a2 = (argb >> 24) & 0xFF;
                    int r2 = (argb >> 16) & 0xFF;
                    int g2 = (argb >>  8) & 0xFF;
                    int b2 =  argb        & 0xFF;
                    // NativeImage: ABGR little-endian
                    ni.setPixelRGBA(px, py, r2 | (g2 << 8) | (b2 << 16) | (a2 << 24));
                }
            }

            DynamicTexture dt = new DynamicTexture(ni);
            dt.setFilter(false, false); // GL_NEAREST, sin mipmaps: nítido
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    "icraft", "photo_frame/" + filename.replace(".png", ""));
            Minecraft.getInstance().getTextureManager().register(loc, dt);

            TEX_CACHE.put(filename, loc);
            DYN_TEXTURES.put(filename, dt);
            REQUESTED_AT.remove(filename);
            return loc;

        } catch (IOException e) {
            TEX_CACHE.put(filename, null);
            return null;
        }
    }

    private static void requestFromServerIfNeeded(String filename) {
        long now = System.currentTimeMillis();
        Long last = REQUESTED_AT.get(filename);
        if (last != null && now - last < REQUEST_COOLDOWN_MS) return;
        REQUESTED_AT.put(filename, now);

        if (Minecraft.getInstance().getConnection() != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.icraft.network.RequestPhotoPacket(filename));
        }
    }

    /** Invalida la caché de una foto (si se sobreescribió el PNG en disco, o si recién llegó por red). */
    public static void invalidateCache(String filename) {
        ResourceLocation loc = TEX_CACHE.remove(filename);
        if (loc != null) Minecraft.getInstance().getTextureManager().release(loc);
        DynamicTexture dt = DYN_TEXTURES.remove(filename);
        if (dt != null) dt.close();
        REQUESTED_AT.remove(filename);
    }

    @Override
    public ResourceLocation getTextureLocation(PhotoFrameEntity entity) {
        // No se usa: cada entidad dibuja su propia textura dinámica
        return ResourceLocation.fromNamespaceAndPath("icraft", "textures/misc/blank.png");
    }
}
