package com.icraft.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Paso 2/4 de la migración: antes se auto-registraba contra el event bus
 * nativo de NeoForge (@EventBusSubscriber + RenderGuiEvent.Post). Ahora
 * expone un register() cross-loader (Architectury ClientGuiEvent.RENDER_HUD)
 * que cada loader llama una sola vez durante su init de cliente.
 */
public class PhoneToast {

    private static boolean registered = false;

    public enum ToastType {
        MESSAGE(0x00AA66FF, "\uD83D\uDCF1 "),
        TIMER(0x00FF9800,   "\u23F0 ");

        final int    accentColor;
        final String icon;

        ToastType(int accentColor, String icon) {
            this.accentColor = accentColor;
            this.icon        = icon;
        }
    }

    private static class Toast {
        final String sender;
        final String preview;
        final ToastType type;
        long born;

        Toast(String sender, String preview, ToastType type) {
            this.sender  = sender;
            this.preview = preview;
            this.type    = type;
            this.born    = System.currentTimeMillis();
        }
    }

    private static final int  MAX_VISIBLE = 3;
    private static final long SHOW_MS     = 4000;
    private static final long FADE_MS     = 500;
    private static final int  TOAST_W     = 160;
    private static final int  TOAST_H     = 28;

    private static final Queue<Toast> pending = new ArrayDeque<>();
    private static final Toast[]      visible = new Toast[MAX_VISIBLE];

    private static volatile long screenOpenedAt = 0;

    public static void markScreenOpened() {
        screenOpenedAt = System.currentTimeMillis();
    }

    public static long getScreenOpenedAt() {
        return screenOpenedAt;
    }

    public static void push(String sender, String content, long msgTimestamp) {

        if (msgTimestamp <= screenOpenedAt) return;
        enqueue(sender, content, ToastType.MESSAGE);
    }

    public static void pushTimer(String message) {
        enqueue(net.minecraft.client.resources.language.I18n.get("icraft.toast.timer_title"), message, ToastType.TIMER);
    }

    private static void enqueue(String sender, String content, ToastType type) {
        String preview = content.length() > 30 ? content.substring(0, 27) + "..." : content;
        synchronized (pending) {
            pending.add(new Toast(sender, preview, type));
        }
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.RENDER_HUD.register(PhoneToast::onRenderGui);
    }

    private static void onRenderGui(GuiGraphics g, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof PhoneScreen) return;

        long now = System.currentTimeMillis();

        synchronized (pending) {
            for (int i = 0; i < MAX_VISIBLE; i++) {
                if (visible[i] == null && !pending.isEmpty()) {
                    visible[i] = pending.poll();
                    visible[i].born = now;
                }
            }
        }

        int screenW    = mc.getWindow().getGuiScaledWidth();
        int toastX     = screenW - TOAST_W - 6;
        int slot       = 0;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            Toast t = visible[i];
            if (t == null) continue;

            long age = now - t.born;
            if (age > SHOW_MS + FADE_MS) {
                visible[i] = null;
                continue;
            }

            float alpha = age > SHOW_MS ? 1f - (float)(age - SHOW_MS) / FADE_MS : 1f;
            int   ia    = (int)(alpha * 255);

            int toastY = 6 + slot * (TOAST_H + 3);
            slot++;

            int bg     = (ia << 24) | 0x00111122;
            int accent = (ia << 24) | t.type.accentColor;
            int white  = (ia << 24) | 0x00FFFFFF;
            int gray   = ((int)(alpha * 180) << 24) | 0x00AAAAAA;

            if (ia <= 0) continue;

            g.fill(toastX,     toastY, toastX + TOAST_W, toastY + TOAST_H, bg);
            g.fill(toastX,     toastY, toastX + 3,        toastY + TOAST_H, accent);

            g.drawString(mc.font, t.type.icon + t.sender, toastX + 6, toastY + 4,  white, false);
            g.drawString(mc.font, t.preview,               toastX + 6, toastY + 15, gray,  false);
        }
    }
}
