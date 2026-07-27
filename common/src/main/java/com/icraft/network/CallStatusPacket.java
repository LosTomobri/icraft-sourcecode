package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record CallStatusPacket(
        String peerName,
        String status
) implements CustomPacketPayload {

    public static final Type<CallStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "call_status")
    );

    public static final StreamCodec<ByteBuf, CallStatusPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CallStatusPacket::peerName,
            ByteBufCodecs.STRING_UTF8, CallStatusPacket::status,
            CallStatusPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CallStatusPacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> PhoneScreen.applyCallStatus(packet.peerName(), packet.status()));
    }
}
