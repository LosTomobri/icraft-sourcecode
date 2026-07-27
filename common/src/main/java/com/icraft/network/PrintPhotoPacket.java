package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record PrintPhotoPacket(String filename, String base64Png) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PrintPhotoPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "print_photo"));

    public static final StreamCodec<ByteBuf, PrintPhotoPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PrintPhotoPacket decode(ByteBuf buf) {
                    FriendlyByteBuf fb = buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
                    String filename  = fb.readUtf(256);
                    String base64Png = new String(fb.readByteArray(),
                                                  java.nio.charset.StandardCharsets.US_ASCII);
                    return new PrintPhotoPacket(filename, base64Png);
                }

                @Override
                public void encode(ByteBuf buf, PrintPhotoPacket pkt) {
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

    public static void handle(PrintPhotoPacket pkt, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
                PhoneServerHandler.handlePrintPhoto(sp, pkt.filename(), pkt.base64Png());
            }
        });
    }
}
