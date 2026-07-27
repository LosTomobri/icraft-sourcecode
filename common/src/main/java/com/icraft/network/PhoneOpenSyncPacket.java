package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.client.PhoneOpenTracker;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

import java.util.UUID;

public record PhoneOpenSyncPacket(
        UUID playerId,
        boolean open
) implements CustomPacketPayload {

    public static final Type<PhoneOpenSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "phone_open_sync")
    );

    public static final StreamCodec<ByteBuf, PhoneOpenSyncPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PhoneOpenSyncPacket::playerId,
            ByteBufCodecs.BOOL,     PhoneOpenSyncPacket::open,
            PhoneOpenSyncPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PhoneOpenSyncPacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (packet.open()) {
                PhoneOpenTracker.markOpen(packet.playerId());
            } else {
                PhoneOpenTracker.markClosed(packet.playerId());
            }
        });
    }
}
