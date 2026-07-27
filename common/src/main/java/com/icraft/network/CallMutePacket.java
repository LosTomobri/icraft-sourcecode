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

public record CallMutePacket(
        boolean muted
) implements CustomPacketPayload {

    public static final Type<CallMutePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "call_mute")
    );

    public static final StreamCodec<ByteBuf, CallMutePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CallMutePacket::muted,
            CallMutePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CallMutePacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
                PhoneServerHandler.handleCallMute(serverPlayer, packet.muted());
            }
        });
    }
}
