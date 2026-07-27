package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

import java.util.List;

public record ContactsPacket(
        List<String> players
) implements CustomPacketPayload {

    public static final Type<ContactsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "contacts")
    );

    public static final StreamCodec<ByteBuf, ContactsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ContactsPacket::players,
            ContactsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContactsPacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> PhoneScreen.applyContacts(packet.players()));
    }
}
