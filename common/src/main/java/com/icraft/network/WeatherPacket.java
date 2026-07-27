package com.icraft.network;

import com.icraft.ICraftConstants;
import com.icraft.client.PhoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record WeatherPacket(
        boolean isRaining,
        boolean isThundering,
        long dayTime,
        float temperature,
        String biome,
        String dimension
) implements CustomPacketPayload {

    public static final Type<WeatherPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "weather_data")
    );

    public static final StreamCodec<ByteBuf, WeatherPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,        WeatherPacket::isRaining,
            ByteBufCodecs.BOOL,        WeatherPacket::isThundering,
            ByteBufCodecs.VAR_LONG,    WeatherPacket::dayTime,
            ByteBufCodecs.FLOAT,       WeatherPacket::temperature,
            ByteBufCodecs.STRING_UTF8, WeatherPacket::biome,
            ByteBufCodecs.STRING_UTF8, WeatherPacket::dimension,
            WeatherPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeatherPacket packet, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (Minecraft.getInstance().screen instanceof PhoneScreen phoneScreen) {
                phoneScreen.updateWeather(packet);
            }
        });
    }

    public String getWeatherDescription() {
        if (isThundering()) return "⛈️ Tormenta";
        if (isRaining()) return "🌧️ Lluvia";
        return "☀️ Despejado";
    }

    public String getTimeOfDay() {
        long time = dayTime() % 24000;
        if (time < 6000) return "Mañana";
        if (time < 12000) return "Tarde";
        if (time < 12200) return "Noche";
        return "Medianoche";
    }
}
