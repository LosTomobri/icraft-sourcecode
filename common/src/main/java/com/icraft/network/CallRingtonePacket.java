package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import dev.architectury.networking.NetworkManager;

public record CallRingtonePacket(
        String soundId
) implements CustomPacketPayload {

    public static final Type<CallRingtonePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "call_ringtone")
    );

    public static final StreamCodec<ByteBuf, CallRingtonePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CallRingtonePacket::soundId,
            CallRingtonePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CallRingtonePacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
                PhoneServerHandler.setCallRingtone(serverPlayer, packet.soundId());
            }
        });
    }
}
