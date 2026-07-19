package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Packet: Server -> Client
 *
 * Lista de TODOS los jugadores que alguna vez se conectaron a este server
 * (no solo los que están online en este momento). Se usa para poblar la
 * app de "Contactos" del celular. El estado online/offline de cada uno se
 * resuelve en el cliente comparando contra la lista de PlayerListPacket.
 */
public record ContactsPacket(
        List<String> players
) implements CustomPacketPayload {

    public static final Type<ContactsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "contacts")
    );

    public static final StreamCodec<ByteBuf, ContactsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ContactsPacket::players,
            ContactsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContactsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> PhoneScreen.applyContacts(packet.players()));
    }
}
