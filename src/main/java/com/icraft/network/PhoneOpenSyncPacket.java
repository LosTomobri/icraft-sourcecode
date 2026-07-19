package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.client.PhoneOpenTracker;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Packet: Server -> Client
 *
 * Avisa a todos los clientes que un jugador (playerId) abrió o cerró su
 * celular, para que el modelo "abierto" (ver overrides en
 * smartphone.json / ClientSetup#onClientSetup) se muestre también en la
 * mano de ese jugador para los DEMÁS clientes, no solo para el suyo.
 */
public record PhoneOpenSyncPacket(
        UUID playerId,
        boolean open
) implements CustomPacketPayload {

    public static final Type<PhoneOpenSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "phone_open_sync")
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

    public static void handle(PhoneOpenSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (packet.open()) {
                PhoneOpenTracker.markOpen(packet.playerId());
            } else {
                PhoneOpenTracker.markClosed(packet.playerId());
            }
        });
    }
}
