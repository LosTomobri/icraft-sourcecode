package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record PhotoUploadPacket(
        String filename,
        String base64Png,
        String convId,
        boolean isGroup,
        String recipientName
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhotoUploadPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "photo_upload"));

    public static final StreamCodec<ByteBuf, PhotoUploadPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PhotoUploadPacket decode(ByteBuf buf) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    String filename      = fb.readUtf(256);

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

    public static void handle(PhotoUploadPacket pkt, NetworkManager.PacketContext ctx) {
        ctx.queue(() ->
            PhoneServerHandler.handlePhotoUpload(
                (net.minecraft.server.level.ServerPlayer) ctx.getPlayer(),
                pkt.filename(), pkt.base64Png(),
                pkt.convId(), pkt.isGroup(), pkt.recipientName()
            )
        );
    }
}
