package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet: Client -> Server
 * Player sends a message to another player or group.
 */
public record SendChatPacket(
        String conversationId,
        String recipientName,    // empty if group message
        String content,
        boolean isGroup,
        boolean deleteForAll,    // true = delete message for everyone
        String messageId         // used for deleteForAll
) implements CustomPacketPayload {

    public static final Type<SendChatPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "send_chat")
    );

    public static final StreamCodec<ByteBuf, SendChatPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SendChatPacket::conversationId,
            ByteBufCodecs.STRING_UTF8, SendChatPacket::recipientName,
            ByteBufCodecs.STRING_UTF8, SendChatPacket::content,
            ByteBufCodecs.BOOL,        SendChatPacket::isGroup,
            ByteBufCodecs.BOOL,        SendChatPacket::deleteForAll,
            ByteBufCodecs.STRING_UTF8, SendChatPacket::messageId,
            SendChatPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SendChatPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                if (packet.deleteForAll()) {
                    PhoneServerHandler.deleteMessageForAll(serverPlayer, packet.conversationId(), packet.messageId());
                } else {
                    PhoneServerHandler.routeMessage(serverPlayer, packet);
                }
            }
        });
    }
}
