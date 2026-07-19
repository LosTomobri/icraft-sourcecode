package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.server.PhoneServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet: Client -> Server
 *
 * El cliente avisa al servidor cuando el jugador local abre o cierra el
 * PhoneScreen, para que el servidor pueda reenviarle ese estado a los
 * demás jugadores (ver PhoneOpenSyncPacket) y así todos vean el modelo
 * de celular "abierto" en la mano de quien lo esté usando, no solo el
 * propio jugador.
 */
public record PhoneOpenStatePacket(
        boolean open
) implements CustomPacketPayload {

    public static final Type<PhoneOpenStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "phone_open_state")
    );

    public static final StreamCodec<ByteBuf, PhoneOpenStatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PhoneOpenStatePacket::open,
            PhoneOpenStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PhoneOpenStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                PhoneServerHandler.broadcastPhoneOpenState(serverPlayer, packet.open());
            }
        });
    }
}
