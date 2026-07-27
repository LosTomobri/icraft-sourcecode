package com.icraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PhoneLang {

    public static final String[] SUPPORTED = { "", "en_us", "es_es", "ja_jp" };

    private static final Map<String, Map<String, String>> CACHE = new LinkedHashMap<>();

    private PhoneLang() {}

    public static String get(String key, Object... args) {
        String lang = PhoneScreen.getPhoneData().language;
        if (lang == null || lang.isEmpty()) {
            return I18n.get(key, args);
        }
        String pattern = load(lang).get(key);
        if (pattern == null) {
            return I18n.get(key, args);
        }
        try {
            return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }

    public static String currentDisplayName() {
        return displayName(PhoneScreen.getPhoneData().language);
    }

    public static String displayName(String lang) {
        if (lang == null) lang = "";
        return switch (lang) {
            case "en_us" -> "English";
            case "es_es" -> "Español";
            case "ja_jp" -> "日本語";
            default -> I18n.get("icraft.phone.settings.language_auto");
        };
    }

    public static String next(String current) {
        int idx = 0;
        for (int i = 0; i < SUPPORTED.length; i++) {
            if (SUPPORTED[i].equals(current)) { idx = i; break; }
        }
        return SUPPORTED[(idx + 1) % SUPPORTED.length];
    }

    /**
     * Locale a usar para formateo de fecha/hora (nombres de dias, meses, etc).
     * Sigue el idioma elegido en los ajustes del telefono; si esta en "Auto"
     * sigue el idioma del propio juego, en vez del locale del sistema operativo.
     */
    public static Locale currentLocale() {
        String lang = PhoneScreen.getPhoneData().language;
        if (lang == null || lang.isEmpty()) {
            lang = Minecraft.getInstance().options.languageCode;
        }
        return toLocale(lang);
    }

    private static Locale toLocale(String lang) {
        if (lang == null || lang.isEmpty()) return Locale.getDefault();
        String[] parts = lang.split("_");
        if (parts.length >= 2) return new Locale(parts[0], parts[1].toUpperCase(Locale.ROOT));
        return new Locale(parts[0]);
    }

    private static synchronized Map<String, String> load(String lang) {
        Map<String, String> cached = CACHE.get(lang);
        if (cached != null) return cached;
        Map<String, String> loaded = readJson(lang);
        CACHE.put(lang, loaded);
        return loaded;
    }

    private static Map<String, String> readJson(String lang) {
        Map<String, String> out = new LinkedHashMap<>();
        String path = "/assets/icraft/lang/" + lang + ".json";
        try (InputStream in = PhoneLang.class.getResourceAsStream(path)) {
            if (in == null) return out;
            JsonObject obj = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : obj.keySet()) {
                out.put(key, obj.get(key).getAsString());
            }
        } catch (IOException | RuntimeException e) {

        }
        return out;
    }
}
