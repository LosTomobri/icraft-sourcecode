package com.icraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.icraft.ICraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.client.resources.PlayerSkin;
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

/**
 * Busca la "cara" (skin) de un jugador directamente desde la API pública de
 * Mojang, usando únicamente su nombre de usuario.
 *
 * == POR QUÉ HACE FALTA ESTO ==
 * Cuando el servidor corre en modo "offline" (típico de lanzadores no
 * premium, como SKlauncher), el perfil (GameProfile) que llega al cliente
 * NO incluye la textura de skin firmada por Mojang. Por eso
 * {@code PlayerInfo#getSkin()} sólo devuelve la skin por defecto
 * (Steve/Alex) para todos los jugadores, sin importar la skin real que
 * tengan puesta.
 *
 * La solución es la misma que usan plugins tipo "SkinRestorer": en vez de
 * depender de los datos de la conexión, el propio mod le pregunta
 * directamente a Mojang "¿qué skin tiene la cuenta que se llama X?" usando
 * sus endpoints públicos (no requieren que quien consulta sea premium):
 *   1) GET api.mojang.com/users/profiles/minecraft/{username}  -> uuid
 *   2) GET sessionserver.mojang.com/session/minecraft/profile/{uuid} -> textura
 *   3) GET la URL de la textura -> el PNG de la skin
 *
 * Esto funciona en servidores offline/SKlauncher siempre que el nombre que
 * usa el jugador coincida con una cuenta real de Mojang que tenga esa skin.
 * Si el nombre es inventado y no corresponde a ninguna cuenta real, no
 * existe ninguna skin para mostrar en ningún lado — eso ya no se puede
 * resolver por software, así que en ese caso se cae al avatar de respaldo
 * (cuadrado de color con la inicial).
 */
public class SkinFetcher {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "icraft-skin-fetch");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Textura ya cargada de la cara de un jugador + sus dimensiones reales
     *  (las skins pueden ser 64x64 -formato nuevo- o 64x32 -formato viejo-). */
    public record FaceTexture(ResourceLocation location, int texW, int texH) {}

    // username (en minúsculas) -> textura ya cacheada
    private static final Map<String, FaceTexture> faceCache = new ConcurrentHashMap<>();
    // usernames que ya se intentaron (para no reintentar todo el rato si falla)
    private static final Map<String, Boolean> attempted = new ConcurrentHashMap<>();

    /**
     * Cachea la skin de un jugador desde su PlayerSkin (disponible mientras está online).
     * Así cuando se desconecte seguimos pudiendo mostrar su cara sin ir a Mojang.
     * Se llama cada vez que dibujamos la cara de un jugador online.
     */
    public static void cacheFromPlayerInfo(String username, PlayerSkin skin) {
        if (username == null || username.isBlank() || skin == null) return;
        String key = username.toLowerCase(Locale.ROOT);
        // Si ya lo tenemos cacheado no hacemos nada
        if (faceCache.containsKey(key)) return;
        ResourceLocation loc = skin.texture();
        if (loc == null) return;
        // Guardamos la textura con dimensiones estándar de skin (64x64)
        faceCache.put(key, new FaceTexture(loc, 64, 64));
        // Marcar como intentado para no lanzar fetch HTTP innecesario
        attempted.putIfAbsent(key, Boolean.TRUE);
    }

    /**
     * Devuelve la textura de la cara del jugador si ya se descargó y cacheó.
     * Si todavía no se intentó buscar, dispara la búsqueda en segundo plano
     * (sin bloquear el hilo de render) y devuelve null por ahora — el
     * llamador debe tener un avatar de respaldo mientras tanto.
     */
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
            // 1) nombre -> uuid (sólo existe si el nombre corresponde a una cuenta real)
            HttpResponse<String> uuidResp = HTTP.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (uuidResp.statusCode() != 200 || uuidResp.body() == null || uuidResp.body().isBlank()) {
                return; // no existe ninguna cuenta de Mojang con ese nombre
            }
            String id = JsonParser.parseString(uuidResp.body()).getAsJsonObject()
                    .get("id").getAsString();

            // 2) uuid -> perfil con propiedades de textura
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

            // 3) descargar el PNG real de la skin
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

            // El registro de la textura en Minecraft debe hacerse en el hilo principal
            Minecraft.getInstance().execute(() -> {
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                        "icraft", "dynamic/skin/" + key.replaceAll("[^a-z0-9_]", "_"));
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                faceCache.put(key, new FaceTexture(loc, w, h));
            });
        } catch (Exception e) {
            ICraftMod.LOGGER.debug("[iCraft] No se pudo obtener la skin de {} desde Mojang: {}",
                    username, e.getMessage());
        }
    }
}
