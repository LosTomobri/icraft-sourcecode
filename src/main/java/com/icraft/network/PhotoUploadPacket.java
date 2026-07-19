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
 * Packet CLIENTE → SERVIDOR para compartir una foto entre jugadores.
 *
 * Cuando un jugador comparte una foto de su galería en un DM o grupo privado,
 * el cliente envía este packet ANTES del SendChatPacket con §§PHOTO:.
 * El servidor recibe el PNG (Base64), lo guarda en shared_photos/ y lo reenvía
 * a los destinatarios online vía AdminPhotoPacket, para que lo tengan localmente
 * antes de que llegue la burbuja del chat.
 *
 * Se usa un packet separado del SendChatPacket porque Minecraft limita los
 * Strings en custom payloads a 32767 caracteres, y un PNG en Base64 supera
 * ese límite fácilmente. Este packet usa writeByteArray() sin ese límite.
 */
public record PhotoUploadPacket(
        String filename,
        String base64Png,
        String convId,
        boolean isGroup,
        String recipientName
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhotoUploadPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "photo_upload"));

    public static final StreamCodec<ByteBuf, PhotoUploadPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PhotoUploadPacket decode(ByteBuf buf) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    String filename      = fb.readUtf(256);
                    // Base64 como byte array: sin límite de 32767 chars
                    String base64Png     = new String(fb.readByteArray(),
                                                      java.nio.charset.StandardCharsets.US_ASCII);
                    String convId        = fb.readUtf(256);
                    boolean isGroup      = fb.readBoolean();
                    String recipientName = fb.readUtf(64);
                    return new PhotoUploadPacket(filename, base64Png, convId, isGroup, recipientName);
                }

                @Override
                public void encode(ByteBuf buf, PhotoUploadPacket pkt) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    fb.writeUtf(pkt.filename(), 256);
                    // Base64 como byte array: sin límite de 32767 chars
                    fb.writeByteArray(pkt.base64Png()
                                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    fb.writeUtf(pkt.convId(), 256);
                    fb.writeBoolean(pkt.isGroup());
                    fb.writeUtf(pkt.recipientName() != null ? pkt.recipientName() : "", 64);
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Handler servidor: guarda el PNG y lo reenvía a los destinatarios. */
    public static void handle(PhotoUploadPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            PhoneServerHandler.handlePhotoUpload(
                (net.minecraft.server.level.ServerPlayer) ctx.player(),
                pkt.filename(), pkt.base64Png(),
                pkt.convId(), pkt.isGroup(), pkt.recipientName()
            )
        );
    }
}
