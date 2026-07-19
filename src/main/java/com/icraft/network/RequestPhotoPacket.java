package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet CLIENTE -> SERVIDOR: "no tengo esta foto en mi carpeta local, mandámela".
 *
 * Se dispara desde PhotoFrameRenderer cuando un cuadro de foto referencia un
 * archivo que no existe en <gameDir>/iCraft/photos/ del cliente actual. Esto
 * pasa con cualquier jugador que no haya sido quien imprimió la foto original
 * (otro jugador en el mismo server, alguien que se conectó después, o el mismo
 * jugador en una instalación distinta de Minecraft): la imagen vivía solo en el
 * disco del autor, nunca se sincronizaba con el resto.
 *
 * El servidor busca el archivo en su repositorio central de fotos (world_photos/,
 * con fallback a admin_photos/ y shared_photos/) y, si lo encuentra, lo devuelve
 * con un AdminPhotoPacket solo al jugador que lo pidió.
 */
public record RequestPhotoPacket(String filename) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPhotoPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "request_photo"));

    public static final StreamCodec<ByteBuf, RequestPhotoPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestPhotoPacket::filename,
            RequestPhotoPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestPhotoPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                PhoneServerHandler.handleRequestPhoto(sp, pkt.filename());
            }
        });
    }
}
