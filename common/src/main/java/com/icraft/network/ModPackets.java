package com.icraft.network;

import dev.architectury.networking.NetworkManager;

/**
 * Paso 3 de la migración: registro vía Architectury Networking API
 * (dev.architectury.networking.NetworkManager) en vez de
 * RegisterPayloadHandlersEvent/PayloadRegistrar de NeoForge.
 *
 * Ya vive en common/ junto con los 20 paquetes (movidos tras resolverse el
 * paso 6): el método handle(...) de cada paquete recibe
 * NetworkManager.PacketContext en vez de IPayloadContext de NeoForge
 * (ctx.enqueueWork -> ctx.queue, ctx.player() -> ctx.getPlayer()), y el
 * registro pasa por NetworkManager.registerReceiver en vez del registrar de
 * NeoForge. TYPE y STREAM_CODEC no cambiaron: CustomPacketPayload y
 * StreamCodec ya son vanilla, no específicos de NeoForge.
 *
 * Las llamadas para ENVIAR paquetes (PhoneScreen.java, PhoneServerHandler.java,
 * PrinterScreen.java, PhotoFrameRenderer.java) ya usan
 * NetworkManager.sendToPlayer/sendToServer — son multi-loader.
 *
 * IMPORTANTE — no verificado contra un build real (sin red saliente en este
 * entorno). Correlo con ./gradlew build (los tres módulos) antes de asumir
 * que compila.
 */
public class ModPackets {

    public static void register() {

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ChatMessagePacket.TYPE,
                ChatMessagePacket.STREAM_CODEC,
                ChatMessagePacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PlayerListPacket.TYPE,
                PlayerListPacket.STREAM_CODEC,
                PlayerListPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                WeatherPacket.TYPE,
                WeatherPacket.STREAM_CODEC,
                WeatherPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                WorldIconPacket.TYPE,
                WorldIconPacket.STREAM_CODEC,
                WorldIconPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ContactsPacket.TYPE,
                ContactsPacket.STREAM_CODEC,
                ContactsPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PhoneOpenSyncPacket.TYPE,
                PhoneOpenSyncPacket.STREAM_CODEC,
                PhoneOpenSyncPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                AdminPhotoPacket.TYPE,
                AdminPhotoPacket.STREAM_CODEC,
                AdminPhotoPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                CallStatusPacket.TYPE,
                CallStatusPacket.STREAM_CODEC,
                CallStatusPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                GlobalImagesSettingPacket.TYPE,
                GlobalImagesSettingPacket.STREAM_CODEC,
                GlobalImagesSettingPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                SendChatPacket.TYPE,
                SendChatPacket.STREAM_CODEC,
                SendChatPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PhoneOpenStatePacket.TYPE,
                PhoneOpenStatePacket.STREAM_CODEC,
                PhoneOpenStatePacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                MarketplacePacket.TYPE,
                MarketplacePacket.STREAM_CODEC,
                MarketplacePacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                RequestDataPacket.TYPE,
                RequestDataPacket.STREAM_CODEC,
                RequestDataPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PhotoUploadPacket.TYPE,
                PhotoUploadPacket.STREAM_CODEC,
                PhotoUploadPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PrintPhotoPacket.TYPE,
                PrintPhotoPacket.STREAM_CODEC,
                PrintPhotoPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                RequestPhotoPacket.TYPE,
                RequestPhotoPacket.STREAM_CODEC,
                RequestPhotoPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CallRequestPacket.TYPE,
                CallRequestPacket.STREAM_CODEC,
                CallRequestPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CallAnswerPacket.TYPE,
                CallAnswerPacket.STREAM_CODEC,
                CallAnswerPacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CallMutePacket.TYPE,
                CallMutePacket.STREAM_CODEC,
                CallMutePacket::handle
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CallRingtonePacket.TYPE,
                CallRingtonePacket.STREAM_CODEC,
                CallRingtonePacket::handle
        );
    }
}
