package com.icraft.client;

import com.icraft.ICraftConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ClickSounds {

    private static final String FOLDER = "sounds/clicks";

    private static List<String> cached = null;

    private static SoundInstance currentPreview = null;

    private ClickSounds() {}

    public static synchronized List<String> getAvailable() {
        if (cached != null) return cached;

        List<String> found = new ArrayList<>();
        try {
            Map<ResourceLocation, Resource> resources = Minecraft.getInstance().getResourceManager()
                    .listResources(FOLDER, loc -> loc.getNamespace().equals(ICraftConstants.MODID) && loc.getPath().endsWith(".ogg"));

            for (ResourceLocation loc : resources.keySet()) {
                String path = loc.getPath();
                int slash = path.lastIndexOf('/');
                String name = path.substring(slash + 1, path.length() - 4);
                if (!name.isEmpty()) found.add(name);
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("No se pudieron listar los sonidos de click", e);
        }

        Collections.sort(found);
        cached = found;
        return found;
    }

    public static synchronized void invalidate() {
        cached = null;
    }

    public static void play(String name) {
        if (name == null || name.isEmpty()) return;
        if (!getAvailable().contains(name)) return;

        var soundManager = Minecraft.getInstance().getSoundManager();
        if (currentPreview != null) {
            soundManager.stop(currentPreview);
        }

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "click." + name);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        currentPreview = SimpleSoundInstance.forUI(event, 1.0F);
        soundManager.play(currentPreview);
    }
}
