package com.icraft.data;

import java.util.*;

/**
 * PhoneData — stored per player using Data Attachments.
 * Holds all state: messages, photos, notes, settings, etc.
 */
public class PhoneData {

    // === ICON EDITOR — personalización de íconos del home ===
    // Orden: Chat, Cámara, Fotos, Clima, Reloj, Notas, Market, Ajustes, Guardados
    // Colores por defecto: mismo orden y mismos valores que usa
    // PhoneScreen.applyDefaultAppColorsOnce(), para que el botón "Restablecer
    // todo" del editor de íconos siempre devuelva exactamente estos colores.
    public String[] appIconLabels = {"Chat","Cámara","Fotos","Clima","Reloj","Notas","Market","Ajustes","Contactos"};
    public int[]    appIconColors = {0xFF4CAF50, 0xFF212121, 0xFFFF9800, 0xFF29B6F6,
                                     0xFF607D8B, 0xFF795548, 0xFFF44336, 0xFF607D8B, 0xFFE91E8C};

    /** Restablece íconos a los valores por defecto. */
    public void resetIconDefaults() {
        appIconLabels = new String[]{"Chat","Cámara","Fotos","Clima","Reloj","Notas","Market","Ajustes","Guardados"};
        appIconColors = new int[]{0xFF4CAF50, 0xFF212121, 0xFFFF9800, 0xFF29B6F6,
                                   0xFF607D8B, 0xFF795548, 0xFFF44336, 0xFF607D8B, 0xFFE91E8C};
    }

    // === SETTINGS ===
    public boolean darkMode = true;
    public String currentCase = "default";      // "default","black","white","neon","diamond"
    public String wallpaper = "wp_space";       // wallpaper texture name (no extension)
    public String notificationSound = "ding";  // "ding","chime","buzz","none"
    public boolean doNotDisturb = false;
    public String theme = "blue";              // "blue","green","purple","pink","red"

    // === CHAT ===
    public List<ChatConversation> conversations = new ArrayList<>();
    public List<String> savedMessages = new ArrayList<>();

    // === PHOTOS ===
    public List<PhotoEntry> photos = new ArrayList<>();

    // === NOTES ===
    public List<Note> notes = new ArrayList<>();

    // === MARKETPLACE ===
    public List<MarketListing> listings = new ArrayList<>();

    // === CONTACTS / KNOWN PLAYERS ===
    public List<String> contacts = new ArrayList<>();

    // === WORLD CLOCKS ===
    public List<String> worldClocks = new ArrayList<>();  // timezone names

    // === UNREAD BADGES ===
    public int unreadMessages = 0;

    // ===================== INNER CLASSES =====================

    public static class ChatConversation {
        public String id;           // UUID or group name
        public String name;
        public boolean isGroup;
        public boolean muted;
        public List<String> members = new ArrayList<>();
        public List<ChatMessage> messages = new ArrayList<>();
        public List<ChatMessage> pinnedMessages = new ArrayList<>();
        public List<String> polls = new ArrayList<>();

        public ChatConversation(String id, String name, boolean isGroup) {
            this.id = id;
            this.name = name;
            this.isGroup = isGroup;
        }

        /**
         * Cuenta mensajes no leídos en esta conversación, excluyendo los que
         * envió el propio jugador (esos no cuentan como "no leídos" para él).
         */
        public int getUnreadCount(String myName) {
            return (int) messages.stream()
                    .filter(m -> !m.read && !m.sender.equals(myName))
                    .count();
        }
    }

    public static class ChatMessage {
        public String id;
        public String sender;
        public String content;
        public long timestamp;
        public boolean read;
        public boolean deletedForAll;
        public boolean isPin;
        public List<String> seenBy = new ArrayList<>();  // ✓✓ Visto

        public ChatMessage(String sender, String content) {
            this.id = UUID.randomUUID().toString();
            this.sender = sender;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
            this.read = false;
            this.deletedForAll = false;
        }

        public String getFormattedTime() {
            long seconds = (System.currentTimeMillis() - timestamp) / 1000;
            if (seconds < 60) return "ahora";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h";
            return (hours / 24) + "d";
        }
    }

    public static class PhotoEntry {
        public String id;
        public String filename;
        public int worldX, worldY, worldZ;
        public String dimension;
        public String filter;       // "none","sepia","vivid","cool","warm"
        public boolean selfie;
        public long timestamp;
        public boolean sharedInChat;

        public PhotoEntry(String filename, int x, int y, int z, String dim) {
            this.id = UUID.randomUUID().toString();
            this.filename = filename;
            this.worldX = x;
            this.worldY = y;
            this.worldZ = z;
            this.dimension = dim;
            this.filter = "none";
            this.selfie = false;
            this.timestamp = System.currentTimeMillis();
        }

        public String getGeoTag() {
            return String.format("📍 %d, %d, %d (%s)", worldX, worldY, worldZ, dimension);
        }
    }

    public static class MarketListing {
        public String id;
        public String seller;
        public String itemName;
        public String itemId;       // minecraft item id
        public int quantity;
        public int price;           // price in diamonds
        public String description;
        public long timestamp;
        public boolean active;

        public MarketListing(String seller, String itemName, String itemId, int qty, int price, String desc) {
            this.id = UUID.randomUUID().toString();
            this.seller = seller;
            this.itemName = itemName;
            this.itemId = itemId;
            this.quantity = qty;
            this.price = price;
            this.description = desc;
            this.timestamp = System.currentTimeMillis();
            this.active = true;
        }
    }

    /**
     * Note — una nota de la app Notas.
     * `text` puede tener varias líneas (separadas por "\n"). Soporta un
     * formato mínimo estilo Markdown, interpretado al renderizar:
     *   - "**palabra**"  -> negrita
     *   - una línea que empieza con "- "  -> viñeta (se muestra como "• ")
     * `folder` es la carpeta/etiqueta de la nota ("" = sin carpeta).
     * `pinned` hace que la nota se muestre fija arriba de la lista.
     */
    public static class Note {
        public String id;
        public String text;
        public String folder;
        public boolean pinned;
        public long timestamp;   // creación
        public long updatedAt;   // última edición

        /** Constructor vacío requerido por (de)serialización basada en reflexión. */
        public Note() {
            this.id = UUID.randomUUID().toString();
            this.text = "";
            this.folder = "";
        }

        public Note(String text) {
            this.id = UUID.randomUUID().toString();
            this.text = text;
            this.folder = "";
            this.pinned = false;
            this.timestamp = System.currentTimeMillis();
            this.updatedAt = this.timestamp;
        }
    }
}
