package com.icraft.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;

/**
 * Punto de entrada específico de NeoForge para Simple Voice Chat.
 * Toda la lógica real está en com.icraft.voicechat.ICraftVoicechatPlugin (common).
 * Esta subclase solo existe para llevar la anotación @ForgeVoicechatPlugin,
 * que es lo único que difiere entre NeoForge y Fabric en este punto (ver TODO fabric).
 */
@ForgeVoicechatPlugin
public class ICraftVoicechatPluginNeoForge extends ICraftVoicechatPlugin {
}
