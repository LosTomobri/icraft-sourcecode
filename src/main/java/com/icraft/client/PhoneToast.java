package com.icraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * PhoneToast — muestra notificaciones tipo toast en el HUD cuando llega un mensaje
 * con el teléfono cerrado. Se renderiza encima del HUD de Minecraft.
 */
@EventBusSubscriber(modid = "icraft", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class PhoneToast {

    /** Tipo de toast: cambia el color de acento y el ícono del título.
     *  MESSAGE = mensaje de chat (violeta), TIMER = aviso de temporizador (naranja). */
    public enum ToastType {
        MESSAGE(0x00AA66FF, "\uD83D\uDCF1 "), // 📱 violeta
        TIMER(0x00FF9800,   "\u23F0 ");       // ⏰ naranja

        final int    accentColor; // RGB sin canal alfa, se le suma el alpha del fade en runtime
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

    // Solo se muestran toasts de mensajes cuyo timestamp sea posterior al momento
    // en que se abrió la pantalla del teléfono. Los mensajes del historial tienen
    // timestamp anterior y se descartan automáticamente sin depender de timers.
    private static volatile long screenOpenedAt = 0;

    /**
     * Registra el momento en que se abrió la pantalla del celular.
     * Todo mensaje con timestamp anterior a este valor es historial y no genera toast.
     * Llamar desde PhoneScreen justo antes de pedir los datos al servidor.
     */
    public static void markScreenOpened() {
        screenOpenedAt = System.currentTimeMillis();
    }

    /**
     * Encola un toast de mensaje de chat solo si el mensaje es nuevo
     * (su timestamp es posterior a cuando se abrió la pantalla del teléfono).
     *
     * @param sender    nombre del remitente
     * @param content   contenido del mensaje
     * @param msgTimestamp timestamp del mensaje (System.currentTimeMillis() del servidor)
     */
    public static void push(String sender, String content, long msgTimestamp) {
        // Si el mensaje es anterior (o igual) al momento de apertura, es historial → ignorar
        if (msgTimestamp <= screenOpenedAt) return;
        enqueue(sender, content, ToastType.MESSAGE);
    }

    /** Encola un toast de aviso de temporizador terminado (color naranja, distinto del de mensajes). */
    public static void pushTimer(String message) {
        enqueue("Temporizador", message, ToastType.TIMER);
    }

    private static void enqueue(String sender, String content, ToastType type) {
        String preview = content.length() > 30 ? content.substring(0, 27) + "..." : content;
        synchronized (pending) {
            pending.add(new Toast(sender, preview, type));
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // No mostrar si el teléfono está abierto
        if (mc.screen instanceof PhoneScreen) return;

        long now = System.currentTimeMillis();

        // Promover pending → visible
        synchronized (pending) {
            for (int i = 0; i < MAX_VISIBLE; i++) {
                if (visible[i] == null && !pending.isEmpty()) {
                    visible[i] = pending.poll();
                    visible[i].born = now;
                }
            }
        }

        GuiGraphics g  = event.getGuiGraphics();
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

            // Calcular alpha (fade-out al final)
            float alpha = age > SHOW_MS ? 1f - (float)(age - SHOW_MS) / FADE_MS : 1f;
            int   ia    = (int)(alpha * 255);

            int toastY = 6 + slot * (TOAST_H + 3);
            slot++;

            // Fondo oscuro semitransparente
            int bg     = (ia << 24) | 0x00111122;          // alpha | color RGB
            int accent = (ia << 24) | t.type.accentColor;  // borde izquierdo: violeta (mensaje) o naranja (temporizador)
            int white  = (ia << 24) | 0x00FFFFFF;
            int gray   = ((int)(alpha * 180) << 24) | 0x00AAAAAA;

            // Asegurarse de que alpha > 0 antes de renderizar
            if (ia <= 0) continue;

            g.fill(toastX,     toastY, toastX + TOAST_W, toastY + TOAST_H, bg);
            g.fill(toastX,     toastY, toastX + 3,        toastY + TOAST_H, accent);

            // Texto: ícono + nombre/título según el tipo, y preview
            g.drawString(mc.font, t.type.icon + t.sender, toastX + 6, toastY + 4,  white, false);
            g.drawString(mc.font, t.preview,               toastX + 6, toastY + 15, gray,  false);
        }
    }
}
