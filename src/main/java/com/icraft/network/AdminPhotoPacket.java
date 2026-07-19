package com.icraft.network;

import com.icraft.ICraftMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Paquete servidor -> cliente para enviar una imagen (admin_photos/, shared_photos/
 * o world_photos/) a uno o varios jugadores.
 *
 * Campos:
 *   filename  - nombre del archivo, ej. "cartel.png". El cliente lo guarda en
 *               iCraft/photos/<filename> (PhoneScreen.receiveAdminPhoto).
 *   base64Png - contenido del PNG codificado en Base64.
 *
 * NOTA IMPORTANTE: el base64 se envía como byte[] (writeByteArray/readByteArray)
 * y NO como String con writeUtf/readUtf. writeUtf() tiene un límite de 32767
 * caracteres por defecto: cualquier imagen de más de ~24 KB (una vez pasada a
 * Base64, que infla el tamaño ~33%) hacía que el codec lanzara una excepción al
 * codificar y el paquete jamás llegaba. Con writeByteArray() no hay ese límite
 * (solo el límite general de paquete de Minecraft, muy por encima de los 512 KB
 * que ya validamos en el servidor).
 *
 * Límite: 512 KB por imagen (ver PhoneServerHandler.MAX_ADMIN_PHOTO_BYTES).
 */
public record AdminPhotoPacket(String filename, String base64Png)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminPhotoPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "admin_photo"));

    public static final StreamCodec<ByteBuf, AdminPhotoPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AdminPhotoPacket decode(ByteBuf buf) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    String filename  = fb.readUtf(256);
                    // Base64 como byte array: sin límite de 32767 chars (ver nota arriba)
                    String base64Png = new String(fb.readByteArray(),
                                                  java.nio.charset.StandardCharsets.US_ASCII);
                    return new AdminPhotoPacket(filename, base64Png);
                }

                @Override
                public void encode(ByteBuf buf, AdminPhotoPacket pkt) {
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

    /**
     * Handler cliente: guarda el PNG recibido en iCraft/photos/ y refresca
     * cualquier textura cacheada (cuadros de fotos, galería del teléfono, etc.)
     * que estuviera esperando este archivo.
     */
    public static void handle(AdminPhotoPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.icraft.client.PhoneScreen.receiveAdminPhoto(pkt.filename(), pkt.base64Png())
        );
    }
}
