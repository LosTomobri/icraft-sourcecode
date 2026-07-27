package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record ChatMessagePacket(
        String conversationId,
        String senderName,
        String content,
        long timestamp,
        boolean isGroup,
        String messageId
) implements CustomPacketPayload {

    public static final Type<ChatMessagePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "chat_message")
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

    public static void handle(ChatMessagePacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {

            PhoneScreen.receiveMessageStatic(packet.conversationId(), packet.senderName(),
                    packet.content(), packet.timestamp(), packet.isGroup(), packet.messageId());
        });
    }
}
