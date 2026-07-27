package com.icraft.voicechat;

/**
 * Punto de entrada específico de Fabric para Simple Voice Chat.
 * Toda la lógica real está en com.icraft.voicechat.ICraftVoicechatPlugin (common).
 *
 * A diferencia de NeoForge (que usa la anotación @ForgeVoicechatPlugin), Fabric
 * no usa anotación ni META-INF/services: Simple Voice Chat descubre el plugin
 * a través de un entrypoint declarado en fabric.mod.json bajo la clave
 * "voicechat", que debe apuntar al nombre calificado de esta clase. Por eso
 * esta clase no lleva ninguna anotación — solo necesita implementar
 * VoicechatPlugin (heredado de ICraftVoicechatPlugin) y tener un constructor
 * público sin argumentos, que es lo que Fabric Loader instancia por
 * reflexión al resolver el entrypoint.
 *
 * Confirmado contra la documentación oficial de la API (getting_started.md
 * de henkelmax/simple-voice-chat) para la serie 2.6.x, que es la que usa
 * este proyecto (voicechat-api 2.6.20).
 */
public class ICraftVoicechatPluginFabric extends ICraftVoicechatPlugin {
}
