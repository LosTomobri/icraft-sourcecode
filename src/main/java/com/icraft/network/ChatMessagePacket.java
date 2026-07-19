package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet: Server -> Client
 * Delivers a chat message to the recipient.
 *
 * NOTA: incluye messageId para poder deduplicar por ID en el cliente
 * en vez de por timestamp (el timestamp puede diferir levemente entre
 * el momento en que el cliente crea el mensaje localmente y el momento
 * en que el servidor lo registra, causando falsos duplicados).
 */
public record ChatMessagePacket(
        String conversationId,
        String senderName,
        String content,
        long timestamp,
        boolean isGroup,
        String messageId
) implements CustomPacketPayload {

    public static final Type<ChatMessagePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "chat_message")
    );

    public static final StreamCodec<ByteBuf, ChatMessagePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ChatMessagePacket::conversationId,
            ByteBufCodecs.STRING_UTF8, ChatMessagePacket::senderName,
            ByteBufCodecs.STRING_UTF8, ChatMessagePacket::content,
            ByteBufCodecs.VAR_LONG,    ChatMessagePacket::timestamp,
            ByteBufCodecs.BOOL,        ChatMessagePacket::isGroup,
            ByteBufCodecs.STRING_UTF8, ChatMessagePacket::messageId,
            ChatMessagePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChatMessagePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Always store the message (static data persists when screen is closed).
            // receiveMessageStatic deduplica por messageId. El mensaje se crea con
            // read=false por defecto, y el contador de no leídos se calcula en vivo
            // a partir de eso — no hace falta incrementar nada manualmente acá.
            PhoneScreen.receiveMessageStatic(packet.conversationId(), packet.senderName(),
                    packet.content(), packet.timestamp(), packet.isGroup(), packet.messageId());
        });
    }
}
