package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet CLIENTE -> SERVIDOR: el jugador apretó "Imprimir foto" en la Impresora.
 *
 * Antes, PrinterScreen.printSelected() agregaba el ItemStack directamente al
 * Player del CLIENTE (player.getInventory().add(...)). Eso nunca le llegaba al
 * servidor: el inventario real (autoritativo) del jugador en el servidor no se
 * enteraba de nada, así que al intentar colgar la "foto" en la pared, el
 * servidor no veía ningún ítem válido en la mano y la colocación fallaba en
 * silencio (parecía que imprimía, pero no pasaba nada al usarla).
 *
 * Ahora el cliente solo manda este paquete con el nombre de archivo y el PNG en
 * Base64. El servidor:
 *   1. Valida y guarda la imagen en <world>/iCraft/world_photos/ (repositorio
 *      central, persistente, ya no depende de que el archivo siga existiendo
 *      solo en la carpeta local del jugador que la imprimió).
 *   2. Crea el ItemStack de verdad y lo agrega al inventario real del jugador.
 *   3. Reenvía la imagen (AdminPhotoPacket) a todos los jugadores conectados,
 *      para que ya la tengan cacheada cuando vean el cuadro colgado.
 */
public record PrintPhotoPacket(String filename, String base64Png) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PrintPhotoPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "print_photo"));

    public static final StreamCodec<ByteBuf, PrintPhotoPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PrintPhotoPacket decode(ByteBuf buf) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    String filename  = fb.readUtf(256);
                    String base64Png = new String(fb.readByteArray(),
                                                  java.nio.charset.StandardCharsets.US_ASCII);
                    return new PrintPhotoPacket(filename, base64Png);
                }

                @Override
                public void encode(ByteBuf buf, PrintPhotoPacket pkt) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    fb.writeUtf(pkt.filename(), 256);
                    fb.writeByteArray(pkt.base64Png()
                                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PrintPhotoPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                PhoneServerHandler.handlePrintPhoto(sp, pkt.filename(), pkt.base64Png());
            }
        });
    }
}
