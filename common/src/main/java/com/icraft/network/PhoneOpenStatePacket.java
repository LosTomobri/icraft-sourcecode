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

public record PhoneOpenStatePacket(
        boolean open
) implements CustomPacketPayload {

    public static final Type<PhoneOpenStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "phone_open_state")
    );

    public static final StreamCodec<ByteBuf, PhoneOpenStatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PhoneOpenStatePacket::open,
            PhoneOpenStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PhoneOpenStatePacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
                PhoneServerHandler.broadcastPhoneOpenState(serverPlayer, packet.open());
            }
        });
    }
}
