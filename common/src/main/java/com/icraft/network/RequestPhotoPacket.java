package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record RequestPhotoPacket(String filename) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPhotoPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "request_photo"));

    public static final StreamCodec<ByteBuf, RequestPhotoPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestPhotoPacket::filename,
            RequestPhotoPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestPhotoPacket pkt, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
                PhoneServerHandler.handleRequestPhoto(sp, pkt.filename());
            }
        });
    }
}
