package com.icraft.data;

import java.util.*;

public class PhoneData {

    public String[] appIconLabels = new String[9];
    public int[]    appIconColors = {0xFF4CAF50, 0xFF212121, 0xFFFF9800, 0xFF29B6F6,
                                     0xFF607D8B, 0xFF795548, 0xFF4FC3F7, 0xFFF06292, 0xFFE91E8C};

    public void resetIconDefaults() {
        appIconLabels = new String[9];
        appIconColors = new int[]{0xFF4CAF50, 0xFF212121, 0xFFFF9800, 0xFF29B6F6,
                                   0xFF607D8B, 0xFF795548, 0xFF4FC3F7, 0xFFF06292, 0xFFE91E8C};
    }

    public boolean darkMode = true;
    public String currentCase = "default";
    public String wallpaper = "wp_space";

    public String notificationSound = "";

    public String callSound = "";

    public String clickSound = "";
    public boolean doNotDisturb = false;
    public String theme = "blue";

    public String language = "";

    public List<ChatConversation> conversations = new ArrayList<>();
    public List<String> savedMessages = new ArrayList<>();

    public List<PhotoEntry> photos = new ArrayList<>();

    public List<Note> notes = new ArrayList<>();

    public List<MarketListing> listings = new ArrayList<>();

    public List<String> contacts = new ArrayList<>();

    public List<String> worldClocks = new ArrayList<>();

    public int unreadMessages = 0;

    public static class ChatConversation {
        public String id;
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
        public List<String> seenBy = new ArrayList<>();

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
        public String filter;
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
        public String itemId;
        public int quantity;
        public int price;
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

    public static class Note {
        public String id;
        public String text;
        public long timestamp;

        public Note() {
            this.id = UUID.randomUUID().toString();
            this.text = "";
        }

        public Note(String text) {
            this.id = UUID.randomUUID().toString();
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
