package com.icraft.network;

import com.icraft.ICraftConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record AdminPhotoPacket(String filename, String base64Png)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminPhotoPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "admin_photo"));

    public static final StreamCodec<ByteBuf, AdminPhotoPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AdminPhotoPacket decode(ByteBuf buf) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    String filename  = fb.readUtf(256);

                    String base64Png = new String(fb.readByteArray(),
                                                  java.nio.charset.StandardCharsets.US_ASCII);
                    return new AdminPhotoPacket(filename, base64Png);
                }

                @Override
                public void encode(ByteBuf buf, AdminPhotoPacket pkt) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    fb.writeUtf(pkt.filename(), 256);
                    fb.writeByteArray(pkt.base64Png()
                                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminPhotoPacket pkt, NetworkManager.PacketContext ctx) {
        ctx.queue(() ->
            com.icraft.client.PhoneScreen.receiveAdminPhoto(pkt.filename(), pkt.base64Png())
        );
    }
}
