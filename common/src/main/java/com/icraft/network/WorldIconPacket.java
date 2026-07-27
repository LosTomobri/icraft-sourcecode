package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record WorldIconPacket(
        String base64Png
) implements CustomPacketPayload {

    public static final Type<WorldIconPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "world_icon")
    );

    public static final StreamCodec<ByteBuf, WorldIconPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WorldIconPacket::base64Png,
            WorldIconPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WorldIconPacket packet, NetworkManager.PacketContext ctx) {

        ctx.queue(() -> PhoneScreen.applyWorldIcon(packet.base64Png()));
    }
}
