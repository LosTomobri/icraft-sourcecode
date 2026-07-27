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

public record MarketplacePacket(
        String action,
        String listingId,
        String itemId,
        String itemName,
        int quantity,
        int price,
        String description
) implements CustomPacketPayload {

    public static final Type<MarketplacePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "marketplace")
    );

    public static final StreamCodec<ByteBuf, MarketplacePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.action);
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.listingId);
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.itemId);
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.itemName);
                ByteBufCodecs.VAR_INT.encode(buf, packet.quantity);
                ByteBufCodecs.VAR_INT.encode(buf, packet.price);
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.description);
            },
            buf -> new MarketplacePacket(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MarketplacePacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
                PhoneServerHandler.handleMarketplace(serverPlayer, packet);
            }
        });
    }
}
