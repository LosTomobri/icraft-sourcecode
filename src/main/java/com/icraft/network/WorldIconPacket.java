package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet: Server -> Client
 *
 * Envía la imagen real icon.png del mundo (leída de la carpeta del save en
 * el servidor) codificada en Base64, para usarla como foto del chat global
 * "Global" en vez de un ícono genérico.
 *
 * Si el mundo no tiene icon.png, base64Png llega vacío ("") y el cliente
 * usa su ícono de globo de respaldo.
 */
public record WorldIconPacket(
        String base64Png
) implements CustomPacketPayload {

    public static final Type<WorldIconPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "world_icon")
    );

    public static final StreamCodec<ByteBuf, WorldIconPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WorldIconPacket::base64Png,
            WorldIconPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WorldIconPacket packet, IPayloadContext ctx) {
        // No depende de que la pantalla del celular esté abierta: la imagen
        // queda lista en cuanto llega, para cuando el jugador abra el chat.
        ctx.enqueueWork(() -> PhoneScreen.applyWorldIcon(packet.base64Png()));
    }
}
