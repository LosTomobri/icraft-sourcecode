package com.icraft.client;

import com.icraft.init.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class PrinterScreen extends Screen {

    private static final int BG_W  = 260;
    private static final int BG_H  = 210;
    private static final int COLS  = 4;
    private static final int THUMB = 48;
    private static final int GAP   = 6;
    private static final int PAD   = 12;

    private final List<String>                  photoFiles  = new ArrayList<>();
    private final Map<String, ResourceLocation> texCache    = new HashMap<>();
    private final Map<String, DynamicTexture>   dynTextures = new HashMap<>();

    private int selectedIndex = -1;
    private int scrollOffset  = 0;
    private int bgX, bgY;

    public PrinterScreen() {
        super(Component.translatable("icraft.printer.title"));
    }

    @Override
    protected void init() {
        super.init();
        bgX = (width  - BG_W) / 2;
        bgY = (height - BG_H) / 2;
        loadPhotoList();
        addPrintButton();
    }

    private void loadPhotoList() {
        photoFiles.clear();
        Path dir = PhoneScreen.getPhotosDirStatic();
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".png"))
                  .map(p -> p.getFileName().toString())
                  .sorted()
                  .forEach(photoFiles::add);
        } catch (IOException ignored) {}
    }

    private void addPrintButton() {
        int btnW = 90, btnH = 18;
        int btnX = bgX + (BG_W - btnW) / 2;
        int btnY = bgY + BG_H - btnH - 8;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.translatable("icraft.printer.print_btn"), btn -> printSelected()
        ).pos(btnX, btnY).size(btnW, btnH).build());
    }

    private void printSelected() {
        if (selectedIndex < 0 || selectedIndex >= photoFiles.size()) return;
        if (!hasPaperInInventory()) {
            Minecraft mc2 = Minecraft.getInstance();
            if (mc2.player != null)
                mc2.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("icraft.photo.need_paper_undeveloped_msg"), true);
            return;
        }
        String filename = photoFiles.get(selectedIndex);
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        try {
            Path file = PhoneScreen.getPhotosDirStatic().resolve(filename);
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > 512 * 1024) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("icraft.photo.too_big_512_msg"), true);
                return;
            }
            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
            dev.architectury.networking.NetworkManager.sendToServer(
                    new com.icraft.network.PrintPhotoPacket(filename, base64));
        } catch (IOException e) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("icraft.photo.cant_read_msg"), true);
            return;
        }

        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        g.fill(0, 0, width, height, 0xC8000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(bgX, bgY, bgX + BG_W, bgY + BG_H, 0xFF1E1E2E);
        g.fill(bgX, bgY, bgX + BG_W, bgY + 1, 0xFF7070FF);
        g.fill(bgX, bgY + BG_H - 1, bgX + BG_W, bgY + BG_H, 0xFF7070FF);

        g.drawCenteredString(font, I18n.get("icraft.printer.title"), bgX + BG_W / 2, bgY + 6, 0xFFFFFF);
        g.drawString(font, I18n.get("icraft.printer.select_photo_label"), bgX + PAD, bgY + 18, 0xAAAAAA, false);

        renderGrid(g, mouseX, mouseY);

        int paperAreaLeft = bgX + BG_W - PAD - 20 - 2;
        int textCenterX   = (bgX + PAD + paperAreaLeft) / 2;

        if (selectedIndex >= 0 && selectedIndex < photoFiles.size()) {
            g.drawCenteredString(font, "> " + photoFiles.get(selectedIndex).replace(".png", ""),
                    textCenterX, bgY + BG_H - 30, 0x88FF88);
        } else {
            g.drawCenteredString(font, I18n.get("icraft.printer.none_selected"),
                    textCenterX, bgY + BG_H - 30, 0x555566);
        }

        int slotX = bgX + BG_W - PAD - 20;
        int slotY = bgY + BG_H - 30;
        boolean hasPaper = hasPaperInInventory();
        int slotBorder = hasPaper ? 0xFF88FF88 : 0xFFFF4444;
        g.fill(slotX - 2, slotY - 2, slotX + 18, slotY + 18, slotBorder);
        g.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF222233);

        ItemStack display = new ItemStack(ModItems.PRINTED_PHOTO.get());
        g.renderItem(display, slotX, slotY);

        String paperLabel = hasPaper ? "§a✓" : "§c✗";
        g.drawString(font, paperLabel, slotX + 18, slotY + 4, 0xFFFFFF, false);
        g.drawString(font, I18n.get("icraft.printer.paper_label"), slotX - 4, slotY + 18, 0xAAAAAA, false);

    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int startY      = bgY + 30;
        int gridH       = BG_H - 30 - 42;
        int rows        = (int) Math.ceil(photoFiles.size() / (double) COLS);
        int visibleRows = gridH / (THUMB + GAP);

        if (photoFiles.isEmpty()) {
            g.drawCenteredString(font, I18n.get("icraft.printer.no_photos"),
                    bgX + BG_W / 2, startY + gridH / 2 - 4, 0x666666);
            return;
        }

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows - visibleRows)));

        for (int i = scrollOffset * COLS; i < photoFiles.size(); i++) {
            int col = (i - scrollOffset * COLS) % COLS;
            int row = (i - scrollOffset * COLS) / COLS;
            int x   = bgX + PAD + col * (THUMB + GAP);
            int y   = startY + row * (THUMB + GAP);
            if (y + THUMB > bgY + BG_H - 42) break;

            int borderColor = (i == selectedIndex) ? 0xFF7070FF : 0xFF444455;
            g.fill(x - 2, y - 2, x + THUMB + 2, y + THUMB + 2, borderColor);
            g.fill(x, y, x + THUMB, y + THUMB, 0xFF333344);

            ResourceLocation tex = getOrLoadTexture(photoFiles.get(i));
            if (tex != null) {

                g.blit(tex, x, y, 0, 0, THUMB, THUMB, THUMB, THUMB);
            } else {
                g.drawCenteredString(font, "?", x + THUMB / 2, y + THUMB / 2 - 4, 0x888888);
            }

            if (mouseX >= x && mouseX < x + THUMB && mouseY >= y && mouseY < y + THUMB) {
                g.fill(x, y, x + THUMB, y + THUMB, 0x33FFFFFF);
            }
        }
    }

    private ResourceLocation getOrLoadTexture(String filename) {
        if (texCache.containsKey(filename)) return texCache.get(filename);

        Path file = PhoneScreen.getPhotosDirStatic().resolve(filename);
        if (!Files.exists(file)) { texCache.put(filename, null); return null; }

        try (InputStream is = Files.newInputStream(file)) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) { texCache.put(filename, null); return null; }

            BufferedImage scaled = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D gr2d = scaled.createGraphics();
            gr2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                  java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gr2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                  java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            gr2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                                  java.awt.RenderingHints.VALUE_RENDER_SPEED);
            gr2d.drawImage(img, 0, 0, THUMB, THUMB, null);
            gr2d.dispose();

            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, THUMB, THUMB, false);
            for (int py = 0; py < THUMB; py++) {
                for (int px = 0; px < THUMB; px++) {
                    int argb = scaled.getRGB(px, py);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int gr = (argb >>  8) & 0xFF;
                    int b =  argb         & 0xFF;

                    ni.setPixelRGBA(px, py, r | (gr << 8) | (b << 16) | (a << 24));
                }
            }

            DynamicTexture dt = new DynamicTexture(ni);

            dt.setFilter(false, false);

            String safeName = filename.replace(".png", "").toLowerCase()
                                      .replaceAll("[^a-z0-9_/.-]", "_");
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("icraft",
                    "printer_thumb/" + safeName);
            Minecraft.getInstance().getTextureManager().register(loc, dt);

            texCache.put(filename, loc);
            dynTextures.put(filename, dt);
            return loc;

        } catch (IOException e) {
            texCache.put(filename, null);
            return null;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int startY = bgY + 30;
        for (int i = scrollOffset * COLS; i < photoFiles.size(); i++) {
            int col = (i - scrollOffset * COLS) % COLS;
            int row = (i - scrollOffset * COLS) / COLS;
            int x   = bgX + PAD + col * (THUMB + GAP);
            int y   = startY + row * (THUMB + GAP);
            if (y + THUMB > bgY + BG_H - 42) break;
            if (mx >= x && mx < x + THUMB && my >= y && my < y + THUMB) {
                selectedIndex = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int rows        = (int) Math.ceil(photoFiles.size() / (double) COLS);
        int gridH       = BG_H - 30 - 42;
        int visibleRows = gridH / (THUMB + GAP);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(sy),
                Math.max(0, rows - visibleRows)));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    private boolean hasPaperInInventory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == ModItems.PRINTED_PHOTO.get()
                    && com.icraft.item.PrintedPhotoItem.getFilename(s).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        Minecraft mc = Minecraft.getInstance();
        texCache.forEach((k, v) -> { if (v != null) mc.getTextureManager().release(v); });
        dynTextures.values().forEach(DynamicTexture::close);
        dynTextures.clear();
        texCache.clear();
    }
}
