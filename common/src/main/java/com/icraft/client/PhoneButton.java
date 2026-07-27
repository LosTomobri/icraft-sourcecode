package com.icraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PhoneButton extends Button {

    private static final int FRAME_W = 32;
    private static final int FRAME_H = 16;
    private static final int CORNER = 4;
    private static final int SHEET_H = FRAME_H * 3;

    private static ResourceLocation texture =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/gui/widget/phone_button.png");

    private static int themeColor = 0xFFFFFFFF;

    public static void setThemeColor(int argb) {
        themeColor = argb;
    }

    public static void setTexture(ResourceLocation newTexture) {
        texture = newTexture;
    }

    public static ResourceLocation getTexture() {
        return texture;
    }

    protected PhoneButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        String clickSound = PhoneScreen.getPhoneData().clickSound;
        if (clickSound != null && !clickSound.isEmpty()) {
            ClickSounds.play(clickSound);
        }
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void playDownSound(SoundManager handler) {

    }

    public static Builder phoneBuilder(Component message, Button.OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int frame = !this.active ? 2 : (this.isHoveredOrFocused() ? 1 : 0);
        int v = frame * FRAME_H;

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        if (frame == 2) {
            g.setColor(1f, 1f, 1f, 1f);
        } else {
            float r = ((themeColor >> 16) & 0xFF) / 255f;
            float gg = ((themeColor >> 8) & 0xFF) / 255f;
            float b = (themeColor & 0xFF) / 255f;
            g.setColor(r, gg, b, 1f);
        }
        blitNineSlice(g, x, y, w, h, v);
        g.setColor(1f, 1f, 1f, 1f);

        int color = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
        drawFittedCenteredString(g, this.getMessage(), x + w / 2, y + h / 2, w - 4, color);
    }

    private static void drawFittedCenteredString(GuiGraphics g, Component message, int centerX, int centerY, int maxWidth, int color) {
        var font = Minecraft.getInstance().font;
        String text = message.getString();
        int textWidth = font.width(text);

        float scale = 1f;
        if (textWidth > maxWidth && textWidth > 0) {
            scale = Math.max(0.5f, maxWidth / (float) textWidth);
        }

        if (scale >= 0.999f) {
            g.drawCenteredString(font, message, centerX, centerY - 4, color);
        } else {
            g.pose().pushPose();
            g.pose().translate(centerX, centerY, 0f);
            g.pose().scale(scale, scale, 1f);
            g.drawCenteredString(font, message, 0, -4, color);
            g.pose().popPose();
        }
    }

    private static void blitNineSlice(GuiGraphics g, int x, int y, int w, int h, int v) {
        int c = Math.min(CORNER, Math.min(w, h) / 2);
        int midW = Math.max(0, w - c * 2);
        int midH = Math.max(0, h - c * 2);
        int midSrcW = FRAME_W - CORNER * 2;
        int midSrcH = FRAME_H - CORNER * 2;

        g.blit(texture, x, y, 0, v, c, c, FRAME_W, SHEET_H);
        g.blit(texture, x + w - c, y, FRAME_W - CORNER, v, c, c, FRAME_W, SHEET_H);
        g.blit(texture, x, y + h - c, 0, v + FRAME_H - CORNER, c, c, FRAME_W, SHEET_H);
        g.blit(texture, x + w - c, y + h - c, FRAME_W - CORNER, v + FRAME_H - CORNER, c, c, FRAME_W, SHEET_H);

        if (midW > 0) {
            g.blit(texture, x + c, y, midW, c, CORNER, v, midSrcW, c, FRAME_W, SHEET_H);
            g.blit(texture, x + c, y + h - c, midW, c, CORNER, v + FRAME_H - CORNER, midSrcW, c, FRAME_W, SHEET_H);
        }
        if (midH > 0) {
            g.blit(texture, x, y + c, c, midH, 0, v + CORNER, c, midSrcH, FRAME_W, SHEET_H);
            g.blit(texture, x + w - c, y + c, c, midH, FRAME_W - CORNER, v + CORNER, c, midSrcH, FRAME_W, SHEET_H);
        }

        if (midW > 0 && midH > 0) {
            g.blit(texture, x + c, y + c, midW, midH, CORNER, v + CORNER, midSrcW, midSrcH, FRAME_W, SHEET_H);
        }
    }

    public static class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        private int x, y, width = 150, height = 20;

        Builder(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public PhoneButton build() {
            return new PhoneButton(x, y, width, height, message, onPress);
        }
    }
}
