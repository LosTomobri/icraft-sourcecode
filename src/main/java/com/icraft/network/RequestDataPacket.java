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
 * Request a refresh of server data (weather, players, marketplace).
 */
public record RequestDataPacket(String dataType) implements CustomPacketPayload {

    public static final Type<RequestDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftMod.MODID, "request_data")
    );

    public static final StreamCodec<ByteBuf, RequestDataPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestDataPacket::dataType,
            RequestDataPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                switch (packet.dataType()) {
                    case "weather" -> PhoneServerHandler.sendWeatherData(serverPlayer);
                    case "players" -> PhoneServerHandler.sendPlayerList(serverPlayer);
                    case "all"     -> PhoneServerHandler.sendAllData(serverPlayer);
                }
            }
        });
    }
}
