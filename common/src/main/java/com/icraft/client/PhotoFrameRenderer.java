package com.icraft.client;

import com.icraft.entity.PhotoFrameEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class PhotoFrameRenderer extends EntityRenderer<PhotoFrameEntity> {

    private static final Map<String, ResourceLocation> TEX_CACHE    = new HashMap<>();
    private static final Map<String, DynamicTexture>   DYN_TEXTURES = new HashMap<>();

    private static final Map<String, Long> REQUESTED_AT = new HashMap<>();
    private static final long REQUEST_COOLDOWN_MS = 8000L;

    private static final float SIZE = 1.0f;

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

    private void applyDirectionRotation(PoseStack pose, Direction dir) {

        switch (dir) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(0f));
            case EAST  -> pose.mulPose(Axis.YP.rotationDegrees(90f));
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180f));
            case WEST  -> pose.mulPose(Axis.YP.rotationDegrees(270f));
            default -> {  }
        }

        pose.translate(0, 0, -0.5f);
    }

    private static final ResourceLocation FRAME_BORDER_TEX =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/entity/photo_frame_border.png");

    private void renderBackPanel(PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float h = SIZE / 2f;
        float frontZ = FRAME_DEPTH;

        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(FRAME_BORDER_TEX));
        Matrix4f m = pose.last().pose();

        int noOverlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        int lU = packedLight & 0xFFFF;
        int lV = (packedLight >> 16) & 0xFFFF;

        addTexturedQuad(m, vc, -h, -h, h, h, 0f, lU, lV, noOverlay, false);

        addTexturedQuad(m, vc, -h, -h, h, h, frontZ, lU, lV, noOverlay, true);

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

    private void renderPhoto(PoseStack pose, MultiBufferSource buffers,
                             ResourceLocation tex, int packedLight) {
        float h    = SIZE / 2f;
        float zOff = FRAME_DEPTH + 0.001f;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(tex));
        Matrix4f m = pose.last().pose();

        int noOverlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        int lU = packedLight & 0xFFFF;
        int lV = (packedLight >> 16) & 0xFFFF;
        vc.addVertex(m, -h, -h, zOff).setColor(255,255,255,255)
          .setUv(0, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
        vc.addVertex(m,  h, -h, zOff).setColor(255,255,255,255)
          .setUv(1, 1).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
        vc.addVertex(m,  h,  h, zOff).setColor(255,255,255,255)
          .setUv(1, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
        vc.addVertex(m, -h,  h, zOff).setColor(255,255,255,255)
          .setUv(0, 0).setOverlay(noOverlay).setUv2(lU, lV).setNormal(0, 0, 1);
    }

    private static ResourceLocation getOrLoadTexture(String filename) {
        if (TEX_CACHE.containsKey(filename)) return TEX_CACHE.get(filename);

        Path file = PhoneScreen.getPhotosDirStatic().resolve(filename);
        if (!Files.exists(file)) {

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

                    ni.setPixelRGBA(px, py, r2 | (g2 << 8) | (b2 << 16) | (a2 << 24));
                }
            }

            DynamicTexture dt = new DynamicTexture(ni);
            dt.setFilter(false, false);
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
            dev.architectury.networking.NetworkManager.sendToServer(
                    new com.icraft.network.RequestPhotoPacket(filename));
        }
    }

    public static void invalidateCache(String filename) {
        ResourceLocation loc = TEX_CACHE.remove(filename);
        if (loc != null) Minecraft.getInstance().getTextureManager().release(loc);
        DynamicTexture dt = DYN_TEXTURES.remove(filename);
        if (dt != null) dt.close();
        REQUESTED_AT.remove(filename);
    }

    @Override
    public ResourceLocation getTextureLocation(PhotoFrameEntity entity) {

        return ResourceLocation.fromNamespaceAndPath("icraft", "textures/misc/blank.png");
    }
}
