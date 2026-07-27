package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.client.GlobalImagesState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record GlobalImagesSettingPacket(
        boolean enabled
) implements CustomPacketPayload {

    public static final Type<GlobalImagesSettingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "global_images_setting")
    );

    public static final StreamCodec<ByteBuf, GlobalImagesSettingPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GlobalImagesSettingPacket::enabled,
            GlobalImagesSettingPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GlobalImagesSettingPacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> GlobalImagesState.setEnabled(packet.enabled()));
    }
}
