package com.icraft.network;

import com.icraft.ICraftMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPackets {

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ICraftMod.MODID).versioned("1.0");

        // Server -> Client packets
        registrar.playToClient(
                ChatMessagePacket.TYPE,
                ChatMessagePacket.STREAM_CODEC,
                ChatMessagePacket::handle
        );

        registrar.playToClient(
                PlayerListPacket.TYPE,
                PlayerListPacket.STREAM_CODEC,
                PlayerListPacket::handle
        );

        registrar.playToClient(
                WeatherPacket.TYPE,
                WeatherPacket.STREAM_CODEC,
                WeatherPacket::handle
        );

        registrar.playToClient(
                WorldIconPacket.TYPE,
                WorldIconPacket.STREAM_CODEC,
                WorldIconPacket::handle
        );

        registrar.playToClient(
                ContactsPacket.TYPE,
                ContactsPacket.STREAM_CODEC,
                ContactsPacket::handle
        );

        registrar.playToClient(
                PhoneOpenSyncPacket.TYPE,
                PhoneOpenSyncPacket.STREAM_CODEC,
                PhoneOpenSyncPacket::handle
        );

        registrar.playToClient(
                AdminPhotoPacket.TYPE,
                AdminPhotoPacket.STREAM_CODEC,
                AdminPhotoPacket::handle
        );

        // Client -> Server packets
        registrar.playToServer(
                SendChatPacket.TYPE,
                SendChatPacket.STREAM_CODEC,
                SendChatPacket::handle
        );

        registrar.playToServer(
                PhoneOpenStatePacket.TYPE,
                PhoneOpenStatePacket.STREAM_CODEC,
                PhoneOpenStatePacket::handle
        );

        registrar.playToServer(
                MarketplacePacket.TYPE,
                MarketplacePacket.STREAM_CODEC,
                MarketplacePacket::handle
        );

        registrar.playToServer(
                RequestDataPacket.TYPE,
                RequestDataPacket.STREAM_CODEC,
                RequestDataPacket::handle
        );

        registrar.playToServer(
                PhotoUploadPacket.TYPE,
                PhotoUploadPacket.STREAM_CODEC,
                PhotoUploadPacket::handle
        );

        registrar.playToServer(
                PrintPhotoPacket.TYPE,
                PrintPhotoPacket.STREAM_CODEC,
                PrintPhotoPacket::handle
        );

        registrar.playToServer(
                RequestPhotoPacket.TYPE,
                RequestPhotoPacket.STREAM_CODEC,
                RequestPhotoPacket::handle
        );
    }
}
