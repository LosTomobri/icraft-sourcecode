package com.icraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.icraft.ICraftConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.resources.PlayerSkin;

public class SkinFetcher {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "icraft-skin-fetch");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record FaceTexture(ResourceLocation location, int texW, int texH) {}

    private static final Map<String, FaceTexture> faceCache = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> attempted = new ConcurrentHashMap<>();

    public static void cacheFromPlayerInfo(String username, PlayerSkin skin) {
        if (username == null || username.isBlank() || skin == null) return;
        String key = username.toLowerCase(Locale.ROOT);

        if (faceCache.containsKey(key)) return;
        ResourceLocation loc = skin.texture();
        if (loc == null) return;

        faceCache.put(key, new FaceTexture(loc, 64, 64));

        attempted.putIfAbsent(key, Boolean.TRUE);
    }

    public static FaceTexture getFace(String username) {
        if (username == null || username.isBlank()) return null;
        String key = username.toLowerCase(Locale.ROOT);
        FaceTexture cached = faceCache.get(key);
        if (cached != null) return cached;
        if (attempted.putIfAbsent(key, Boolean.TRUE) == null) {
            EXECUTOR.submit(() -> fetch(key, username));
        }
        return null;
    }

    private static void fetch(String key, String username) {
        try {

            HttpResponse<String> uuidResp = HTTP.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (uuidResp.statusCode() != 200 || uuidResp.body() == null || uuidResp.body().isBlank()) {
                return;
            }
            String id = JsonParser.parseString(uuidResp.body()).getAsJsonObject()
                    .get("id").getAsString();

            HttpResponse<String> profResp = HTTP.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (profResp.statusCode() != 200) return;
            JsonObject profJson = JsonParser.parseString(profResp.body()).getAsJsonObject();
            if (!profJson.has("properties")) return;
            String texturesB64 = profJson.getAsJsonArray("properties")
                    .get(0).getAsJsonObject().get("value").getAsString();
            JsonObject texturesJson = JsonParser.parseString(
                    new String(Base64.getDecoder().decode(texturesB64))).getAsJsonObject();
            JsonObject skinObj = texturesJson.getAsJsonObject("textures").getAsJsonObject("SKIN");
            String skinUrl = skinObj.get("url").getAsString();

            HttpResponse<byte[]> skinResp = HTTP.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(skinUrl))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (skinResp.statusCode() != 200) return;

            NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(skinResp.body()));
            int w = img.getWidth();
            int h = img.getHeight();

            Minecraft.getInstance().execute(() -> {
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                        "icraft", "dynamic/skin/" + key.replaceAll("[^a-z0-9_]", "_"));
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                faceCache.put(key, new FaceTexture(loc, w, h));
            });
        } catch (Exception e) {
            ICraftConstants.LOGGER.debug("[iCraft] No se pudo obtener la skin de {} desde Mojang: {}",
                    username, e.getMessage());
        }
    }
}
