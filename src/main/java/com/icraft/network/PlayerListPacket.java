package com.icraft.network;

import com.icraft.ICraftMod;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Packet: Server -> Client
 * Sends the list of online players to the phone's contact list.
 */
public record PlayerListPacket(
        List<String> playerNames
) implements CustomPacketPayload {

    public static final Type<PlayerListPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "player_list")
    );

    public static final StreamCodec<ByteBuf, PlayerListPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PlayerListPacket::playerNames,
            PlayerListPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerListPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof PhoneScreen phoneScreen) {
                phoneScreen.updatePlayerList(packet.playerNames());
            }
        });
    }
}
