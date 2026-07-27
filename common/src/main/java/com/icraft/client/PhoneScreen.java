package com.icraft.client;

import com.icraft.ICraftConstants;
import com.icraft.data.PhoneData;
import com.icraft.network.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Properties;
import javax.imageio.ImageIO;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class PhoneScreen extends Screen {

    private static int getChatUnreadCount() {
        String myName = Minecraft.getInstance().getUser() != null
                ? Minecraft.getInstance().getUser().getName() : "";
        return phoneData.conversations.stream()
                .mapToInt(c -> c.getUnreadCount(myName))
                .sum();
    }

    private static int getTotalUnreadCount() {
        return getChatUnreadCount() + (timerUnread ? 1 : 0);
    }

    private static final ResourceLocation HUD_ICONS =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/item/phone_hud.png");
    private static final int ICON_SIZE    = 16;
    private static final int SHEET_W     = 144;
    private static final int SHEET_H     = 48;

    private static final ResourceLocation WEATHER_ICONS =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/gui/weather/weather_icons.png");
    private static final int WEATHER_ICON_SIZE = 16;
    private static final int WEATHER_SHEET_W   = 64;
    private static final int WEATHER_SHEET_H   = 48;
    private static final int WX_CLEAR = 0, WX_RAIN = 1, WX_STORM = 2;
    private static final int TOD_MORNING = 0, TOD_AFTERNOON = 1, TOD_NIGHT = 2, TOD_DAWN = 3;
    // El spritesheet weather_icons.png no sigue el orden TOD_MORNING..TOD_DAWN por columna:
    // columna 0 = amanecer, columna 1 = dia, columna 2 = noche, columna 3 = atardecer.
    // Esta tabla traduce cada TOD_* a la columna real del sheet para que el icono
    // coincida con el fondo.
    private static final int[] WEATHER_ICON_COL = { 1, 3, 2, 0 }; // indexado por TOD_MORNING..TOD_DAWN

    // Rango de ticks (level.getDayTime() % 24000) en el que arranca cada estado del cielo.
    // El estado activo es el que va desde su TOD_*_START hasta el START del siguiente (con wrap a 24000).
    private static final int TOD_DAWN_START      = 22500; // amanecer: 22500 -> 500
    private static final int TOD_MORNING_START   = 500;   // mediodia/dia: 500 -> 11700
    private static final int TOD_AFTERNOON_START = 11700; // atardecer: 11700 -> 14000
    private static final int TOD_NIGHT_START     = 14000; // noche: 14000 -> 22500

    private static final String[] WEATHER_BG_TOD  = {"day", "sunset", "night", "dawn"}; // indexado como TOD_MORNING..TOD_DAWN
    private static final String[] WEATHER_BG_WX   = {"clear", "rain", "storm"};          // indexado como WX_CLEAR..WX_STORM
    private static final int WEATHER_BG_TEX_W = 38;
    private static final int WEATHER_BG_TEX_H = 64;
    private static final ResourceLocation[][] WEATHER_BG_LOCS = new ResourceLocation[4][3];
    static {
        for (int tod = 0; tod < 4; tod++) {
            for (int wx = 0; wx < 3; wx++) {
                WEATHER_BG_LOCS[tod][wx] = ResourceLocation.fromNamespaceAndPath(
                        "icraft", "textures/gui/weather/backgrounds/weather_bg_" + WEATHER_BG_TOD[tod] + "_" + WEATHER_BG_WX[wx] + ".png");
            }
        }
    }

    private static final ResourceLocation WORLD_ICON =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/gui/world_icon.png");

    private static final ResourceLocation CAMERA_OVERLAY =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/gui/camera_overlay.png");
    private static final int CAM_TEX_W = 280;
    private static final int CAM_TEX_H = 160;
    private static final int WORLD_ICON_TEX_SIZE = 32;

    private static ResourceLocation worldIconLocation = null;
    private static DynamicTexture worldIconDynamicTexture = null;
    private static int worldIconW = 64, worldIconH = 64;

    private static final int BASE_PHONE_W     = 160;
    private static final int BASE_PHONE_H     = 320;
    private static final int BASE_SCREEN_X_OFF = 10;
    private static final int BASE_SCREEN_Y_OFF = 35;
    private static final int BASE_SCREEN_W    = 140;
    private static final int BASE_SCREEN_H    = 240;
    private static final int BASE_STATUS_H    = 16;
    private static final int BASE_NAV_H       = 24;

    private static final int BASE_CAM_W       = 280;
    private static final int BASE_CAM_H       = 160;
    private static final int CAM_RIGHT_BEZEL_W = 26;

    private int phoneX, phoneY;
    private int PHONE_W, PHONE_H;
    private int SCREEN_X_OFF, SCREEN_Y_OFF;
    private int SCREEN_W, SCREEN_H;
    private int STATUS_H, NAV_H, APP_H;

    private float uiScale = 1f;

    public enum App { HOME, CHAT, CHAT_CONV, CAMERA, PHOTOS, WEATHER, CLOCK, NOTES, NOTE_EDIT, MAPA, SETTINGS, ICON_EDITOR, CONTACTS, PRIVACY, CREATE_GROUP, SOUND, WALLPAPER, THEME }
    private App currentApp = App.HOME;

    private final Deque<App> appHistory = new ArrayDeque<>();

    private void goToApp(App app) {

        if (currentApp != app) {
            appHistory.push(currentApp);
        }
        currentApp = app;
        initCurrentApp();
    }

    private void playClickSound() {
        String clickSound = phoneData.clickSound;
        if (clickSound != null && !clickSound.isEmpty()) {
            ClickSounds.play(clickSound);
        }
    }

    private boolean goBack() {
        if (isCallLocked() && currentApp == App.CONTACTS) return true;
        if (appHistory.isEmpty()) return false;
        currentApp = appHistory.pop();
        initCurrentApp();
        return true;
    }

    private static PhoneData phoneData = new PhoneData();

    public static PhoneData getPhoneData() {
        return phoneData;
    }
    private static List<String> onlinePlayers = new ArrayList<>();
    private static List<String> knownContacts = new ArrayList<>();

    private static volatile String activeCallPeer = null;

    private enum CallState { NONE, RINGING_OUT, INCOMING, CONNECTED }
    private static volatile CallState callState = CallState.NONE;

    private static volatile String callPeerPending = null;

    private static volatile boolean micMuted = false;

    private static volatile boolean peerMuted = false;

    private static int ringTickCounter = 0;

    private static boolean isCallLocked() {
        return callState != CallState.NONE;
    }

    private PhoneData.ChatConversation currentConv = null;
    private EditBox chatInput;
    private int chatListScrollOffset = 0;
    private int chatConvScrollOffset = 0;
    private int contactsScrollOffset = 0;

    private String contactOptionsFor = null;
    private int privacyScrollOffset = 0;
    private int wallpaperScrollOffset = 0;
    private int themeScrollOffset = 0;
    private int iconColorScrollOffset = 0;

    // Pronostico por franja horaria (mañana/tarde/noche/amanecer) para la app Weather.
    // Se recalcula una vez por dia de Minecraft, con lluvia/tormenta poco frecuentes.
    private final int[] weatherForecastConditions = new int[4];
    private long weatherForecastDay = Long.MIN_VALUE;

    private EditBox groupNameInput = null;

    private final Set<String> groupSelectedMembers = new LinkedHashSet<>();

    private static final Map<String, String> pendingGroupInvites = new java.util.LinkedHashMap<>();

    private static volatile boolean hasNewGroupInvite = false;
    private boolean chatAtBottom = true;

    private int settingsContentBottom = 0;

    private boolean selfieMode = false;
    private String selectedFilter = "none";
    private static final String[] FILTERS = {"none","sepia","vivid","cool","warm","noir","retro","fade","creeper","enderman","skeleton","blaze","bat"};
    private int filterIndex = 0;
    private int cameraPerspective = 0;
    private static final int CAM_FOV_MIN = 2;
    private static final int CAM_FOV_MAX = 50;
    private static final int CAM_FOV_TELE_THRESHOLD = 15;
    private int cameraFov = CAM_FOV_MAX;
    private static volatile int pendingCaptureFov  = CAM_FOV_MAX;
    private static volatile boolean captureFovActive = false;
    private boolean cameraLayout = false;

    private boolean closedNotified = false;
    private boolean cameraMouseCaptured = false;
    private double camPrevCursorX = 0, camPrevCursorY = 0;

    private long cameraEnteredAt = 0;

    private static App pendingOpenApp = null;
    private static App reopenAfterPhotoApp = null;

    private int clockTab = 0;

    private static final int CLOCK_TAB0_CONTENT_H = 96;
    private static final int CLOCK_TAB1_CONTENT_H = 50;
    private static final int CLOCK_TAB2_CONTENT_H_IDLE    = 68;
    private static final int CLOCK_TAB2_CONTENT_H_RUNNING = 50;

    private static boolean stopwatchRunning = false;
    private static long stopwatchStartedAt = 0;
    private static long stopwatchAccumulatedMs = 0;

    private static boolean timerRunning = false;
    private static boolean timerFinished = false;
    private static long timerEndAt = 0;
    private static long timerRemainingMs = 5 * 60 * 1000L;
    private static long timerDurationMs  = 5 * 60 * 1000L;
    private static final long TIMER_MIN_MS = 10_000L;
    private static final long TIMER_MAX_MS = 99 * 60_000L + 59_000L;
    private static boolean timerNeedsUiRefresh = false;

    private static boolean timerUnread = false;
    private static String  timerUnreadPreview = "";
    private static long    timerUnreadTimestamp = 0;

    static {
        // Paso 6: antes se registraba directo contra net.neoforged.neoforge.common.NeoForge.EVENT_BUS
        // (específico de NeoForge). ClientTickEvent de Architectury es cross-loader (funciona igual
        // en NeoForge y Fabric), así que este registro ya no necesita pasar por ICraftMod/paso 4.
        ClientTickEvent.CLIENT_POST.register(client -> checkTimerCompletion());
    }

    private static void checkTimerCompletion() {
        if (timerRunning && System.currentTimeMillis() >= timerEndAt) {
            timerRunning = false;
            timerFinished = true;
            timerRemainingMs = 0;
            timerNeedsUiRefresh = true;

            String doneMsg = PhoneLang.get("icraft.phone.clock.timer_done");

            if (!phoneData.notificationSound.isEmpty()) {
                NotificationSounds.play(phoneData.notificationSound);
            } else if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
            }

            PhoneToast.pushTimer(doneMsg);

            timerUnread = true;
            timerUnreadPreview = doneMsg;
            timerUnreadTimestamp = System.currentTimeMillis();
        }
    }

    private EditBox noteInput;
    private int selectedNote = -1;

    private String noteDraftBuffer = "";

    private boolean noteViewMode = false;

    private String pendingNoteInputValue = null;

    private int noteEditScrollOffset = 0;

    private int notePendingDeleteIndex = -1;

    private static final String[] THEMES = {
        "white","light_gray","gray","black","brown","red","orange","yellow","lime","green",
        "cyan","light_blue","blue","purple","magenta","pink",
        "gold","diamond","emerald","netherite"
    };
    private static final int[] THEME_COLORS = {
        0xFFF9FFFE, 0xFF9D9D97, 0xFF474F52, 0xFF1D1D21, 0xFF835432, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D, 0xFF80C71F, 0xFF5E7C16,
        0xFF169C9C, 0xFF3AB3DA, 0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA,
        0xFFFFD700, 0xFF4AEDD9, 0xFF17DD62, 0xFF443A3B
    };
    private static final String[] CASES = {"default","black","white","neon","diamond"};

    private static String[] sounds() {
        java.util.List<String> available = com.icraft.client.NotificationSounds.getAvailable();
        return available.toArray(new String[0]);
    }

    private static String[] callSoundsArr() {
        java.util.List<String> available = com.icraft.client.CallSounds.getAvailable();
        return available.toArray(new String[0]);
    }

    private static String[] clickSoundsArr() {
        java.util.List<String> available = com.icraft.client.ClickSounds.getAvailable();
        return available.toArray(new String[0]);
    }

    private static final int WP_TEX_W = 38;
    private static final int WP_TEX_H = 64;
    private static final String[] WALLPAPER_IDS = {
        "wp_space", "wp_sunset", "wp_forest", "wp_ocean",
        "wp_neoncity", "wp_minecraft", "wp_galaxy", "wp_retro"
    };

    private static String displaySenderName(String rawSender) {
        return com.icraft.server.PhoneServerHandler.SYSTEM_SENDER.equals(rawSender)
                ? "§a" + I18n.get("icraft.chat.system_sender")
                : rawSender;
    }

    private static String displayMessageContent(String raw) {
        if (raw == null) return "";
        if (raw.startsWith("§§JOIN:")) {
            return "§e" + I18n.get("icraft.chat.system_join_msg", raw.substring("§§JOIN:".length()));
        }
        if (raw.startsWith("§§LEAVE:")) {
            return "§e" + I18n.get("icraft.chat.system_leave_msg", raw.substring("§§LEAVE:".length()));
        }
        return raw;
    }

    private static final String[] DEFAULT_LABEL_KEYS = {
        "icraft.phone.apps.chat_label", "icraft.phone.apps.camera_label",
        "icraft.phone.apps.photos_label", "icraft.phone.apps.weather_label",
        "icraft.phone.apps.clock_label", "icraft.phone.apps.notes_label",
        "icraft.phone.apps.market_label", "icraft.phone.apps.settings_label",
        "icraft.phone.apps.contacts_label"
    };

    private String effectiveAppLabel(int i) {
        String custom = phoneData.appIconLabels[i];
        return (custom != null && !custom.isBlank()) ? custom : PhoneLang.get(DEFAULT_LABEL_KEYS[i]);
    }

    private String[] effectiveAppLabels() {
        String[] out = new String[phoneData.appIconLabels.length];
        for (int i = 0; i < out.length; i++) out[i] = effectiveAppLabel(i);
        return out;
    }

    private static String[] wallpaperNames() {
        return new String[] {
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_space"),
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_sunset"),
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_forest"),
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_ocean"),
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_neoncity"),
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_minecraft"),
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_galaxy"),
            PhoneLang.get("icraft.phone.settings.wallpaper.wp_retro")
        };
    }
    private static final ResourceLocation[] WALLPAPER_LOCS = new ResourceLocation[WALLPAPER_IDS.length];
    static {
        for (int i = 0; i < WALLPAPER_IDS.length; i++) {
            WALLPAPER_LOCS[i] = ResourceLocation.fromNamespaceAndPath(
                "icraft", "textures/gui/wallpapers/" + WALLPAPER_IDS[i] + ".png");
        }
    }

    private static final int CASE_TEX_W = 160;
    private static final int CASE_TEX_H = 320;
    private static final ResourceLocation[] CASE_LOCS;
    static {
        CASE_LOCS = new ResourceLocation[CASES.length];
        for (int i = 0; i < CASES.length; i++) {
            CASE_LOCS[i] = ResourceLocation.fromNamespaceAndPath(
                    "icraft", "textures/gui/cases/frame_" + CASES[i] + ".png");
        }
    }

    private static List<String> notifications = new ArrayList<>();

    private static final java.util.Set<String> receivedMessageIds = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>() {
        @Override public boolean add(String s) {
            if (size() >= 2000) { iterator().remove(); }
            return super.add(s);
        }
    });

    private static final java.util.Set<String> persistedReadIds = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>() {
        @Override public boolean add(String s) {
            if (size() >= 2000) { iterator().remove(); }
            return super.add(s);
        }
    });

    private static boolean readIdsLoaded = false;
    private long lastRequestTime = 0;

    private static volatile String activeChatConvId = null;

    private static volatile boolean newMessageForActiveConv = false;

    private boolean photoShareMenuOpen = false;

    private int photoShareIndex = -1;

    private final Map<String, ResourceLocation> photoTextureCache = new LinkedHashMap<>();
    private final Map<String, DynamicTexture>   photoTextures     = new LinkedHashMap<>();
    private final Map<String, int[]> photoDimsCache = new LinkedHashMap<>();

    private int photoViewerIndex = -1;
    private int photosScrollOffset = 0;
    private boolean photoDeleteConfirmOpen = false;

    private boolean locked = true;

    public PhoneScreen() {
        super(Component.literal("iCraft"));
        loadSettings();
        syncCallRingtoneToServer();
        ensureReadIdsLoaded();
        phoneData.darkMode = true;
        applyDefaultAppColorsOnce();
        syncPhotosFromDisk();
        NetworkManager.sendToServer(new com.icraft.network.PhoneOpenStatePacket(true));
    }

    private void syncPhotosFromDisk() {
        try {
            Path photosDir = getPhotosDir();
            if (!Files.exists(photosDir)) return;

            Set<String> known = new HashSet<>();
            for (PhoneData.PhotoEntry p : phoneData.photos) {
                known.add(p.filename);
            }

            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(photosDir)) {
                stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .forEach(files::add);
            }
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));

            String currentDim = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.dimension().location().toString()
                    : "minecraft:overworld";

            for (Path file : files) {
                String filename = file.getFileName().toString();
                if (known.contains(filename)) continue;

                PhoneData.PhotoEntry photo = new PhoneData.PhotoEntry(filename, 0, 0, 0, currentDim);
                photo.filter = "none";
                photo.selfie = false;
                loadPhotoMeta(photo, photosDir);
                phoneData.photos.add(photo);
                known.add(filename);
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("Could not sync photos from disk: {}", e.getMessage());
        }
    }

    private static void savePhotoMeta(PhoneData.PhotoEntry photo) {
        try {
            Path metaFile = getPhotosDirStatic().resolve(photo.filename.replace(".png", ".meta"));
            Properties p = new Properties();
            p.setProperty("worldX",    String.valueOf(photo.worldX));
            p.setProperty("worldY",    String.valueOf(photo.worldY));
            p.setProperty("worldZ",    String.valueOf(photo.worldZ));
            p.setProperty("dimension", photo.dimension != null ? photo.dimension : "");
            p.setProperty("filter",    photo.filter != null ? photo.filter : "none");
            p.setProperty("selfie",    String.valueOf(photo.selfie));
            try (FileOutputStream fos = new FileOutputStream(metaFile.toFile())) {
                p.store(fos, "iCraft photo metadata");
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("Could not save photo meta for {}: {}", photo.filename, e.getMessage());
        }
    }

    private void loadPhotoMeta(PhoneData.PhotoEntry photo, Path photosDir) {
        try {
            Path metaFile = photosDir.resolve(photo.filename.replace(".png", ".meta"));
            if (!Files.exists(metaFile)) return;
            Properties p = new Properties();
            try (FileInputStream fis = new FileInputStream(metaFile.toFile())) {
                p.load(fis);
            }
            photo.worldX    = Integer.parseInt(p.getProperty("worldX", "0"));
            photo.worldY    = Integer.parseInt(p.getProperty("worldY", "0"));
            photo.worldZ    = Integer.parseInt(p.getProperty("worldZ", "0"));
            photo.dimension = p.getProperty("dimension", "");
            photo.filter    = p.getProperty("filter", "none");
            photo.selfie    = Boolean.parseBoolean(p.getProperty("selfie", "false"));
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("Could not load photo meta for {}: {}", photo.filename, e.getMessage());
        }
    }

    @Override
    protected void init() {
        super.init();

        recalcLayout();

        for (PhoneData.ChatConversation conv : phoneData.conversations) {
            for (PhoneData.ChatMessage msg : conv.messages) {
                if (msg.id != null && !msg.id.isEmpty()) {
                    receivedMessageIds.add(msg.id);
                }
            }
        }

        PhoneToast.markScreenOpened();
        NetworkManager.sendToServer(new RequestDataPacket("all"));

        if (pendingOpenApp != null) {
            currentApp = pendingOpenApp;
            pendingOpenApp = null;
        }

        if (isCallLocked()) {
            currentApp = App.CONTACTS;
            contactOptionsFor = null;
            locked = false;
        }

        initCurrentApp();
    }

    private void recalcLayout() {
        int margin = 10;

        if (cameraLayout) {
            PHONE_W      = width  - margin * 2;
            PHONE_H      = height - margin * 2;
            SCREEN_X_OFF = 0;
            SCREEN_Y_OFF = 0;
            STATUS_H     = 0;
            NAV_H        = 0;
            SCREEN_W     = PHONE_W;
            SCREEN_H     = PHONE_H;
            APP_H        = SCREEN_H;
            uiScale      = PHONE_W / (float) BASE_PHONE_W;
        } else {
            float scaleH = (height - margin * 2) / (float) BASE_PHONE_H;
            float scaleW = (width  - margin * 2) / (float) BASE_PHONE_W;
            float scale  = Math.min(scaleH, scaleW);
            scale = Math.max(scale, 0.75f);
            scale = Math.min(scale, 2.5f);

            PHONE_W      = Math.round(BASE_PHONE_W      * scale);
            PHONE_H      = Math.round(BASE_PHONE_H      * scale);

            double leftFrac   = 2.0  / 32.0;
            double rightFrac  = 30.0 / 32.0;
            double topFrac    = 7.0  / 64.0;
            double bottomFrac = 62.0 / 64.0;

            int leftEdge   = (int) Math.floor(leftFrac   * PHONE_W);
            int rightEdge  = (int) Math.ceil (rightFrac  * PHONE_W);
            int topEdge    = (int) Math.floor(topFrac    * PHONE_H);
            int bottomEdge = (int) Math.ceil (bottomFrac * PHONE_H);

            SCREEN_X_OFF = leftEdge;
            SCREEN_Y_OFF = topEdge;
            SCREEN_W     = rightEdge - leftEdge;
            SCREEN_H     = bottomEdge - topEdge;

            STATUS_H     = Math.round(BASE_STATUS_H     * scale);
            NAV_H        = Math.round(BASE_NAV_H        * scale);
            APP_H        = SCREEN_H - STATUS_H;
            uiScale      = scale;
            uiScale      = scale;
        }

        phoneX = (width  - PHONE_W) / 2;
        phoneY = (height - PHONE_H) / 2;
    }

    private int sx() { return phoneX + SCREEN_X_OFF; }
    private int sy() { return phoneY + SCREEN_Y_OFF + STATUS_H; }
    private int appY() { return sy(); }
    private int appBottom() { return sy() + APP_H; }

    private int[] cameraViewportRect() {
        int left   = phoneX;
        int top    = phoneY;
        int right  = phoneX + PHONE_W - CAM_RIGHT_BEZEL_W;
        int bottom = phoneY + PHONE_H;
        return new int[]{left, top, right, bottom};
    }

    private void initCurrentApp() {
        clearWidgets();
        if (currentApp != App.CHAT_CONV) activeChatConvId = null;
        if (currentApp != App.CONTACTS) contactOptionsFor = null;

        boolean wantsCameraLayout = (currentApp == App.CAMERA);
        if (wantsCameraLayout != cameraLayout) {
            cameraLayout = wantsCameraLayout;
            recalcLayout();
            if (cameraLayout) {
                cameraEnteredAt = System.currentTimeMillis();
                captureCameraMouse();
            } else {
                Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                Minecraft.getInstance().mouseHandler.releaseMouse();
                releaseCameraMouse();
            }
        }

        switch (currentApp) {
            case HOME       -> {}
            case CHAT       -> initChat();
            case CHAT_CONV  -> initChatConversation();
            case CAMERA     -> initCamera();
            case PHOTOS     -> {}
            case WEATHER    -> {}
            case CLOCK      -> initClock();
            case NOTES      -> initNotes();
            case NOTE_EDIT  -> initNoteEdit();
            case MAPA       -> initMapa();
            case SETTINGS   -> initSettings();
            case ICON_EDITOR -> initIconEditor();
            case CONTACTS   -> {}
            case PRIVACY    -> initPrivacy();
            case CREATE_GROUP -> initCreateGroup();
            case SOUND      -> initSound();
            case WALLPAPER  -> initWallpaperPicker();
            case THEME      -> initTheme();
        }
    }

    private void initChat() {
        int btnW = 42;
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.chats.new_group_btn")), b -> {
            groupSelectedMembers.clear();
            goToApp(App.CREATE_GROUP);
        }).pos(sx() + SCREEN_W - btnW - 2, appY() + 1).size(btnW, 11).build());
    }

    private void initCreateGroup() {
        clearWidgets();
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.CHAT);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        groupNameInput = new EditBox(font, sx() + 5, appY() + 18, SCREEN_W - 10, 12,
                Component.literal(PhoneLang.get("icraft.phone.chats.group_name_hint")));
        groupNameInput.setMaxLength(32);
        addRenderableWidget(groupNameInput);

        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.chats.create_group_btn")), b -> {
            String gName = groupNameInput != null ? groupNameInput.getValue().trim() : "";
            if (gName.isEmpty() || groupSelectedMembers.isEmpty()) {
                notifications.add(PhoneLang.get("icraft.phone.chats.group_missing_fields"));
                return;
            }
            createPrivateGroup(gName, new ArrayList<>(groupSelectedMembers));
        }).pos(sx() + 5, appBottom() - NAV_H - 14).size(SCREEN_W - 10, 12).build());
    }

    private void createPrivateGroup(String groupName, List<String> members) {
        String myName = Minecraft.getInstance().getUser().getName();
        String groupId = "grp_" + myName + "_" + System.currentTimeMillis();

        PhoneData.ChatConversation grpConv = new PhoneData.ChatConversation(groupId, groupName, true);
        grpConv.members.addAll(members);
        if (!grpConv.members.contains(myName)) grpConv.members.add(myName);
        phoneData.conversations.add(0, grpConv);

        for (String member : members) {
            NetworkManager.sendToServer(new SendChatPacket(
                    "GROUP_INVITE:" + groupId + ":" + groupName, member,
                    "__group_invite__",
                    false, true, java.util.UUID.randomUUID().toString()));
        }

        notifications.add(PhoneLang.get("icraft.phone.chats.group_created", groupName));
        currentConv = grpConv;
        chatConvScrollOffset = Integer.MAX_VALUE;
        chatAtBottom = true;
        goToApp(App.CHAT_CONV);
    }

    public static void receiveGroupInvite(String groupId, String groupName) {
        pendingGroupInvites.put(groupId, groupName);
        hasNewGroupInvite = true;
        notifications.add(PhoneLang.get("icraft.phone.chats.group_invite_received", groupName));
    }

    private void sendChatMessage(String content) {
        if (currentConv == null || content == null || content.isEmpty()) return;
        String myName = Minecraft.getInstance().getUser().getName();
        PhoneData.ChatMessage m = new PhoneData.ChatMessage(myName, content);
        m.read = true;
        currentConv.messages.add(m);
        receivedMessageIds.add(m.id);
        persistedReadIds.add(m.id);
        NetworkManager.sendToServer(new SendChatPacket(
                currentConv.id, currentConv.isGroup ? "" : currentConv.name,
                content, currentConv.isGroup, false, m.id));
        chatConvScrollOffset = Integer.MAX_VALUE;
    }

    private void initChatConversation() {
        activeChatConvId = currentConv != null ? currentConv.id : null;

        if (currentConv != null) {
            for (PhoneData.ChatMessage m : currentConv.messages) {
                m.read = true;
                if (m.id != null && !m.id.isEmpty()) {
                    persistedReadIds.add(m.id);
                }
            }
            savePersistedReadIds();
        }

        int sendBtnW = 24;
        int locBtnW  = 16;
        int locX     = sx() + 2;
        int inputX   = locX + locBtnW + 2;
        int inputW   = SCREEN_W - sendBtnW - locBtnW - 10;
        int inputY   = appBottom() - NAV_H - 14;

        chatInput = new EditBox(font, inputX, inputY, inputW, 11,
                Component.literal(PhoneLang.get("icraft.phone.chat.message_hint")));
        chatInput.setMaxLength(200);
        addRenderableWidget(chatInput);

        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("📍"), b -> {
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            net.minecraft.core.BlockPos pos = player.blockPosition();
            String desc = chatInput != null ? chatInput.getValue().trim() : "";
            String content = "📍 X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ();
            if (!desc.isEmpty()) content += "\n" + desc;
            sendChatMessage(content);
            if (chatInput != null) chatInput.setValue("");
        }).pos(locX, inputY).size(locBtnW, 11).build());

        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(">>"), b -> {
            if (chatInput != null && !chatInput.getValue().trim().isEmpty()) {
                sendChatMessage(chatInput.getValue().trim());
                chatInput.setValue("");
            }
        }).pos(inputX + inputW + 2, inputY).size(sendBtnW, 11).build());

        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.CHAT);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        addRenderableWidget(PhoneButton.phoneBuilder(
                Component.literal(currentConv != null && currentConv.muted ? "[M]" : "[S]"),
                b -> { if (currentConv != null) currentConv.muted = !currentConv.muted; initCurrentApp(); }
        ).pos(sx() + SCREEN_W - 26, appY() + 2).size(24, 10).build());
    }

    private void initCamera() {
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        cameraPerspective = 0;
        notifications.add(PhoneLang.get("icraft.phone.camera.mode_hint"));
    }

    public float getCameraFov() {
        return cameraFov;
    }

    public static float getCaptureFov() {
        return pendingCaptureFov;
    }

    public static boolean isCaptureFovActive() {
        return captureFovActive;
    }

    private void initNotes() {
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.notes.new_btn")), b -> {
            selectedNote = -1;
            noteDraftBuffer = "";
            noteViewMode = false;
            pendingNoteInputValue = "";
            noteEditScrollOffset = 0;
            goToApp(App.NOTE_EDIT);
        }).pos(sx() + 4, appBottom() - NAV_H - 15).size(SCREEN_W - 8, 12).build());
    }

    private void initNoteEdit() {
        ICraftConstants.LOGGER.info("[iCraft-DEBUG] initNoteEdit() llamado. noteViewMode={} selectedNote={}", noteViewMode, selectedNote);
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.NOTES);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        if (noteViewMode) {
            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.notes.edit_btn")),
                    b -> enterNoteEditMode()
            ).pos(sx() + SCREEN_W - 44, appY() + 1).size(42, 11).build());
            return;
        }

        int inputY = noteInputY();
        int saveY  = noteInputMaxY() + 12;

        String preserveValue;
        int    preserveCursor;
        if (pendingNoteInputValue != null) {
            preserveValue  = pendingNoteInputValue;
            preserveCursor = preserveValue.length();
            pendingNoteInputValue = null;
        } else {
            preserveValue  = noteInput != null ? noteInput.getValue() : "";
            preserveCursor = noteInput != null ? noteInput.getCursorPosition() : 0;
        }

        int noteMaxW = SCREEN_W - 6;
        noteInput = new EditBox(font, sx() + 3, inputY, noteMaxW, 10,
                Component.literal(PhoneLang.get("icraft.phone.notes.write_line_hint")));
        noteInput.setMaxLength(1000);
        noteInput.setBordered(false);
        noteInput.setTextColor(phoneData.darkMode ? 0xFFFFFFFF : 0xFF333333);

        noteInput.setResponder(this::onNoteInputChanged);
        noteInput.setValue(preserveValue);
        noteInput.setCursorPosition(preserveCursor);
        addRenderableWidget(noteInput);
        setInitialFocus(noteInput);
        noteInput.setFocused(true);

        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.notes.save_btn")),
                b -> { ICraftConstants.LOGGER.info("[iCraft-DEBUG] Save button pressed"); saveCurrentNote(); }
        ).pos(sx() + 2, saveY).size(SCREEN_W - 4, 11).build());
        ICraftConstants.LOGGER.info("[iCraft-DEBUG] noteInput creado en x={} y={} w={} h=10", sx() + 3, inputY, noteMaxW);
    }

    private void onNoteInputChanged(String value) {
        int maxW = SCREEN_W - 6;
        if (font.width(value) <= maxW) return;

        StringBuilder committed = new StringBuilder(noteDraftBuffer);
        String remaining = value;
        while (font.width(remaining) > maxW) {
            int cut = noteWrapSplitIndex(remaining, maxW);
            String line = remaining.substring(0, cut);
            String rest = remaining.substring(cut);
            if (rest.startsWith(" ")) rest = rest.substring(1);
            if (committed.length() > 0) committed.append("\n");
            committed.append(line);
            remaining = rest;
        }

        noteDraftBuffer = committed.toString();
        noteEditScrollOffset = Integer.MAX_VALUE;

        if (noteInput != null) {
            int newY = noteInputY();
            if (noteInput.getY() != newY) {
                recreateNoteInputAt(newY, remaining);
            } else {
                noteInput.setValue(remaining);
                noteInput.setCursorPosition(remaining.length());
                noteInput.setFocused(true);
            }
        } else {
            pendingNoteInputValue = remaining;
        }
    }

    private void recreateNoteInputAt(int y, String value) {
        removeWidget(noteInput);
        int noteMaxW = SCREEN_W - 6;
        noteInput = new EditBox(font, sx() + 3, y, noteMaxW, 10,
                Component.literal(PhoneLang.get("icraft.phone.notes.write_line_hint")));
        noteInput.setMaxLength(1000);
        noteInput.setBordered(false);
        noteInput.setTextColor(phoneData.darkMode ? 0xFFFFFFFF : 0xFF333333);
        noteInput.setResponder(this::onNoteInputChanged);
        noteInput.setValue(value);
        noteInput.setCursorPosition(value.length());
        addRenderableWidget(noteInput);
        setInitialFocus(noteInput);
        noteInput.setFocused(true);
    }

    private static final String[] NOTE_TOOLBAR_CODES = {
        "\u00A7r", "\u00A7c", "\u00A76", "\u00A7e", "\u00A7a", "\u00A7b", "\u00A79", "\u00A7d", "\u00A7l"
    };
    private static final int[] NOTE_TOOLBAR_SWATCH_COLORS = {
        0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFFF55FF
    };
    private int noteToolbarX, noteToolbarY, noteToolbarIconSize, noteToolbarGap;

    private void renderNoteFormatToolbar(GuiGraphics g, int mx, int my) {
        int count = NOTE_TOOLBAR_CODES.length;

        int iconSize = 10;
        int gap = 4;

        int marginX = 4;
        int available = SCREEN_W - marginX * 2;

        int totalW = count * iconSize + (count - 1) * gap;
        if (totalW > available) {

            iconSize = Math.max(6, (available - (count - 1) * gap) / count);
            totalW = count * iconSize + (count - 1) * gap;
            if (totalW > available) {
                gap = 2;
                iconSize = Math.max(6, (available - (count - 1) * gap) / count);
                totalW = count * iconSize + (count - 1) * gap;
            }
        }

        int startX = sx() + Math.max(marginX, (SCREEN_W - totalW) / 2);
        int y = appY() + 16;

        noteToolbarX = startX;
        noteToolbarY = y;
        noteToolbarIconSize = iconSize;
        noteToolbarGap = gap;

        for (int i = 0; i < count; i++) {
            int bx = startX + i * (iconSize + gap);
            boolean hover = mx >= bx && mx < bx + iconSize && my >= y && my < y + iconSize;
            g.fill(bx - 1, y - 1, bx + iconSize + 1, y + iconSize + 1, hover ? 0xFFFFFFFF : 0xFF444444);

            int textY = y + Math.max(0, (iconSize - 8) / 2);
            if (i == 0) {

                g.fill(bx, y, bx + iconSize, y + iconSize, phoneData.darkMode ? 0xFF2A2A3E : 0xFFEEEEEE);
                g.drawCenteredString(font, "x", bx + iconSize / 2, textY, 0xFFFF5555);
            } else if (i == count - 1) {

                g.fill(bx, y, bx + iconSize, y + iconSize, 0xFF222222);
                g.drawCenteredString(font, "\u00A7lB", bx + iconSize / 2, textY, 0xFFFFFFFF);
            } else {
                g.fill(bx, y, bx + iconSize, y + iconSize, NOTE_TOOLBAR_SWATCH_COLORS[i - 1]);
            }
        }
    }

    private boolean handleNoteFormatToolbarClick(double mx, double my) {
        if (noteViewMode || noteInput == null) return false;
        int iconSize = noteToolbarIconSize;
        int gap = noteToolbarGap;
        int count = NOTE_TOOLBAR_CODES.length;
        if (my < noteToolbarY - 1 || my >= noteToolbarY + iconSize + 1) return false;
        for (int i = 0; i < count; i++) {
            int bx = noteToolbarX + i * (iconSize + gap);
            if (mx >= bx - 1 && mx < bx + iconSize + 1) {
                playClickSound();
                insertNoteFormatCode(NOTE_TOOLBAR_CODES[i]);
                return true;
            }
        }
        return false;
    }

    private void insertNoteFormatCode(String code) {
        if (noteInput == null) return;
        int cursor = noteInput.getCursorPosition();
        String value = noteInput.getValue();
        if (cursor < 0) cursor = 0;
        if (cursor > value.length()) cursor = value.length();
        String newValue = value.substring(0, cursor) + code + value.substring(cursor);
        noteInput.setValue(newValue);
        noteInput.setCursorPosition(cursor + code.length());
        noteInput.setFocused(true);
    }

    private int noteWrapSplitIndex(String text, int maxW) {
        int idx = text.length();
        while (idx > 0 && font.width(text.substring(0, idx)) > maxW) idx--;
        if (idx <= 0) return 1;
        int lastSpace = text.lastIndexOf(' ', idx);
        return lastSpace > 0 ? lastSpace : idx;
    }

    private void enterNoteEditMode() {
        noteViewMode = false;
        if (selectedNote >= 0 && selectedNote < phoneData.notes.size()) {
            PhoneData.Note n = phoneData.notes.get(selectedNote);
            String[] lines = n.text.split("\n", -1);
            if (lines.length > 1) {
                noteDraftBuffer = String.join("\n", Arrays.copyOf(lines, lines.length - 1));
                pendingNoteInputValue = lines[lines.length - 1];
            } else {
                noteDraftBuffer = "";
                pendingNoteInputValue = n.text;
            }
        } else {
            noteDraftBuffer = "";
            pendingNoteInputValue = "";
        }
        noteEditScrollOffset = Integer.MAX_VALUE;
        initCurrentApp();
    }

    private void saveCurrentNote() {
        String current = noteInput != null ? noteInput.getValue() : "";
        StringBuilder full = new StringBuilder(noteDraftBuffer);
        if (!current.trim().isEmpty()) {
            if (full.length() > 0) full.append("\n");
            full.append(current);
        }
        String finalText = full.toString().trim();
        if (finalText.isEmpty()) return;

        if (selectedNote >= 0 && selectedNote < phoneData.notes.size()) {
            PhoneData.Note n = phoneData.notes.get(selectedNote);
            n.text = finalText;
            n.timestamp = System.currentTimeMillis();
        } else {
            phoneData.notes.add(new PhoneData.Note(finalText));
        }
        selectedNote = -1;
        noteDraftBuffer = "";
        noteViewMode = false;
        if (noteInput != null) noteInput.setValue("");
        if (!goBack()) goToApp(App.NOTES);
    }

    private List<Integer> getSortedNoteIndices() {
        List<PhoneData.Note> all = phoneData.notes;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) indices.add(i);
        indices.sort((a, b) -> Long.compare(all.get(b).timestamp, all.get(a).timestamp));
        return indices;
    }

    private List<String> wrapNoteText(String text, int maxW) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        String carry = "";
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            StringBuilder line = new StringBuilder(carry);
            boolean lineHasWords = false;
            for (String word : paragraph.split(" ")) {
                String candidate = lineHasWords ? line + " " + word : line + word;
                if (font.width(candidate) > maxW && lineHasWords) {
                    result.add(line.toString());
                    carry = activeNoteFormatAtEnd(line.toString());
                    line = new StringBuilder(carry).append(word);
                    lineHasWords = true;
                } else {
                    line = new StringBuilder(candidate);
                    lineHasWords = true;
                }
            }
            result.add(line.toString());
            carry = activeNoteFormatAtEnd(line.toString());
        }
        return result;
    }

    private String activeNoteFormatAtEnd(String line) {
        String color = "";
        boolean bold = false;
        for (int i = 0; i < line.length() - 1; i++) {
            if (line.charAt(i) == '\u00A7') {
                char c = Character.toLowerCase(line.charAt(i + 1));
                if (c == 'r') { color = ""; bold = false; }
                else if (c == 'l') { bold = true; }
                else if ("0123456789abcdef".indexOf(c) >= 0) { color = "\u00A7" + c; bold = false; }
            }
        }
        return color + (bold ? "\u00A7l" : "");
    }

    private static final int BASE_SETTINGS_ROW_H       = 18;
    private static final int BASE_SETTINGS_ROW_GAP     = 24;
    private static final int BASE_SETTINGS_HEADER_GAP  = 26;
    private static final int BASE_SETTINGS_SIDE_MARGIN = 5;
    private static final int BASE_SOUND_HEADER_GAP      = 48;

    private int settingsRowH()      { return Math.round(BASE_SETTINGS_ROW_H       * uiScale); }
    private int settingsRowGap()    { return Math.round(BASE_SETTINGS_ROW_GAP     * uiScale); }
    private int settingsHeaderGap() { return Math.round(BASE_SETTINGS_HEADER_GAP  * uiScale); }
    private int settingsSideMargin(){ return Math.round(BASE_SETTINGS_SIDE_MARGIN * uiScale); }
    private int soundHeaderGap()    { return Math.round(BASE_SOUND_HEADER_GAP     * uiScale); }

    private void initSettings() {
        int y = appY() + settingsHeaderGap();
        int bw = SCREEN_W - settingsSideMargin() * 2;
        int rowH = settingsRowH();
        int rowGap = settingsRowGap();

        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.theme_entry_btn")), b -> {
            goToApp(App.THEME);
        }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());

        y += rowGap;
        {

            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.sound_entry_btn")), b -> {
                goToApp(App.SOUND);
            }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());
        }

        y += rowGap;
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.privacy_btn")), b -> {
            goToApp(App.PRIVACY);
        }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());

        y += rowGap;
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.icon_editor_btn")), b -> {
            goToApp(App.ICON_EDITOR);
        }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());

        y += rowGap;
        {

            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.wallpaper_entry_btn")), b -> {
                goToApp(App.WALLPAPER);
            }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());
        }

        y += rowGap;
        {

            String langLabel = PhoneLang.currentDisplayName();
            addRenderableWidget(PhoneButton.phoneBuilder(
                    Component.literal(PhoneLang.get("icraft.phone.settings.language_btn", langLabel)), b -> {
                phoneData.language = PhoneLang.next(phoneData.language);
                saveSettings(); initCurrentApp();
            }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());
        }

        settingsContentBottom = y + rowH;
    }

    private void initPrivacy() {
        privacyScrollOffset = 0;
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.SETTINGS);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());
    }

    private void initSound() {
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.SETTINGS);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        int y = appY() + soundHeaderGap();
        int bw = SCREEN_W - settingsSideMargin() * 2;
        int rowH = settingsRowH();
        int rowGap = settingsRowGap();

        String soundLabel = phoneData.notificationSound.isEmpty()
                ? PhoneLang.get("icraft.phone.settings.sound_off")
                : phoneData.notificationSound;
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.sound_btn", soundLabel)), b -> {
            String[] d = sounds();
            String[] o = new String[d.length + 1];
            o[0] = "";
            System.arraycopy(d, 0, o, 1, d.length);
            int idx = Arrays.asList(o).indexOf(phoneData.notificationSound);
            phoneData.notificationSound = o[(idx + 1) % o.length];
            saveSettings(); initCurrentApp();
            if (!phoneData.notificationSound.isEmpty()) {
                com.icraft.client.NotificationSounds.play(phoneData.notificationSound);
            }
        }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());

        y += rowGap;

        String callSoundLabel = phoneData.callSound.isEmpty()
                ? PhoneLang.get("icraft.phone.settings.sound_off")
                : phoneData.callSound;
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.call_sound_btn", callSoundLabel)), b -> {
            String[] d = callSoundsArr();
            String[] o = new String[d.length + 1];
            o[0] = "";
            System.arraycopy(d, 0, o, 1, d.length);
            int idx = Arrays.asList(o).indexOf(phoneData.callSound);
            phoneData.callSound = o[(idx + 1) % o.length];
            saveSettings(); initCurrentApp();
            if (!phoneData.callSound.isEmpty()) {
                com.icraft.client.CallSounds.play(phoneData.callSound);
            }
        }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());

        y += rowGap;

        String clickSoundLabel = phoneData.clickSound.isEmpty()
                ? PhoneLang.get("icraft.phone.settings.sound_off")
                : phoneData.clickSound;
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.settings.click_sound_btn", clickSoundLabel)), b -> {
            String[] d = clickSoundsArr();
            String[] o = new String[d.length + 1];
            o[0] = "";
            System.arraycopy(d, 0, o, 1, d.length);
            int idx = Arrays.asList(o).indexOf(phoneData.clickSound);
            phoneData.clickSound = o[(idx + 1) % o.length];
            saveSettings(); initCurrentApp();
            if (!phoneData.clickSound.isEmpty()) {
                com.icraft.client.ClickSounds.play(phoneData.clickSound);
            }
        }).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());

        y += rowGap;
        addRenderableWidget(PhoneButton.phoneBuilder(
                Component.literal(PhoneLang.get("icraft.phone.settings.dnd_btn", (phoneData.doNotDisturb ? "ON" : "OFF"))),
                b -> { phoneData.doNotDisturb = !phoneData.doNotDisturb; saveSettings(); initCurrentApp(); }
        ).pos(sx() + settingsSideMargin(), y).size(bw, rowH).build());
    }

    private float lastDelta = 1.0f;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        lastDelta = delta;
        if (suppressRender) return;

        PhoneButton.setThemeColor(getThemeColor());

        if (cameraLayout) {
            updateCameraLook();
            renderCurrentApp(g, mouseX, mouseY);
        } else {
            renderBackground(g, mouseX, mouseY, delta);

            renderPhoneFrame(g);

            if (locked) {
                renderLockScreen(g, mouseX, mouseY);
            } else {
                renderStatusBar(g);
                renderCurrentApp(g, mouseX, mouseY);
                renderNavBar(g, mouseX, mouseY);
            }
        }

        for (var listener : this.children()) {
            if (listener instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(g, mouseX, mouseY, delta);
            }
        }
        g.flush();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (cameraLayout) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            g.flush();
            mc.gameRenderer.processBlurEffect(partialTick);
            mc.getMainRenderTarget().bindWrite(true);
            g.flush();
        }
    }

    private void renderPhoneFrame(GuiGraphics g) {
        int screenBg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFEEEEEE;

        g.fill(phoneX, phoneY, phoneX + PHONE_W, phoneY + PHONE_H, 0xFF000000);

        g.fill(phoneX + SCREEN_X_OFF, phoneY + SCREEN_Y_OFF,
               phoneX + SCREEN_X_OFF + SCREEN_W, phoneY + SCREEN_Y_OFF + SCREEN_H, screenBg);

        ResourceLocation caseLoc = getCaseTexture();
        if (caseLoc != null) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            g.blit(caseLoc, phoneX, phoneY, PHONE_W, PHONE_H,
                   0, 0, CASE_TEX_W, CASE_TEX_H, CASE_TEX_W, CASE_TEX_H);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            int fallback = getCaseColor();
            g.fill(phoneX, phoneY, phoneX + PHONE_W, phoneY + PHONE_H, fallback);
            g.fill(phoneX + SCREEN_X_OFF, phoneY + SCREEN_Y_OFF,
                   phoneX + SCREEN_X_OFF + SCREEN_W, phoneY + SCREEN_Y_OFF + SCREEN_H, screenBg);
        }
    }

    private ResourceLocation getCaseTexture() {
        for (int i = 0; i < CASES.length; i++) {
            if (CASES[i].equals(phoneData.currentCase)) return CASE_LOCS[i];
        }
        return CASE_LOCS[0];
    }

    private void renderStatusBar(GuiGraphics g) {
        int bgColor = phoneData.darkMode ? 0xFF16213E : 0xFF4A90D9;
        int bx = sx();
        int by = phoneY + SCREEN_Y_OFF;

        g.fill(bx, by, bx + SCREEN_W, by + STATUS_H, bgColor);

        int statusIconSize = Math.min(ICON_SIZE, STATUS_H - 2);
        int iconY = by + (STATUS_H - statusIconSize) / 2;

        long time = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getDayTime() % 24000 : 6000;
        int hours = (int)(6 + time / 1000) % 24;
        int mins  = (int)((time % 1000) * 60 / 1000);
        String timeStr = String.format("%02d:%02d", hours, mins);
        int timeY = by + (STATUS_H - 7) / 2 + 1;
        g.drawString(font, timeStr, bx + 5, timeY, 0xFFFFFFFF, false);

        int iconGap  = 1;
        int iconStep = statusIconSize + iconGap;
        int batteryX = bx + SCREEN_W - statusIconSize - 2;
        blitIcon(g, 0, 2, batteryX, iconY, statusIconSize);
        int wifiX = batteryX - iconStep;
        blitIcon(g, 1, 2, wifiX, iconY, statusIconSize);
        int signalX = wifiX - iconStep;
        blitIcon(g, 2, 2, signalX, iconY, statusIconSize);

        int totalUnread = getTotalUnreadCount();
        if (totalUnread > 0) {
            int bw = 14;
            int bh = 9;
            int badgeX = bx + (SCREEN_W - bw) / 2;
            int badgeTop = by + (STATUS_H - bh) * 2 / 3;
            int badgeBottom = badgeTop + bh;
            g.fill(badgeX, badgeTop, badgeX + bw, badgeBottom, 0xFFFF3333);
            int badgeTextY = badgeTop + (bh - 7) / 2;
            g.drawCenteredString(font, String.valueOf(Math.min(totalUnread, 9)),
                    badgeX + bw / 2, badgeTextY, 0xFFFFFFFF);
        }
    }

    private void renderLockScreen(GuiGraphics g, int mouseX, int mouseY) {
        int lx = sx();
        int ly = phoneY + SCREEN_Y_OFF;

        renderWallpaper(g, lx, ly, SCREEN_W, SCREEN_H);
        g.fill(lx, ly, lx + SCREEN_W, ly + SCREEN_H, 0x55000000);

        renderStatusBar(g);

        int centerX = lx + SCREEN_W / 2;
        int contentTop = ly + STATUS_H;

        String dateStr = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("EEEE dd MMM", PhoneLang.currentLocale()));
        g.drawCenteredString(font, dateStr, centerX, contentTop + 10, 0xFFEEEEEE);

        long time = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getDayTime() % 24000 : 6000;
        int hours = (int) (6 + time / 1000) % 24;
        int mins  = (int) ((time % 1000) * 60 / 1000);
        String timeStr = String.format("%02d:%02d", hours, mins);

        int timeY = contentTop + 22;
        float timeScale = 2.2f;
        drawScaledCenteredString(g, timeStr, centerX, timeY, timeScale, 0xFFFFFFFF);

        int belowTimeY = timeY + (int) (font.lineHeight * timeScale) + 12;

        int cardY = belowTimeY;
        int chatUnread = getChatUnreadCount();
        if (chatUnread > 0) {
            renderLockNotificationCard(g, lx, cardY, 0, 0,
                    PhoneLang.get("icraft.phone.lock.notif_title", chatUnread),
                    latestChatPreview());
            cardY += LOCK_CARD_H + LOCK_CARD_GAP;
        }
        if (timerUnread) {
            renderLockNotificationCard(g, lx, cardY, 4, 0,
                    PhoneLang.get("icraft.toast.timer_title"),
                    timerUnreadPreview);
            cardY += LOCK_CARD_H + LOCK_CARD_GAP;
        }

        int[] btn = lockUnlockButtonRect();
        boolean hovered = mouseX >= btn[0] && mouseX < btn[0] + btn[2]
                && mouseY >= btn[1] && mouseY < btn[1] + btn[3];
        renderUnlockBar(g, btn, hovered);
    }

    private static final int LOCK_CARD_H   = 26;
    private static final int LOCK_CARD_GAP = 4;

    private void renderLockNotificationCard(GuiGraphics g, int lx, int y, int iconCol, int iconRow,
                                              String title, String subtitle) {
        int cardX = lx + 4;
        int cardW = SCREEN_W - 8;
        int cardH = LOCK_CARD_H;

        g.fill(cardX, y, cardX + cardW, y + cardH, 0x992A2A3E);
        g.fill(cardX, y, cardX + cardW, y + 1, 0x33FFFFFF);

        int iconSize = Math.min(ICON_SIZE, cardH - 6);
        int iconX = cardX + 4;
        int iconY = y + (cardH - iconSize) / 2;
        blitIcon(g, iconCol, iconRow, iconX, iconY, iconSize);

        int textX = iconX + iconSize + 5;
        int maxTextW = cardX + cardW - textX - 4;

        g.drawString(font, truncate(title, maxTextW), textX, y + 4, 0xFFFFFFFF, false);
        g.drawString(font, truncate(subtitle, maxTextW), textX, y + 15, 0xFFB0B0B0, false);
    }

    private String latestChatPreview() {
        String myName = Minecraft.getInstance().getUser() != null
                ? Minecraft.getInstance().getUser().getName() : "";

        PhoneData.ChatMessage latest = null;
        for (PhoneData.ChatConversation conv : phoneData.conversations) {
            for (PhoneData.ChatMessage msg : conv.messages) {
                if (msg.read || msg.sender.equals(myName) || msg.deletedForAll) continue;
                if (latest == null || msg.timestamp > latest.timestamp) latest = msg;
            }
        }
        if (latest == null) return "";
        return displaySenderName(latest.sender) + ": " + displayMessageContent(latest.content);
    }

    private void renderUnlockBar(GuiGraphics g, int[] btn, boolean hovered) {
        int bx = btn[0], by = btn[1], bw = btn[2], bh = btn[3];
        int bg = hovered ? 0xCC33334A : 0xAA1E1E2C;
        g.fill(bx, by, bx + bw, by + bh, bg);

        int handleW = Math.max(20, bw / 5);
        int handleH = 2;
        int handleX = bx + (bw - handleW) / 2;
        int handleY = by + 6;
        int handleColor = hovered ? getThemeColor() : 0xFFAAAAAA;
        g.fill(handleX, handleY, handleX + handleW, handleY + handleH, handleColor);

        g.drawCenteredString(font, PhoneLang.get("icraft.phone.lock.unlock"),
                bx + bw / 2, by + bh - 12, 0xFFCCCCCC);
    }

    private int[] lockUnlockButtonRect() {
        int bw = Math.max(60, SCREEN_W - 30);
        int bh = 28;
        int bx = sx() + (SCREEN_W - bw) / 2;
        int by = phoneY + SCREEN_Y_OFF + SCREEN_H - bh - 14;
        return new int[]{bx, by, bw, bh};
    }

    private void renderCurrentApp(GuiGraphics g, int mouseX, int mouseY) {
        switch (currentApp) {
            case HOME      -> renderHome(g, mouseX, mouseY);
            case CHAT      -> renderChat(g, mouseX, mouseY);
            case CHAT_CONV -> renderChatConversation(g, mouseX, mouseY);
            case CAMERA    -> renderCamera(g, mouseX, mouseY);
            case PHOTOS    -> renderPhotos(g, mouseX, mouseY);
            case WEATHER   -> renderWeather(g, mouseX, mouseY);
            case CLOCK     -> renderClock(g, mouseX, mouseY);
            case NOTES     -> renderNotes(g, mouseX, mouseY);
            case NOTE_EDIT -> renderNoteEdit(g, mouseX, mouseY);
            case MAPA      -> renderMapa(g, mouseX, mouseY);
            case SETTINGS  -> renderSettings(g, mouseX, mouseY);
            case ICON_EDITOR -> renderIconEditor(g, mouseX, mouseY);
            case CONTACTS  -> { if (isCallLocked()) renderCallScreen(g, mouseX, mouseY); else renderContacts(g, mouseX, mouseY); }
            case PRIVACY   -> renderPrivacy(g, mouseX, mouseY);
            case CREATE_GROUP -> renderCreateGroup(g, mouseX, mouseY);
            case SOUND     -> renderSound(g, mouseX, mouseY);
            case WALLPAPER -> renderWallpaperPicker(g, mouseX, mouseY);
            case THEME     -> renderTheme(g, mouseX, mouseY);
        }
    }

    private void renderHome(GuiGraphics g, int mx, int my) {

        renderWallpaper(g, sx(), appY(), SCREEN_W, APP_H - NAV_H);

        g.fill(sx(), appY(), sx() + SCREEN_W, appY() + 30, 0x22000000);

        int[] iconCols = {0, 1, 2, 3, 4, 5, 6, 7, 8};

        String[] labels = effectiveAppLabels();
        String[] appKeys = {"CHAT","CAMERA","PHOTOS","WEATHER","CLOCK","NOTES","MAPA","SETTINGS","CONTACTS"};

        int cols    = 3;
        int cellW   = SCREEN_W / cols;
        int usableH = APP_H - NAV_H;
        int cellH   = usableH / 3;
        int iconSz  = 22;
        int iconHalf = iconSz / 2;
        int labelMaxW = cellW - 4;

        for (int i = 0; i < 9; i++) {
            int col = i % cols;
            int row = i / cols;

            int cx = sx() + col * cellW + cellW / 2;
            int cy = appY() + row * cellH + cellH / 2 - 4;

            int iconBg = getAppIconColor(appKeys[i]);
            g.fill(cx - iconHalf + 1, cy - iconHalf + 1, cx + iconHalf + 1, cy + iconHalf + 1, 0x33000000);
            g.fill(cx - iconHalf,     cy - iconHalf,     cx + iconHalf,     cy + iconHalf,     iconBg);
            g.fill(cx - iconHalf + 1, cy - iconHalf - 1, cx + iconHalf - 1, cy - iconHalf,    iconBg);
            g.fill(cx - iconHalf + 1, cy + iconHalf,     cx + iconHalf - 1, cy + iconHalf + 1, iconBg);

            int iconDrawX = cx - ICON_SIZE / 2;
            int iconDrawY = cy - ICON_SIZE / 2;
            blitIcon(g, iconCols[i], 0, iconDrawX, iconDrawY, ICON_SIZE);

            String label = labels[i];
            float labelScale = 1f;
            float labelW = font.width(label);
            if (labelW * labelScale > labelMaxW) labelScale = labelMaxW / labelW;
            labelScale = Math.max(0.6f, Math.min(labelScale, 1f));
            int labelColor = 0xFFFFFFFF;
            drawScaledCenteredString(g, label, cx, cy + iconHalf + 3, labelScale, labelColor, true);

            int badgeCount = 0;
            if (appKeys[i].equals("CHAT")) {
                badgeCount = getChatUnreadCount();
            } else if (appKeys[i].equals("CLOCK") && timerUnread) {
                badgeCount = 1;
            }

            if (badgeCount > 0) {
                int bx = cx + iconHalf - 3;
                int by = cy - iconHalf - 3;
                g.fill(bx, by, bx + 8, by + 8, 0xFFFF3333);
                String unreadStr = String.valueOf(Math.min(badgeCount, 9));
                g.drawCenteredString(font, unreadStr, bx + 4, by + (8 - 7) / 2, 0xFFFFFFFF);
            }
        }
    }

    private void renderContacts(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222;
        int subColor  = phoneData.darkMode ? 0xFF888888 : 0xFF666666;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 8, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.contacts.title"), sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        List<String> ordered = orderedContacts();

        int rowH = 22;
        int totalH = ordered.size() * rowH;
        int visibleH = appBottom() - NAV_H - (appY() + 17);
        int maxScroll = Math.max(0, totalH - visibleH);
        if (contactsScrollOffset > maxScroll) contactsScrollOffset = maxScroll;

        int y = appY() + 17 - contactsScrollOffset;
        for (String name : ordered) {
            if (y + 21 < appY() + 17) { y += rowH; continue; }
            if (y + 21 > appBottom() - NAV_H) break;
            boolean online = onlinePlayers.contains(name);

            int rowBg = phoneData.darkMode ? 0xFF16213E : 0xFFF5F5F5;
            g.fill(sx() + 1, y - 1, sx() + SCREEN_W - 1, y + 20, rowBg);

            drawPlayerFace(g, name, sx() + 3, y + 1, 16);
            if (!online) {
                g.fill(sx() + 3, y + 1, sx() + 19, y + 17, 0xAA000000);
            } else {
                g.fill(sx() + 15, y + 12, sx() + 20, y + 17, rowBg);
                g.fill(sx() + 16, y + 13, sx() + 19, y + 16, 0xFF4CAF50);
            }

            int nameColor = online ? textColor : subColor;
            g.drawString(font, truncate(name, SCREEN_W - 30), sx() + 23, y + 3, nameColor, false);
            boolean inCallWithRow = name.equals(activeCallPeer);
            String status = inCallWithRow
                    ? PhoneLang.get("icraft.phone.contacts.in_call")
                    : I18n.get(online ? "icraft.phone.contacts.online" : "icraft.phone.contacts.offline");
            int statusColor = inCallWithRow ? 0xFF4CAF50 : (online ? 0xFF4CAF50 : subColor);
            g.drawString(font, status, sx() + 23, y + 12, statusColor, false);

            y += rowH;
        }

        if (ordered.isEmpty()) {
            int margin = 14;
            int maxW = SCREEN_W - margin * 2;
            List<String> lines = wrapNoteText(PhoneLang.get("icraft.phone.contacts.empty"), maxW);
            int lineH = font.lineHeight + 2;
            int startY = appY() + 90 - (lines.size() - 1) * lineH / 2;
            for (int i = 0; i < lines.size(); i++) {
                g.drawCenteredString(font, lines.get(i), sx() + SCREEN_W / 2, startY + i * lineH, subColor);
            }
        }

        if (contactOptionsFor != null) {
            renderContactOptionsMenu(g, mx, my, contactOptionsFor);
        }
    }

    private void renderContactOptionsMenu(GuiGraphics g, int mx, int my, String contact) {
        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int menuTop = appY() + 30;
        int rowH = 20;
        int nameGap = 14;
        int menuH = 34 + nameGap + rowH * 2 + 12;
        int menuBottom = menuTop + menuH;

        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom() - NAV_H, 0x99000000);

        g.fill(menuX - 1, menuTop - 1, menuX + menuW + 1, menuBottom + 1, 0xFF333333);
        g.fill(menuX, menuTop, menuX + menuW, menuBottom, phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF);

        int headY = menuTop + 6;
        drawPlayerFace(g, contact, menuX + menuW / 2 - 10, headY, 20);
        g.drawCenteredString(font, truncate(contact, menuW - 10), menuX + menuW / 2, headY + 23,
                phoneData.darkMode ? 0xFFEEEEEE : 0xFF222222);

        int y = menuTop + 34 + nameGap;

        boolean hoverChat = mx >= menuX && mx < menuX + menuW && my >= y && my < y + rowH - 2;
        g.fill(menuX + 3, y, menuX + menuW - 3, y + rowH - 2, hoverChat ? 0xFF2E7D32 : 0xCC1565C0);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.contacts.chat_btn"),
                menuX + menuW / 2, y + (rowH - 2 - 8) / 2, 0xFFFFFFFF);
        y += rowH;

        boolean contactOnline = onlinePlayers.contains(contact);
        boolean hoverCall = contactOnline && mx >= menuX && mx < menuX + menuW && my >= y && my < y + rowH - 2;
        int callBg = !contactOnline ? 0xFF2A2A2A : (hoverCall ? 0xFF2E7D32 : 0xCC1565C0);
        int callTextColor = !contactOnline ? 0xFF777777 : 0xFFFFFFFF;
        g.fill(menuX + 3, y, menuX + menuW - 3, y + rowH - 2, callBg);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.contacts.call_btn"),
                menuX + menuW / 2, y + (rowH - 2 - 8) / 2, callTextColor);
        y += rowH;

        boolean hoverCancel = mx >= menuX && mx < menuX + menuW && my >= y && my < menuBottom;
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.common.cancel_btn"),
                menuX + menuW / 2, y + 2, hoverCancel ? 0xFFFF5555 : 0xFFAAAAAA);
    }

    private void renderCreateGroup(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222;
        int subColor = phoneData.darkMode ? 0xFF888888 : 0xFF666666;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        g.drawString(font, PhoneLang.get("icraft.phone.chats.new_group_title"), sx() + 24, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        g.drawString(font, PhoneLang.get("icraft.phone.common.name_label"), sx() + 5, appY() + 16, subColor, false);

        g.drawString(font, PhoneLang.get("icraft.phone.chats.select_members_label"), sx() + 5, appY() + 34, subColor, false);

        List<String> contacts = orderedContacts();
        int rowH = 20;
        int listTop = appY() + 44;
        int listBottom = appBottom() - NAV_H - 16;

        int y = listTop;
        for (String name : contacts) {
            if (y + rowH > listBottom) break;
            boolean selected = groupSelectedMembers.contains(name);
            boolean online = onlinePlayers.contains(name);

            int rowBg = selected
                    ? (phoneData.darkMode ? 0xFF1B3A1B : 0xFFD8F5D8)
                    : (phoneData.darkMode ? 0xFF16213E : 0xFFF5F5F5);
            g.fill(sx() + 1, y, sx() + SCREEN_W - 1, y + rowH - 1, rowBg);

            int checkX = sx() + 3, checkY = y + 4;
            g.fill(checkX, checkY, checkX + 10, checkY + 10, selected ? 0xFF4CAF50 : 0x44AAAAAA);
            if (selected) g.drawString(font, "✓", checkX + 1, checkY + 1, 0xFFFFFFFF, false);

            drawPlayerFace(g, name, sx() + 16, y + 2, 14);

            int nameColor = online ? textColor : subColor;
            g.drawString(font, truncate(name, SCREEN_W - 50), sx() + 33, y + 3, nameColor, false);
            g.drawString(font, online ? PhoneLang.get("icraft.phone.contacts.online") : PhoneLang.get("icraft.phone.contacts.offline"),
                    sx() + 33, y + 11, online ? 0xFF4CAF50 : subColor, false);

            y += rowH;
        }

        if (contacts.isEmpty()) {
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.chats.no_contacts"),
                    sx() + SCREEN_W / 2, listTop + 20, subColor);
        }

        if (!groupSelectedMembers.isEmpty()) {
            String sel = PhoneLang.get("icraft.phone.chats.selected_count", groupSelectedMembers.size());
            g.drawString(font, sel, sx() + 5, listBottom + 2, getThemeColor(), false);
        }

        if (hasNewGroupInvite && !pendingGroupInvites.isEmpty()) {
            g.fill(sx() + SCREEN_W - 20, appY() + 1, sx() + SCREEN_W - 2, appY() + 13, 0xFFFF3333);
            g.drawCenteredString(font, "!", sx() + SCREEN_W - 11, appY() + 3, 0xFFFFFFFF);
        }
    }

    private boolean handleCreateGroupClick(double mx, double my) {
        List<String> contacts = orderedContacts();
        int rowH = 20;
        int listTop = appY() + 44;
        int listBottom = appBottom() - NAV_H - 16;
        int y = listTop;
        for (String name : contacts) {
            if (y + rowH > listBottom) break;
            if (my >= y && my < y + rowH && mx >= sx() + 1 && mx < sx() + SCREEN_W - 1) {
                if (groupSelectedMembers.contains(name)) {
                    groupSelectedMembers.remove(name);
                } else {
                    groupSelectedMembers.add(name);
                }
                return true;
            }
            y += rowH;
        }
        return false;
    }

    private List<String> orderedContacts() {
        String myName = Minecraft.getInstance().getUser().getName();
        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        for (String p : knownContacts) {
            if (p.equals(myName)) continue;
            if (onlinePlayers.contains(p)) online.add(p); else offline.add(p);
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);
        offline.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> ordered = new ArrayList<>(online);
        ordered.addAll(offline);
        return ordered;
    }

    private boolean handleContactsClick(double mx, double my) {
        if (contactOptionsFor != null) {
            return handleContactOptionsMenuClick(mx, my);
        }

        List<String> ordered = orderedContacts();
        int y = appY() + 17 - contactsScrollOffset;
        for (String name : ordered) {
            if (my >= y - 1 && my < y + 21 && mx >= sx() + 1 && mx < sx() + SCREEN_W - 1) {
                playClickSound();
                contactOptionsFor = name;
                return true;
            }
            y += 22;
        }
        return false;
    }

    private boolean handleContactOptionsMenuClick(double mx, double my) {
        String contact = contactOptionsFor;
        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int menuTop = appY() + 30;
        int rowH = 20;
        int nameGap = 14;

        int y = menuTop + 34 + nameGap;
        if (my >= y && my < y + rowH - 2 && mx >= menuX && mx < menuX + menuW) {
            playClickSound();
            contactOptionsFor = null;
            openChatWith(contact);
            return true;
        }
        y += rowH;
        if (my >= y && my < y + rowH - 2 && mx >= menuX && mx < menuX + menuW) {
            if (!onlinePlayers.contains(contact)) {

                return true;
            }
            playClickSound();
            contactOptionsFor = null;
            startCallWith(contact);
            return true;
        }

        contactOptionsFor = null;
        return true;
    }

    private void startCallWith(String player) {
        String myName = Minecraft.getInstance().getUser().getName();
        if (player.equals(myName)) return;
        if (isCallLocked()) return;

        NetworkManager.sendToServer(new CallRequestPacket(player, false));
        callState = CallState.RINGING_OUT;
        callPeerPending = player;
    }

    private void answerCall(boolean accepted) {
        NetworkManager.sendToServer(new CallAnswerPacket(accepted));
        if (!accepted) {

            CallSounds.stopRing();
            callState = CallState.NONE;
            callPeerPending = null;
        }
    }

    private void toggleMute() {
        micMuted = !micMuted;
        NetworkManager.sendToServer(new CallMutePacket(micMuted));
    }

    private static void notifyIncomingCall() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneScreen ps) {
            ps.currentApp = App.CONTACTS;
            ps.contactOptionsFor = null;
            ps.locked = false;
        }
    }

    public static void applyCallStatus(String peerName, String status) {
        if (!status.equals("INCOMING")) {
            CallSounds.stopRing();
        }
        switch (status) {
            case "RINGING" -> {
                callState = CallState.RINGING_OUT;
                callPeerPending = peerName;
            }
            case "INCOMING" -> {
                callState = CallState.INCOMING;
                callPeerPending = peerName;
                ringTickCounter = 0;
                notifyIncomingCall();
            }
            case "CONNECTED_CALLER" -> {
                callState = CallState.CONNECTED;
                activeCallPeer = peerName;
                callPeerPending = null;
                micMuted = false;
                peerMuted = false;
            }
            case "CONNECTED_CALLEE" -> {
                callState = CallState.CONNECTED;
                activeCallPeer = peerName;
                callPeerPending = null;
                micMuted = false;
                peerMuted = false;
            }
            case "DECLINED" -> {
                callState = CallState.NONE;
                callPeerPending = null;
                peerMuted = false;
            }
            case "ENDED" -> {
                if (peerName.isEmpty() || peerName.equals(activeCallPeer) || peerName.equals(callPeerPending)) {
                    activeCallPeer = null;
                }
                callState = CallState.NONE;
                callPeerPending = null;
                peerMuted = false;
            }
            case "TARGET_BUSY" -> {
                callState = CallState.NONE;
                callPeerPending = null;
            }
            case "NOT_INSTALLED" -> {
                callState = CallState.NONE;
                callPeerPending = null;
            }
            case "CALLER_NOT_CONNECTED" -> {
                callState = CallState.NONE;
                callPeerPending = null;
            }
            case "TARGET_NOT_CONNECTED" -> {
                callState = CallState.NONE;
                callPeerPending = null;
            }
            case "TARGET_OFFLINE" -> {
                callState = CallState.NONE;
                callPeerPending = null;
            }
            case "PEER_MUTED" -> {

                if (callState == CallState.CONNECTED && peerName.equals(activeCallPeer)) {
                    peerMuted = true;
                }
            }
            case "PEER_UNMUTED" -> {
                if (callState == CallState.CONNECTED && peerName.equals(activeCallPeer)) {
                    peerMuted = false;
                }
            }
            default -> {}
        }
    }

    private void renderCallScreen(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222;
        int subColor = phoneData.darkMode ? 0xFF888888 : 0xFF666666;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        String peer = callState == CallState.CONNECTED ? activeCallPeer : callPeerPending;
        if (peer == null) peer = "";

        int centerX = sx() + SCREEN_W / 2;
        int faceY = appY() + 26;
        drawPlayerFace(g, peer, centerX - 20, faceY, 40);
        g.drawCenteredString(font, truncate(peer, SCREEN_W - 20), centerX, faceY + 46, textColor);

        String statusText = switch (callState) {
            case RINGING_OUT -> PhoneLang.get("icraft.phone.contacts.call_ringing_out");
            case INCOMING    -> PhoneLang.get("icraft.phone.contacts.call_ringing_in");
            case CONNECTED   -> micMuted
                    ? PhoneLang.get("icraft.phone.contacts.call_active_muted")
                    : PhoneLang.get("icraft.phone.contacts.call_active");
            default -> "";
        };
        g.drawCenteredString(font, statusText, centerX, faceY + 60, subColor);

        if (callState == CallState.CONNECTED && peerMuted) {
            int warnColor = 0xFFFFA726;
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.contacts.call_peer_muted", peer),
                    centerX, faceY + 72, warnColor);
        }

        int rowH = 20;
        int btnW = SCREEN_W - 20;
        int btnX = sx() + 10;
        int bottomPad = 12;

        if (callState == CallState.INCOMING) {
            int declineY = appBottom() - NAV_H - bottomPad - rowH;
            int acceptY = declineY - 4 - rowH;

            boolean hoverAccept = mx >= btnX && mx < btnX + btnW && my >= acceptY && my < acceptY + rowH;
            g.fill(btnX, acceptY, btnX + btnW, acceptY + rowH, hoverAccept ? 0xFF43A047 : 0xFF2E7D32);
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.contacts.accept_btn"),
                    centerX, acceptY + (rowH - 8) / 2, 0xFFFFFFFF);

            boolean hoverDecline = mx >= btnX && mx < btnX + btnW && my >= declineY && my < declineY + rowH;
            g.fill(btnX, declineY, btnX + btnW, declineY + rowH, hoverDecline ? 0xFFE53935 : 0xFFC62828);
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.contacts.decline_btn"),
                    centerX, declineY + (rowH - 8) / 2, 0xFFFFFFFF);
            return;
        }

        int hangupY = appBottom() - NAV_H - bottomPad - rowH;
        boolean hoverHangup = mx >= btnX && mx < btnX + btnW && my >= hangupY && my < hangupY + rowH;
        g.fill(btnX, hangupY, btnX + btnW, hangupY + rowH, hoverHangup ? 0xFFE53935 : 0xFFC62828);
        String hangupLabel = callState == CallState.RINGING_OUT
                ? PhoneLang.get("icraft.phone.common.cancel_btn")
                : PhoneLang.get("icraft.phone.contacts.hangup_btn");
        g.drawCenteredString(font, hangupLabel, centerX, hangupY + (rowH - 8) / 2, 0xFFFFFFFF);

        if (callState == CallState.CONNECTED) {
            int muteY = hangupY - 4 - rowH;
            boolean hoverMute = mx >= btnX && mx < btnX + btnW && my >= muteY && my < muteY + rowH;
            int muteBg = micMuted ? (hoverMute ? 0xFF757575 : 0xFF616161) : (hoverMute ? 0xFF1E88E5 : 0xFF1565C0);
            g.fill(btnX, muteY, btnX + btnW, muteY + rowH, muteBg);
            g.drawCenteredString(font, PhoneLang.get(micMuted
                            ? "icraft.phone.contacts.unmute_btn" : "icraft.phone.contacts.mute_btn"),
                    centerX, muteY + (rowH - 8) / 2, 0xFFFFFFFF);
        }
    }

    private boolean handleCallScreenClick(double mx, double my) {
        int rowH = 20;
        int btnW = SCREEN_W - 20;
        int btnX = sx() + 10;
        int bottomPad = 12;

        if (callState == CallState.INCOMING) {
            int declineY = appBottom() - NAV_H - bottomPad - rowH;
            int acceptY = declineY - 4 - rowH;

            if (my >= acceptY && my < acceptY + rowH && mx >= btnX && mx < btnX + btnW) {
                playClickSound();
                answerCall(true);
                return true;
            }
            if (my >= declineY && my < declineY + rowH && mx >= btnX && mx < btnX + btnW) {
                playClickSound();
                answerCall(false);
                return true;
            }
            return true;
        }

        int hangupY = appBottom() - NAV_H - bottomPad - rowH;
        if (my >= hangupY && my < hangupY + rowH && mx >= btnX && mx < btnX + btnW) {
            playClickSound();
            NetworkManager.sendToServer(new CallRequestPacket("", true));
            return true;
        }

        if (callState == CallState.CONNECTED) {
            int muteY = hangupY - 4 - rowH;
            if (my >= muteY && my < muteY + rowH && mx >= btnX && mx < btnX + btnW) {
                playClickSound();
                toggleMute();
                return true;
            }
        }

        return true;
    }

    private void openChatWith(String player) {
        String myName = Minecraft.getInstance().getUser().getName();
        if (player.equals(myName)) return;

        String convId = canonicalDmId(myName, player);
        PhoneData.ChatConversation conv = phoneData.conversations.stream()
                .filter(c -> c.id.equals(convId)).findFirst()
                .orElseGet(() -> {
                    PhoneData.ChatConversation nc = new PhoneData.ChatConversation(convId, player, false);
                    phoneData.conversations.add(0, nc);
                    return nc;
                });
        currentConv = conv;
        chatConvScrollOffset = Integer.MAX_VALUE;
        chatAtBottom = true;
        goToApp(App.CHAT_CONV);
    }

    private static String canonicalDmId(String playerA, String playerB) {
        if (playerA.compareToIgnoreCase(playerB) <= 0) {
            return "dm_" + playerA + "_" + playerB;
        } else {
            return "dm_" + playerB + "_" + playerA;
        }
    }

    private static String extractOtherFromDmId(String convId, String myName) {
        if (convId == null || !convId.startsWith("dm_")) return null;
        String rest = convId.substring(3);
        String prefix = myName + "_";
        String suffix = "_" + myName;
        if (rest.startsWith(prefix)) {
            return rest.substring(prefix.length());
        } else if (rest.endsWith(suffix)) {
            return rest.substring(0, rest.length() - suffix.length());
        }
        return null;
    }

    public static void applyContacts(List<String> players) {
        knownContacts = new ArrayList<>(players);
    }

    private void renderChat(GuiGraphics g, int mouseX, int mouseY) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        int textColor = phoneData.darkMode ? 0xFFEEEEEE : 0xFF222222;
        int subColor  = phoneData.darkMode ? 0xFF888888 : 0xFF666666;

        blitIcon(g, 1, 1, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.chats.title"), sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        int nameMaxW  = SCREEN_W - 26 - 22;
        int previewMaxW = SCREEN_W - 26 - 22;

        int totalConvH = phoneData.conversations.size() * 22;
        int visibleConvH = appBottom() - NAV_H - (appY() + 17);
        int maxConvScroll = Math.max(0, totalConvH - visibleConvH);
        if (chatListScrollOffset > maxConvScroll) chatListScrollOffset = maxConvScroll;

        int y = appY() + 17 - chatListScrollOffset;
        for (PhoneData.ChatConversation conv : phoneData.conversations) {
            if (y + 21 < appY() + 17) { y += 22; continue; }
            if (y + 21 > appBottom() - NAV_H) break;
            int rowBg = phoneData.darkMode ? 0xFF16213E : 0xFFF5F5F5;
            g.fill(sx() + 1, y - 1, sx() + SCREEN_W - 1, y + 20, rowBg);

            drawConversationAvatar(g, conv, sx() + 3, y + 1, 16);

            String mutedPrefix = conv.muted ? "[M] " : "";
            String name = truncate(mutedPrefix + conv.name, nameMaxW);
            g.drawString(font, name, sx() + 23, y + 2, textColor, false);

            String preview = conv.messages.isEmpty() ? PhoneLang.get("icraft.phone.chats.no_messages_preview")
                    : displayMessageContent(conv.messages.get(conv.messages.size() - 1).content);
            g.drawString(font, truncate(preview, previewMaxW), sx() + 23, y + 11, subColor, false);

            int unread = conv.getUnreadCount(Minecraft.getInstance().getUser().getName());
            if (unread > 0) {
                g.fill(sx() + SCREEN_W - 18, y + 4, sx() + SCREEN_W - 4, y + 15, 0xFFFF3333);
                g.drawCenteredString(font, String.valueOf(Math.min(unread, 9)),
                        sx() + SCREEN_W - 11, y + 6, 0xFFFFFFFF);
            }

            y += 22;
        }

        if (phoneData.conversations.isEmpty()) {
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.chats.empty"), sx() + SCREEN_W / 2, appY() + 90, subColor);
        }
    }

    private void drawConversationAvatar(GuiGraphics g, PhoneData.ChatConversation conv, int x, int y, int size) {
        if (conv.isGroup) {
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF1B5E20);
            if (worldIconLocation != null) {
                g.blit(worldIconLocation, x, y, size, size, 0, 0, worldIconW, worldIconH, worldIconW, worldIconH);
            } else {
                g.blit(WORLD_ICON, x, y, size, size, 0, 0,
                        WORLD_ICON_TEX_SIZE, WORLD_ICON_TEX_SIZE, WORLD_ICON_TEX_SIZE, WORLD_ICON_TEX_SIZE);
            }
            return;
        }
        drawPlayerFace(g, conv.name, x, y, size);
    }

    private void drawPlayerFace(GuiGraphics g, String name, int x, int y, int size) {
        net.minecraft.client.multiplayer.ClientPacketListener connection =
                Minecraft.getInstance().getConnection();
        net.minecraft.client.multiplayer.PlayerInfo info =
                connection != null ? connection.getPlayerInfo(name) : null;

        if (info != null) {
            var skin = info.getSkin();
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, skin, x, y, size);
            SkinFetcher.cacheFromPlayerInfo(name, skin);
            return;
        }

        SkinFetcher.FaceTexture fetchedFace = SkinFetcher.getFace(name);
        if (fetchedFace != null) {
            g.blit(fetchedFace.location(), x, y, size, size,
                    8, 8, 8, 8, fetchedFace.texW(), fetchedFace.texH());
            // Second layer (hat/overlay) — accessories like glasses live here.
            // Without this the head shown after a player disconnects loses that
            // layer compared to the live vanilla render, which always draws both.
            if (fetchedFace.texH() >= 64) {
                g.blit(fetchedFace.location(), x, y, size, size,
                        40, 8, 8, 8, fetchedFace.texW(), fetchedFace.texH());
            }
        } else {
            g.fill(x, y, x + size, y + size, getThemeColor());
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            g.drawCenteredString(font, initial, x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
        }
    }

    private java.util.List<net.minecraft.util.FormattedCharSequence> wrapMessageLines(String content, int maxWidth) {
        java.util.List<net.minecraft.util.FormattedCharSequence> result = new java.util.ArrayList<>();
        for (String rawLine : content.split("\n", -1)) {
            net.minecraft.network.chat.MutableComponent comp;
            if (rawLine.contains("§")) {
                comp = net.minecraft.network.chat.Component.translatable(rawLine);
                comp = legacyToComponent(rawLine);
            } else {
                comp = net.minecraft.network.chat.Component.literal(rawLine);
            }
            result.addAll(font.split(comp, maxWidth));
        }
        return result;
    }

    private static net.minecraft.network.chat.MutableComponent legacyToComponent(String text) {
        net.minecraft.network.chat.MutableComponent root =
                net.minecraft.network.chat.Component.empty();
        net.minecraft.network.chat.Style currentStyle =
                net.minecraft.network.chat.Style.EMPTY;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                if (current.length() > 0) {
                    root.append(net.minecraft.network.chat.Component
                            .literal(current.toString()).withStyle(currentStyle));
                    current.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (fmt == net.minecraft.ChatFormatting.RESET) {
                        currentStyle = net.minecraft.network.chat.Style.EMPTY;
                    } else {
                        currentStyle = currentStyle.applyFormat(fmt);
                    }
                }
                i++;
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            root.append(net.minecraft.network.chat.Component
                    .literal(current.toString()).withStyle(currentStyle));
        }
        return root;
    }

    private void renderChatConversation(GuiGraphics g, int mx, int my) {
        if (currentConv == null) { currentApp = App.CHAT; return; }

        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFF0F0F0;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        int hdrBg = phoneData.darkMode ? 0xFF16213E : 0xFFD0E8FF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appY() + 18, hdrBg);

        int avatarSize = 14;
        int avatarX = sx() + 22;
        int avatarY = appY() + 2;
        if (currentConv.isGroup) {
            if (worldIconLocation != null) {
                g.blit(worldIconLocation, avatarX, avatarY, avatarSize, avatarSize,
                        0, 0, worldIconW, worldIconH, worldIconW, worldIconH);
            } else {
                g.blit(WORLD_ICON, avatarX, avatarY, avatarSize, avatarSize,
                        0, 0, WORLD_ICON_TEX_SIZE, WORLD_ICON_TEX_SIZE,
                        WORLD_ICON_TEX_SIZE, WORLD_ICON_TEX_SIZE);
            }
        } else {
            drawPlayerFace(g, currentConv.name, avatarX, avatarY, avatarSize);
        }

        int nameX = avatarX + avatarSize + 4;
        int nameMaxW = (sx() + SCREEN_W - 28) - nameX;
        g.drawString(font,
                truncate(currentConv.name, nameMaxW),
                nameX, appY() + 5,
                phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222, false);

        String myName = Minecraft.getInstance().getUser().getName();
        int maxBubW = SCREEN_W - 10;
        int maxTextW = maxBubW - 10;

        java.util.List<Integer> msgHeights = new java.util.ArrayList<>();
        for (PhoneData.ChatMessage msg : currentConv.messages) {
            if (msg.deletedForAll) { msgHeights.add(12); continue; }
            boolean isMe2 = msg.sender.equals(myName);
            int h;
            if (msg.content.startsWith("§§PHOTO:")) {
                h = 56;
            } else {
                java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                    wrapMessageLines(displayMessageContent(msg.content), maxTextW);
                h = lines.size() * 10 + 8;
            }
            if (currentConv.isGroup && !isMe2) h += 10;
            msgHeights.add(h + 4);
        }
        boolean hasPinned = !currentConv.pinnedMessages.isEmpty();
        int pinnedH = hasPinned ? 8 : 0;
        int msgsTop = appY() + 20 + pinnedH;
        if (hasPinned) {
            g.fill(sx(), appY() + 18, sx() + SCREEN_W, appY() + 18 + pinnedH, 0x44FFD700);
            g.drawString(font, PhoneLang.get("icraft.phone.chat.pinned_count", currentConv.pinnedMessages.size()) ,
                    sx() + 3, appY() + 19, 0xFFFFD700, false);
        }

        int totalH = msgHeights.stream().mapToInt(Integer::intValue).sum();
        int visibleH = appBottom() - NAV_H - msgsTop - 20;
        int maxScroll = Math.max(0, totalH - visibleH);

        if (newMessageForActiveConv) {
            newMessageForActiveConv = false;
            if (chatAtBottom) chatConvScrollOffset = Integer.MAX_VALUE;
        }

        if (chatConvScrollOffset > maxScroll) chatConvScrollOffset = maxScroll;
        if (chatConvScrollOffset < 0) chatConvScrollOffset = 0;
        chatAtBottom = chatConvScrollOffset >= maxScroll;

        int msgY = msgsTop - chatConvScrollOffset;
        int clipTop = msgsTop;
        int clipBottom = appBottom() - NAV_H - 20;

        g.enableScissor(sx(), clipTop, sx() + SCREEN_W, clipBottom);

        for (int i = 0; i < currentConv.messages.size(); i++) {
            PhoneData.ChatMessage msg = currentConv.messages.get(i);
            int mh = msgHeights.get(i);
            if (msgY + mh < clipTop) { msgY += mh; continue; }
            if (msgY > clipBottom) break;

            if (msg.deletedForAll) {
                if (msgY >= clipTop) g.drawString(font, PhoneLang.get("icraft.phone.chat.deleted_msg"), sx() + 5, msgY, 0xFF888888, false);
                msgY += 12;
                continue;
            }
            boolean isMe = msg.sender.equals(myName);
            int bubbleBg = isMe ? getThemeColor() : (phoneData.darkMode ? 0xFF2D2D44 : 0xFFDDDDDD);
            int textC = isMe ? 0xFFFFFFFF : (phoneData.darkMode ? 0xFFEEEEEE : 0xFF222222);

            if (currentConv.isGroup && !isMe) {
                if (msgY >= clipTop && msgY + 9 <= clipBottom)
                    g.drawString(font, displaySenderName(msg.sender), sx() + 5, msgY, 0xFFAAAAAA, false);
                msgY += 10;
            }

            if (msg.content.startsWith("§§PHOTO:")) {
                String sharedFilename = msg.content.substring("§§PHOTO:".length());
                int thumbSize = 48;
                int bubW = thumbSize + 8;
                int bubH = thumbSize + 8;
                int bx = isMe ? (sx() + SCREEN_W - bubW - 4) : (sx() + 4);

                if (msgY >= clipTop - bubH && msgY <= clipBottom) {
                    g.fill(bx, msgY, bx + bubW, msgY + bubH, bubbleBg);
                    ResourceLocation thumbLoc = getOrLoadPhotoTexture(sharedFilename);
                    if (thumbLoc != null) {
                        int[] dims = photoDimsCache.get(sharedFilename);
                        int srcW = dims != null ? dims[0] : thumbSize;
                        int srcH = dims != null ? dims[1] : thumbSize;
                        g.blit(thumbLoc, bx + 4, msgY + 4, thumbSize, thumbSize,
                                0, 0, srcW, srcH, srcW, srcH);
                    } else {
                        g.fill(bx + 4, msgY + 4, bx + 4 + thumbSize, msgY + 4 + thumbSize, 0xFF555555);
                        g.drawCenteredString(font, "📷", bx + 4 + thumbSize / 2, msgY + 4 + thumbSize / 2 - 4, 0xFFFFFFFF);
                    }
                }
                msgY += bubH + 4;
                msg.read = true;
                continue;
            }

            java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                wrapMessageLines(displayMessageContent(msg.content), maxTextW);
            int bubH = lines.size() * 10 + 8;
            int bubW = 0;
            for (net.minecraft.util.FormattedCharSequence seq : lines) {
                bubW = Math.max(bubW, font.width(seq) + 14);
            }
            bubW = Math.min(maxBubW, bubW);
            int bx = isMe ? (sx() + SCREEN_W - bubW - 4) : (sx() + 4);

            if (msgY >= clipTop - bubH && msgY <= clipBottom) {
                g.fill(bx, msgY, bx + bubW, msgY + bubH, bubbleBg);
                int lineY = msgY + 4;
                for (net.minecraft.util.FormattedCharSequence seq : lines) {
                    g.drawString(font, seq, bx + 5, lineY, textC, false);
                    lineY += 10;
                }
            }

            msgY += bubH + 4;
            msg.read = true;
        }

        g.disableScissor();

        if (!chatAtBottom) {
            int[] bb = scrollToBottomBtnBounds();
            g.fill(bb[0], bb[1], bb[0] + bb[2], bb[1] + bb[3], 0xCC222222);
            g.drawCenteredString(font, "▼", bb[0] + bb[2] / 2, bb[1] + 2, 0xFFFFFFFF);
        }
    }

    private int[] scrollToBottomBtnBounds() {
        int bw = 16, bh = 12;
        int clipBottom = appBottom() - NAV_H - 20;
        int bx2 = sx() + SCREEN_W - bw - 3;
        int by2 = clipBottom - bh - 2;
        return new int[]{bx2, by2, bw, bh};
    }

    private void captureCameraMouse() {
        if (cameraMouseCaptured) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        org.lwjgl.glfw.GLFW.glfwSetInputMode(window,
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
        if (org.lwjgl.glfw.GLFW.glfwRawMouseMotionSupported()) {
            org.lwjgl.glfw.GLFW.glfwSetInputMode(window,
                    org.lwjgl.glfw.GLFW.GLFW_RAW_MOUSE_MOTION, org.lwjgl.glfw.GLFW.GLFW_TRUE);
        }
        double[] xb = new double[1];
        double[] yb = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, xb, yb);
        camPrevCursorX = xb[0];
        camPrevCursorY = yb[0];
        cameraMouseCaptured = true;
    }

    private void releaseCameraMouse() {
        if (!cameraMouseCaptured) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        org.lwjgl.glfw.GLFW.glfwSetInputMode(window,
                org.lwjgl.glfw.GLFW.GLFW_RAW_MOUSE_MOTION, org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwSetInputMode(window,
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
        cameraMouseCaptured = false;
    }

    private void updateCameraLook() {
        if (!cameraMouseCaptured) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.isWindowActive()) return;
        long window = mc.getWindow().getWindow();
        double[] xb = new double[1];
        double[] yb = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, xb, yb);
        double dx = xb[0] - camPrevCursorX;
        double dy = yb[0] - camPrevCursorY;
        camPrevCursorX = xb[0];
        camPrevCursorY = yb[0];
        if (dx == 0.0 && dy == 0.0) return;
        double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
        double scale = sens * sens * sens * 8.0 * 0.15;
        mc.player.turn(dx * scale, dy * scale);
    }

    private void renderCamera(GuiGraphics g, int mx, int my) {
        int pfLeft   = phoneX;
        int pfTop    = phoneY;
        int pfRight  = phoneX + PHONE_W;
        int pfBottom = phoneY + PHONE_H;

        int[] vp = cameraViewportRect();
        int vpLeft   = vp[0];
        int vpTop    = vp[1];
        int vpRight  = vp[2];
        int vpBottom = vp[3];

        int filterTint = getFilterColor();
        if (filterTint != 0) {
            g.fill(vpLeft, vpTop, vpRight, vpBottom, filterTint);
        }

        int barH = 16;
        g.fill(vpLeft, vpTop, vpRight, vpTop + barH, 0x88000000);
        String recLabel = PhoneLang.get("icraft.phone.camera.rec_label");
        g.drawString(font, recLabel, vpLeft + 6, vpTop + 4, 0xFFFFFFFF, false);
        String perspName = switch (cameraPerspective) {
            case 1 -> PhoneLang.get("icraft.phone.camera.persp_third_back");
            case 2 -> PhoneLang.get("icraft.phone.camera.persp_third_front");
            default -> PhoneLang.get("icraft.phone.camera.persp_first");
        };
        String hint = PhoneLang.get("icraft.phone.camera.controls_hint", perspName);
        g.drawString(font, hint, vpRight - font.width(hint) - 6, vpTop + 4, 0xFFFFFFFF, false);

        int innerTop = vpTop + barH;
        int cornerLen = 14, cornerTh = 2, pad = 6;
        int cc = 0xFFFFFFFF;
        g.fill(vpLeft + pad, innerTop + pad, vpLeft + pad + cornerLen, innerTop + pad + cornerTh, cc);
        g.fill(vpLeft + pad, innerTop + pad, vpLeft + pad + cornerTh, innerTop + pad + cornerLen, cc);
        g.fill(vpRight - pad - cornerLen, innerTop + pad, vpRight - pad, innerTop + pad + cornerTh, cc);
        g.fill(vpRight - pad - cornerTh,  innerTop + pad, vpRight - pad, innerTop + pad + cornerLen, cc);
        g.fill(vpLeft + pad, vpBottom - pad - cornerTh, vpLeft + pad + cornerLen, vpBottom - pad, cc);
        g.fill(vpLeft + pad, vpBottom - pad - cornerLen, vpLeft + pad + cornerTh, vpBottom - pad, cc);
        g.fill(vpRight - pad - cornerLen, vpBottom - pad - cornerTh, vpRight - pad, vpBottom - pad, cc);
        g.fill(vpRight - pad - cornerTh,  vpBottom - pad - cornerLen, vpRight - pad, vpBottom - pad, cc);

        int cx = (vpLeft + vpRight) / 2;

        int vpW = vpRight - vpLeft;
        int vpH = vpBottom - innerTop;
        long now_ms = System.currentTimeMillis();
        switch (selectedFilter) {
            case "enderman" -> {

                float fpulse = 0.5f + 0.5f * (float)Math.sin(now_ms / 300.0);
                int fa = (int)(0x22 + fpulse * 0x44);
                int fc = (fa << 24) | 0x1A0033;
                int fb = (int)(6 + fpulse * 8);
                g.fill(vpLeft, innerTop, vpRight, innerTop + fb, fc);
                g.fill(vpLeft, vpBottom - fb, vpRight, vpBottom, fc);
                g.fill(vpLeft, innerTop + fb, vpLeft + fb, vpBottom - fb, fc);
                g.fill(vpRight - fb, innerTop + fb, vpRight, vpBottom - fb, fc);
            }
            case "bat" -> {

                float fpulse = 0.5f + 0.5f * (float)Math.sin(now_ms / 300.0);
                int fa = (int)(0x22 + fpulse * 0x44);
                int fc = (fa << 24) | 0x000011;
                int fb = (int)(6 + fpulse * 8);
                g.fill(vpLeft, innerTop, vpRight, innerTop + fb, fc);
                g.fill(vpLeft, vpBottom - fb, vpRight, vpBottom, fc);
                g.fill(vpLeft, innerTop + fb, vpLeft + fb, vpBottom - fb, fc);
                g.fill(vpRight - fb, innerTop + fb, vpRight, vpBottom - fb, fc);
            }
            case "creeper" -> {
                int cl = 0x22003300;
                for (int sy = innerTop; sy < vpBottom; sy += 4) {
                    g.fill(vpLeft, sy + 2, vpRight, sy + 3, cl);
                }
            }
            case "blaze" -> {
                float fpulse = 0.5f + 0.5f * (float)Math.sin(now_ms / 300.0);
                int fa = (int)(0x22 + fpulse * 0x44);
                int fc = (fa << 24) | 0xFF6600;
                int fb = (int)(6 + fpulse * 8);
                g.fill(vpLeft, innerTop, vpRight, innerTop + fb, fc);
                g.fill(vpLeft, vpBottom - fb, vpRight, vpBottom, fc);
                g.fill(vpLeft, innerTop + fb, vpLeft + fb, vpBottom - fb, fc);
                g.fill(vpRight - fb, innerTop + fb, vpRight, vpBottom - fb, fc);
            }
            default -> {}
        }

        if (!selectedFilter.equals("none")) {
            String filterLabel = selectedFilter.toUpperCase();
            int flW = font.width(filterLabel) + 8;
            int flX = cx - flW / 2;
            int flY = innerTop + 3;
            int pillColor = switch (selectedFilter) {
                case "sepia"  -> 0xCC8B6914;
                case "vivid"  -> 0xCC147832;
                case "cool"   -> 0xCC143C8B;
                case "warm"   -> 0xCCBB4400;
                case "noir"   -> 0xCC222222;
                case "retro"  -> 0xCCAA5500;
                case "fade"      -> 0xCC556677;
                case "creeper"   -> 0xCC1A6B1A;
                case "enderman"  -> 0xCC330066;
                case "skeleton"  -> 0xCCCCCCCC;
                case "blaze"     -> 0xCCCC6600;
                case "bat"       -> 0xCC111122;
                default          -> 0xCC444444;
            };
            g.fill(flX, flY, flX + flW, flY + 11, pillColor);
            g.drawString(font, filterLabel, flX + 4, flY + 2, 0xFFFFFFFF, false);
        }

        String count = PhoneLang.get("icraft.phone.photos.count_label", phoneData.photos.size());
        int countMargin = 8;
        int countX = vpRight - pad - cornerLen - countMargin - font.width(count);
        int countY = vpBottom - pad - cornerLen - countMargin - font.lineHeight;
        g.drawString(font, count, countX, countY, 0xFF88FF88, false);

        renderFovSlider(g, pfLeft, pfTop, pfRight, pfBottom, vpRight, vpTop, vpBottom);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        if (pfRight > vpRight) {
            g.fill(vpRight, pfTop, pfRight, pfBottom, 0x66000000);
        }
        int borderColor = 0x33FFFFFF;
        g.fill(pfLeft, pfTop, pfRight, pfTop + 1, borderColor);
        g.fill(pfLeft, pfBottom - 1, pfRight, pfBottom, borderColor);
        g.fill(pfLeft, pfTop, pfLeft + 1, pfBottom, borderColor);
        g.fill(pfRight - 1, pfTop, pfRight, pfBottom, borderColor);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private void renderFovSlider(GuiGraphics g,
                                  int pfLeft, int pfTop, int pfRight, int pfBottom,
                                  int vpRight, int vpTop, int vpBottom) {
        int bx = vpRight + 2;
        int bRight = pfRight - 3;
        int bw = bRight - bx;
        if (bw < 4) return;

        int margin = Math.max(8, (pfBottom - pfTop) / 8);
        int minTopMargin = 23;
        int trackX  = bx + bw / 2 - 1;
        int trackTop = pfTop + Math.max(margin, minTopMargin);
        int trackBot = pfBottom - margin;
        int trackH  = trackBot - trackTop;

        g.fill(trackX, trackTop, trackX + 2, trackBot, 0x88FFFFFF);

        float t = (float)(CAM_FOV_MAX - cameraFov) / (CAM_FOV_MAX - CAM_FOV_MIN);
        int thumbY = trackTop + (int)(t * trackH);

        int thumbH = 6;
        int thumbW = bw - 2;
        int thumbX = bx + 1;
        boolean teleZoom = cameraFov <= CAM_FOV_TELE_THRESHOLD;
        int thumbColor = teleZoom ? 0xFFFFD24A : 0xFFFFFFFF;
        g.fill(thumbX, thumbY - thumbH / 2, thumbX + thumbW, thumbY + thumbH / 2, thumbColor);

        int plusY = trackTop - 9;
        g.drawString(font, "+", bx + bw / 2 - 3, plusY, 0xAAFFFFFF, false);
        g.drawString(font, "-", bx + bw / 2 - 3, trackBot + 2, 0xAAFFFFFF, false);

        String fovLabel = cameraFov + "°";
        int fovColor = teleZoom ? 0xFFFFD24A : 0xFFAAFFAA;
        int fovLabelY = plusY - font.lineHeight - 2;
        g.drawCenteredString(font, fovLabel, bx + bw / 2, fovLabelY, fovColor);
    }

    private void renderPhotos(GuiGraphics g, int mx, int my) {
        if (photoViewerIndex >= 0 && photoViewerIndex < phoneData.photos.size()) {
            renderPhotoViewer(g, mx, my);
            return;
        }

        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 2, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.photos.title"), sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        int subColor = phoneData.darkMode ? 0xFF888888 : 0xFF666666;

        int cols = 3;
        int cellSize = (SCREEN_W - 6) / cols;
        int headerH = 18;
        int gridStartY = appY() + headerH;

        int contentBottom = appBottom() - NAV_H;

        int totalPhotos = phoneData.photos.size();
        int totalRows = (totalPhotos + cols - 1) / cols;
        int totalGridH = totalRows * cellSize;
        int visibleH = contentBottom - gridStartY;
        int maxScroll = Math.max(0, totalGridH - visibleH + 4);
        if (photosScrollOffset > maxScroll) photosScrollOffset = maxScroll;
        if (photosScrollOffset < 0) photosScrollOffset = 0;

        for (int i = 0; i < totalPhotos; i++) {
            PhoneData.PhotoEntry photo = phoneData.photos.get(i);
            int col = i % cols;
            int row = i / cols;
            int px = sx() + 3 + col * cellSize;
            int py = gridStartY + row * cellSize - photosScrollOffset;

            if (py + cellSize <= gridStartY) continue;
            if (py >= contentBottom) break;

            int drawY = Math.max(py, gridStartY);
            int clipH = Math.min(py + cellSize, contentBottom) - drawY;
            if (clipH <= 0) continue;

            ResourceLocation texLoc = getOrLoadPhotoTexture(photo.filename);
            if (texLoc != null) {
                int[] dims = photoDimsCache.get(photo.filename);
                int srcW = dims != null ? dims[0] : (cellSize - 2);
                int srcH = dims != null ? dims[1] : (cellSize - 2);
                int squareSrc = Math.min(srcW, srcH);
                int uvX = (srcW - squareSrc) / 2;
                int uvY = (srcH - squareSrc) / 2;
                int dstSize = cellSize - 2;

                int clipTop = Math.max(0, gridStartY - py);
                int clipBot = Math.max(0, (py + dstSize) - contentBottom);
                int visH = dstSize - clipTop - clipBot;
                if (visH <= 0) continue;

                int uvClipTop = clipTop * squareSrc / dstSize;
                int uvClipBot = clipBot * squareSrc / dstSize;
                int uvH = squareSrc - uvClipTop - uvClipBot;

                g.blit(texLoc,
                        px, py + clipTop,
                        dstSize, visH,
                        uvX, uvY + uvClipTop,
                        squareSrc, uvH,
                        srcW, srcH);
            } else {
                g.fill(px, drawY, px + cellSize - 2, drawY + clipH, getPhotoColor(photo.filter));
            }

            if (py + (cellSize - 2) > gridStartY && py < contentBottom) {
                if (photo.selfie) {
                    g.drawString(font, "🤳", px + cellSize - 14, Math.max(py, gridStartY) + 2, 0xFFFFFFFF, false);
                }
            }
        }

        if (totalPhotos == 0) {
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.empty_line1"), sx() + SCREEN_W / 2, appY() + 74, subColor);
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.empty_line2"), sx() + SCREEN_W / 2, appY() + 86, subColor);
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.empty_line3"), sx() + SCREEN_W / 2, appY() + 98, subColor);
        }

        if (totalGridH > visibleH && totalPhotos > 0) {
            int trackX  = sx() + SCREEN_W - 3;
            int trackTop = gridStartY + 2;
            int trackBot = contentBottom - 2;
            int trackH  = trackBot - trackTop;
            g.fill(trackX, trackTop, trackX + 2, trackBot, 0x44FFFFFF);
            float ratio = (float) photosScrollOffset / maxScroll;
            int thumbH = Math.max(8, trackH * visibleH / totalGridH);
            int thumbY = trackTop + (int)((trackH - thumbH) * ratio);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF);
        }
    }

    private static LocalDateTime parsePhotoTimestamp(String filename) {
        try {
            String base = filename;
            if (base.endsWith(".png")) base = base.substring(0, base.length() - 4);
            if (base.startsWith("photo_")) base = base.substring("photo_".length());
            return LocalDateTime.parse(base, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
        } catch (Exception e) {
            return null;
        }
    }

    private void renderPhotoViewer(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF0D0D1A : 0xFF111111;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        PhoneData.PhotoEntry photo = phoneData.photos.get(photoViewerIndex);

        int headerH = 14;
        int hdrBg = phoneData.darkMode ? 0xCC16213E : 0xCC222222;
        g.fill(sx(), appY(), sx() + SCREEN_W, appY() + headerH, hdrBg);
        g.drawString(font, PhoneLang.get("icraft.phone.common.back_btn"), sx() + 3, appY() + 3, 0xFFFFFFFF, false);

        String delIcon = "🗑";
        int delBtnW = font.width(delIcon) + 6;
        int delBtnX = sx() + SCREEN_W - delBtnW;
        boolean delHover = mx >= delBtnX && mx < delBtnX + delBtnW && my >= appY() && my < appY() + headerH;
        g.fill(delBtnX, appY(), delBtnX + delBtnW, appY() + headerH, delHover ? 0xCCFF3333 : 0x66FF3333);
        g.drawCenteredString(font, delIcon, delBtnX + delBtnW / 2, appY() + 3, 0xFFFFFFFF);

        String counter = (photoViewerIndex + 1) + "/" + phoneData.photos.size();
        g.drawString(font, counter, delBtnX - font.width(counter) - 5, appY() + 3, 0xFFAAAAAA, false);

        int imgAreaY = appY() + headerH + 2;
        int totalH = appBottom() - imgAreaY - 2;
        int metaH = 36;
        int navH2 = 18;
        int imgAreaH = totalH - metaH - navH2 - 4;
        int imgAreaW = SCREEN_W - 4;

        ResourceLocation texLoc = getOrLoadPhotoTexture(photo.filename);
        int[] dims = photoDimsCache.get(photo.filename);
        int dispW = imgAreaW, dispH = imgAreaH;
        if (dims != null && dims[0] > 0 && dims[1] > 0) {
            double aspect = dims[0] / (double) dims[1];
            dispW = imgAreaW;
            dispH = (int) Math.round(dispW / aspect);
            if (dispH > imgAreaH) {
                dispH = imgAreaH;
                dispW = (int) Math.round(dispH * aspect);
            }
        }
        int imgDrawX = sx() + 2 + (imgAreaW - dispW) / 2;
        int imgDrawY = imgAreaY;

        if (texLoc != null) {
            g.blit(texLoc, imgDrawX, imgDrawY, dispW, dispH,
                    0, 0, dims != null ? dims[0] : dispW, dims != null ? dims[1] : dispH,
                    dims != null ? dims[0] : dispW, dims != null ? dims[1] : dispH);
        } else {
            g.fill(sx() + 2, imgAreaY, sx() + 2 + imgAreaW, imgAreaY + dispH,
                    getPhotoColor(photo.filter));
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.no_preview"),
                    sx() + SCREEN_W / 2, imgAreaY + dispH / 2 - 5, 0xFF888888);
        }

        int contentBottom = appBottom() - NAV_H;
        int navBtnH = 14;
        int navBtnGap = 4;
        int navAreaY = contentBottom - navBtnH - navBtnGap;

        int shareBtnH = 14;
        int shareBtnGap = 3;
        int shareAreaY = navAreaY - shareBtnH - shareBtnGap;
        int shareW = SCREEN_W - 10;
        int shareBtnX = sx() + 5;

        int infoZoneTop = imgAreaY + dispH + 4;
        int infoZoneH = shareAreaY - infoZoneTop - 4;

        String rawName = photo.filename;

        int lineGap = 3;
        int lineH = 8;
        LocalDateTime photoDateTime = parsePhotoTimestamp(rawName);
        String dateLine = photoDateTime != null
                ? photoDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null;
        String timeLine = photoDateTime != null
                ? photoDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) : null;
        int numLines = (dateLine != null ? 2 : 0) + 2;
        int totalTextH = numLines * lineH + (numLines - 1) * lineGap;
        int infoStartY = infoZoneTop + Math.max(0, (infoZoneH - totalTextH) / 2);

        boolean photoDialogOpen = photoDeleteConfirmOpen
                || (photoShareMenuOpen && photoShareIndex == photoViewerIndex);

        if (!photoDialogOpen) {
            int curY = infoStartY;
            if (dateLine != null) {
                g.drawCenteredString(font, dateLine, sx() + SCREEN_W / 2, curY, 0xFF88CCFF);
                curY += lineH + lineGap;
                g.drawCenteredString(font, timeLine, sx() + SCREEN_W / 2, curY, 0xFF88CCFF);
                curY += lineH + lineGap;
            }

            String filterLabel = photo.filter.equals("none") ? PhoneLang.get("icraft.phone.photos.filter_none")
                    : PhoneLang.get("icraft.phone.photos.filter_label", photo.filter.toUpperCase());
            int filterColor = photo.filter.equals("none") ? 0xFF888888 : 0xFF88EEFF;
            String line2 = filterLabel + (photo.selfie ? "  " + PhoneLang.get("icraft.phone.photos.selfie_tag") : "");
            g.drawCenteredString(font, line2, sx() + SCREEN_W / 2, curY, filterColor);
            curY += lineH + lineGap;

            String coordStr = photo.worldX + ", " + photo.worldY + ", " + photo.worldZ;
            g.drawCenteredString(font, coordStr, sx() + SCREEN_W / 2, curY, 0xFFAAFFAA);

            g.fill(shareBtnX, shareAreaY, shareBtnX + shareW, shareAreaY + shareBtnH, 0xCC1565C0);
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.send_chat_btn"), shareBtnX + shareW / 2, shareAreaY + 3, 0xFFFFFFFF);

            int navW = 26, navGap = 14;
            if (phoneData.photos.size() > 1) {
                boolean canPrev = photoViewerIndex > 0;
                boolean canNext = photoViewerIndex < phoneData.photos.size() - 1;
                int navPairW = navW * 2 + navGap;
                int navX = sx() + (SCREEN_W - navPairW) / 2;

                int bgOn = 0xAA000000, bgOff = 0x44000000;
                int fgOn = 0xFFFFFFFF, fgOff = 0x55AAAAAA;

                g.fill(navX, navAreaY, navX + navW, navAreaY + navBtnH, canPrev ? bgOn : bgOff);
                g.drawCenteredString(font, "<", navX + navW / 2, navAreaY + 3, canPrev ? fgOn : fgOff);

                g.fill(navX + navW + navGap, navAreaY, navX + navW * 2 + navGap, navAreaY + navBtnH, canNext ? bgOn : bgOff);
                g.drawCenteredString(font, ">", navX + navW + navGap + navW / 2, navAreaY + 3, canNext ? fgOn : fgOff);
            }
        }

        if (photoShareMenuOpen && photoShareIndex == photoViewerIndex) {
            renderPhotoShareMenu(g, mx, my, photo.filename);
        }

        if (photoDeleteConfirmOpen) {
            renderPhotoDeleteConfirm(g);
        }
    }

    private void renderPhotoDeleteConfirm(GuiGraphics g) {

        g.flush();

        int headerH = 14;
        int coverTop = appY() + headerH;
        int coverBottom = appBottom() - NAV_H;
        g.fill(sx(), coverTop, sx() + SCREEN_W, coverBottom,
                phoneData.darkMode ? 0xFF0D0D1A : 0xFF111111);

        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int menuH = 46;
        int menuTop = coverTop + (coverBottom - coverTop - menuH) / 2;
        int menuBottom = menuTop + menuH;

        g.fill(menuX - 1, menuTop - 1, menuX + menuW + 1, menuBottom + 1, 0xFF333333);
        g.fill(menuX, menuTop, menuX + menuW, menuBottom, phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.delete_confirm_title"),
                menuX + menuW / 2, menuTop + 6, phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222);

        int btnY = menuTop + 20;
        int btnH = 14;
        int btnGap = 4;
        int btnW = (menuW - btnGap) / 2;

        g.fill(menuX, btnY, menuX + btnW, btnY + btnH, 0xAA555555);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.common.cancel_btn"), menuX + btnW / 2, btnY + 3, 0xFFFFFFFF);

        int delX = menuX + btnW + btnGap;
        g.fill(delX, btnY, delX + btnW, btnY + btnH, 0xAAFF3333);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.delete_confirm_yes"), delX + btnW / 2, btnY + 3, 0xFFFFFFFF);
    }

    private void renderPhotoShareMenu(GuiGraphics g, int mx, int my, String filename) {

        g.flush();

        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int rowH = 18;

        List<PhoneData.ChatConversation> shareableConvs = phoneData.conversations.stream()
                .filter(c -> GlobalImagesState.isEnabled()
                        || !com.icraft.server.PhoneServerHandler.GLOBAL_GROUP_ID.equals(c.id))
                .toList();
        int maxRows = Math.min(shareableConvs.size(), 6);

        int titleH = 17;
        int cancelH = 14;
        int cancelGap = 6;
        int menuH = titleH + maxRows * rowH + cancelGap + cancelH;

        int contentTop = appY() + 14;
        int contentBottom = appBottom() - NAV_H;
        int menuTop = contentTop + Math.max(0, (contentBottom - contentTop - menuH) / 2);
        int menuBottom = menuTop + menuH;

        g.fill(menuX - 1, menuTop - 1, menuX + menuW + 1, menuBottom + 1, 0xFF333333);
        g.fill(menuX, menuTop, menuX + menuW, menuBottom, phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.photos.share_menu_title"), menuX + menuW / 2, menuTop + 4,
                getThemeColor());
        g.fill(menuX, menuTop + 14, menuX + menuW, menuTop + 15, 0x44AAAAAA);

        int y = menuTop + titleH;
        int shown = 0;
        for (PhoneData.ChatConversation conv : shareableConvs) {
            if (shown >= maxRows) break;
            boolean hover = mx >= menuX && mx < menuX + menuW && my >= y && my < y + rowH;
            g.fill(menuX, y, menuX + menuW, y + rowH - 1,
                    hover ? 0x44FFFFFF : 0);
            drawConversationAvatar(g, conv, menuX + 2, y + 1, 14);
            g.drawString(font, truncate(conv.name, menuW - 22), menuX + 19, y + 5,
                    phoneData.darkMode ? 0xFFEEEEEE : 0xFF222222, false);
            y += rowH;
            shown++;
        }

        int cancelY = menuBottom - cancelH;
        g.fill(menuX, cancelY, menuX + menuW, menuBottom, 0xAAFF3333);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.common.cancel_btn"), menuX + menuW / 2, cancelY + 3, 0xFFFFFFFF);
    }

    private void sharePhotoToConversation(PhoneData.ChatConversation conv, String filename) {
        if (conv == null || filename == null || filename.isEmpty()) return;
        if (com.icraft.server.PhoneServerHandler.GLOBAL_GROUP_ID.equals(conv.id) && !GlobalImagesState.isEnabled()) {
            notifications.add(PhoneLang.get("icraft.phone.photos.share_global_blocked"));
            photoShareMenuOpen = false;
            return;
        }

        try {
            Path photoFile = getPhotosDir().resolve(filename);
            if (Files.exists(photoFile)) {
                byte[] bytes = Files.readAllBytes(photoFile);
                if (bytes.length <= 512 * 1024) {
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    NetworkManager.sendToServer(new com.icraft.network.PhotoUploadPacket(
                            filename, base64, conv.id, conv.isGroup,
                            conv.isGroup ? "" : conv.name));
                } else {
                    notifications.add(PhoneLang.get("icraft.phone.photos.share_too_big"));
                    photoShareMenuOpen = false;
                    return;
                }
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudo leer \"{}\" para compartir: {}", filename, e.getMessage());
        }

        String content = "§§PHOTO:" + filename;
        String myName = Minecraft.getInstance().getUser().getName();
        PhoneData.ChatMessage m = new PhoneData.ChatMessage(myName, content);
        conv.messages.add(m);
        receivedMessageIds.add(m.id);
        NetworkManager.sendToServer(new SendChatPacket(
                conv.id, conv.isGroup ? "" : conv.name,
                content, conv.isGroup, false, m.id));
        photoShareMenuOpen = false;
        notifications.add(PhoneLang.get("icraft.phone.photos.shared_to", conv.name));
    }

    private ResourceLocation getOrLoadPhotoTexture(String filename) {
        if (photoTextureCache.containsKey(filename)) {
            return photoTextureCache.get(filename);
        }

        Path photoFile = getPhotosDir().resolve(filename);
        if (!Files.exists(photoFile)) {
            photoTextureCache.put(filename, null);
            return null;
        }

        try (InputStream is = Files.newInputStream(photoFile)) {
            NativeImage nativeImg = NativeImage.read(is);
            photoDimsCache.put(filename, new int[]{nativeImg.getWidth(), nativeImg.getHeight()});
            DynamicTexture dynTex = new DynamicTexture(nativeImg);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    "icraft", "dynamic/photo/" + filename.replace(".png", "").replace(".", "_"));
            Minecraft.getInstance().getTextureManager().register(loc, dynTex);
            photoTextureCache.put(filename, loc);
            photoTextures.put(filename, dynTex);
            return loc;
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("Could not load photo texture {}: {}", filename, e.getMessage());
            photoTextureCache.put(filename, null);
            return null;
        }
    }

    private void blitWeatherBackground(GuiGraphics g, int condition, int timeOfDayIdx, int x, int y, int w, int h) {
        ResourceLocation loc = WEATHER_BG_LOCS[timeOfDayIdx][condition];
        float scale = Math.max(w / (float) WEATHER_BG_TEX_W, h / (float) WEATHER_BG_TEX_H);
        int srcW = Math.min(WEATHER_BG_TEX_W, Math.round(w / scale));
        int srcH = Math.min(WEATHER_BG_TEX_H, Math.round(h / scale));
        int srcX = (WEATHER_BG_TEX_W - srcW) / 2;
        int srcY = (WEATHER_BG_TEX_H - srcH) / 2;
        g.blit(loc, x, y, w, h, srcX, srcY, srcW, srcH, WEATHER_BG_TEX_W, WEATHER_BG_TEX_H);
    }

    private void renderWeather(GuiGraphics g, int mx, int my) {
        String weatherStr = PhoneLang.get("icraft.phone.weather.cond_clear");
        String tempStr = "20 C";
        String timeOfDay = PhoneLang.get("icraft.phone.weather.time_day");
        int condition = WX_CLEAR;
        int timeOfDayIdx = TOD_AFTERNOON;
        long forecastDay = 0L;

        if (Minecraft.getInstance().level != null) {
            var level = Minecraft.getInstance().level;
            forecastDay = level.getDayTime() / 24000L;
            if (level.isThundering()) { weatherStr = PhoneLang.get("icraft.phone.weather.cond_storm"); tempStr = "8 C"; condition = WX_STORM; }
            else if (level.isRaining())  { weatherStr = PhoneLang.get("icraft.phone.weather.cond_rain");   tempStr = "14 C"; condition = WX_RAIN; }

            long t = level.getDayTime() % 24000;
            if (t >= TOD_DAWN_START || t < TOD_MORNING_START) {
                // 22500 -> 500 (da la vuelta a medianoche del ciclo de 24000 ticks)
                timeOfDay = PhoneLang.get("icraft.phone.weather.time_dawn");
                timeOfDayIdx = TOD_DAWN;
            } else if (t < TOD_AFTERNOON_START) {
                // 500 -> 11700
                timeOfDay = PhoneLang.get("icraft.phone.weather.time_morning");
                timeOfDayIdx = TOD_MORNING;
            } else if (t < TOD_NIGHT_START) {
                // 11700 -> 14000
                timeOfDay = PhoneLang.get("icraft.phone.weather.time_afternoon");
                timeOfDayIdx = TOD_AFTERNOON;
            } else {
                // 14000 -> 22500
                timeOfDay = PhoneLang.get("icraft.phone.weather.time_night");
                timeOfDayIdx = TOD_NIGHT;
            }
        }

        blitWeatherBackground(g, condition, timeOfDayIdx, sx(), appY(), SCREEN_W, appBottom() - appY());
        ensureWeatherForecast(forecastDay);

        blitIcon(g, 3, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.weather.title"), sx() + 20, appY() + 4, 0xFFFFFFFF, false);

        int boxSize = 56;
        int boxX = sx() + SCREEN_W / 2 - boxSize / 2;
        int boxY = appY() + 20;
        int bigIconSize = 40;
        int bigIconX = boxX + (boxSize - bigIconSize) / 2;
        int bigIconY = boxY + (boxSize - bigIconSize) / 2;
        blitWeatherIcon(g, condition, timeOfDayIdx, bigIconX, bigIconY, bigIconSize);

        int textY = boxY + boxSize + 8;
        g.drawCenteredString(font, weatherStr, sx() + SCREEN_W / 2, textY,      0xFFFFFFFF);
        g.drawCenteredString(font, tempStr,    sx() + SCREEN_W / 2, textY + 12, 0xFFFFFF88);
        g.drawCenteredString(font, timeOfDay,  sx() + SCREEN_W / 2, textY + 24, 0xFFCCFFFF);

        int dividerY = textY + 40;
        g.fill(sx() + 10, dividerY, sx() + SCREEN_W - 10, dividerY + 1, 0x44FFFFFF);

        String[] days  = {
            PhoneLang.get("icraft.phone.weather.time_morning"),
            PhoneLang.get("icraft.phone.weather.time_afternoon"),
            PhoneLang.get("icraft.phone.weather.time_night"),
            PhoneLang.get("icraft.phone.weather.time_dawn")
        };
        int[] dayTimeIdx = { TOD_MORNING, TOD_AFTERNOON, TOD_NIGHT, TOD_DAWN };
        String[] baseTemps = {"22 C", "18 C", "12 C", "15 C"};
        int rowY = dividerY + 10;
        int rowMargin = 10;
        int rowW = SCREEN_W - rowMargin * 2;
        int colW = rowW / 4;
        int rowX = sx() + rowMargin;
        float dayScale = 1f;
        for (String d : days) {
            float w = font.width(d);
            if (w * dayScale > colW - 4) dayScale = (colW - 4) / w;
        }
        dayScale = Math.min(dayScale, 1f);
        float tempScale = 1f;
        for (String t : baseTemps) {
            float w = font.width(t);
            if (w * tempScale > colW - 4) tempScale = (colW - 4) / w;
        }
        tempScale = Math.min(tempScale, 1f);
        for (int i = 0; i < 4; i++) {
            int colCenterX = rowX + i * colW + colW / 2;
            int slotCondition = weatherForecastConditions[i];

            blitWeatherIcon(g, slotCondition, dayTimeIdx[i], colCenterX - ICON_SIZE / 2, rowY, ICON_SIZE);
            drawScaledCenteredString(g, days[i], colCenterX, rowY + 20, dayScale, 0xFFCCCCCC);
            drawScaledCenteredString(g, baseTemps[i], colCenterX, rowY + 30, tempScale, 0xFFFFFF88);
        }
    }

    private void initClock() {
        clearWidgets();

        timerUnread = false;

        int tabY = appY() + 17;
        int tabH = 13;
        int tabGap = 2;
        String[] tabLabels = {PhoneLang.get("icraft.phone.clock.tab_clock"), PhoneLang.get("icraft.phone.clock.tab_stopwatch"), PhoneLang.get("icraft.phone.clock.tab_timer")};
        int tabW = (SCREEN_W - 6 - tabGap * 2) / 3;

        for (int i = 0; i < tabLabels.length; i++) {
            final int idx = i;
            int tx = sx() + 3 + i * (tabW + tabGap);
            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(tabLabels[i]), b -> {
                clockTab = idx;
                initClock();
            }).pos(tx, tabY).size(tabW, tabH).build());
        }

        if (clockTab == 1) {
            int top = clockTabContentTop(CLOCK_TAB1_CONTENT_H);
            int btnY = top + 36;
            int btnW = (SCREEN_W - 16) / 2;
            addRenderableWidget(PhoneButton.phoneBuilder(
                    Component.literal(PhoneLang.get(stopwatchRunning ? "icraft.phone.clock.pause_btn" : "icraft.phone.clock.start_btn")),
                    b -> toggleStopwatch()
            ).pos(sx() + 5, btnY).size(btnW, 14).build());

            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.clock.reset_btn")), b -> resetStopwatch())
                    .pos(sx() + 5 + btnW + 6, btnY).size(btnW, 14).build());

        } else if (clockTab == 2) {
            int contentH = timerRunning ? CLOCK_TAB2_CONTENT_H_RUNNING : CLOCK_TAB2_CONTENT_H_IDLE;
            int top = clockTabContentTop(contentH);

            if (!timerRunning) {
                int adjY = top + 36;
                int adjW = (SCREEN_W - 18) / 4;
                long[] deltas = {-60_000L, -10_000L, 10_000L, 60_000L};
                String[] labels = {"-1m", "-10s", "+10s", "+1m"};
                for (int i = 0; i < 4; i++) {
                    final long d = deltas[i];
                    addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(labels[i]), b -> adjustTimer(d))
                            .pos(sx() + 4 + i * (adjW + 2), adjY).size(adjW, 12).build());
                }
            }

            int btnY = top + (timerRunning ? 36 : 54);
            int btnW = (SCREEN_W - 16) / 2;
            addRenderableWidget(PhoneButton.phoneBuilder(
                    Component.literal(PhoneLang.get(timerRunning ? "icraft.phone.clock.pause_btn" : "icraft.phone.clock.start_btn")),
                    b -> toggleTimer()
            ).pos(sx() + 5, btnY).size(btnW, 14).build());

            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.clock.reset_btn")), b -> resetTimer())
                    .pos(sx() + 5 + btnW + 6, btnY).size(btnW, 14).build());
        }
    }

    private static long getStopwatchElapsedMs() {
        long elapsed = stopwatchAccumulatedMs;
        if (stopwatchRunning) elapsed += System.currentTimeMillis() - stopwatchStartedAt;
        return elapsed;
    }

    private void toggleStopwatch() {
        if (stopwatchRunning) {
            stopwatchAccumulatedMs += System.currentTimeMillis() - stopwatchStartedAt;
            stopwatchRunning = false;
        } else {
            stopwatchStartedAt = System.currentTimeMillis();
            stopwatchRunning = true;
        }
        initClock();
    }

    private void resetStopwatch() {
        stopwatchRunning = false;
        stopwatchAccumulatedMs = 0;
        initClock();
    }

    private void adjustTimer(long deltaMs) {
        if (timerRunning) return;
        timerDurationMs = Math.max(TIMER_MIN_MS, Math.min(TIMER_MAX_MS, timerDurationMs + deltaMs));
        timerRemainingMs = timerDurationMs;
        timerFinished = false;
        initClock();
    }

    private void toggleTimer() {
        if (timerRunning) {
            timerRemainingMs = Math.max(0, timerEndAt - System.currentTimeMillis());
            timerRunning = false;
        } else {
            if (timerRemainingMs <= 0) timerRemainingMs = timerDurationMs;
            timerEndAt = System.currentTimeMillis() + timerRemainingMs;
            timerRunning = true;
            timerFinished = false;
        }
        initClock();
    }

    private void resetTimer() {
        timerRunning = false;
        timerFinished = false;
        timerRemainingMs = timerDurationMs;
        initClock();
    }

    private int clockTabContentTop(int contentH) {
        return clockTabContentTop(contentH, 0.5f);
    }

    private int clockTabContentTop(int contentH, float verticalBias) {
        int tabY = appY() + 17;
        int tabH = 13;
        int contentTop = tabY + tabH + 8;
        int availableH = (appBottom() - NAV_H) - contentTop;
        return contentTop + Math.max(0, (int) ((availableH - contentH) * verticalBias));
    }

    private void renderClock(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222;
        int subColor  = phoneData.darkMode ? 0xFF888888 : 0xFF666666;

        blitIcon(g, 4, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.clock.tab_clock"), sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx() + 2, appY() + 14, sx() + SCREEN_W - 2, appY() + 15, 0x44AAAAAA);

        int tabY = appY() + 17;
        int tabH = 13;
        int tabGap = 2;
        int tabW = (SCREEN_W - 6 - tabGap * 2) / 3;
        int activeX = sx() + 3 + clockTab * (tabW + tabGap);
        g.fill(activeX, tabY + tabH + 1, activeX + tabW, tabY + tabH + 3, getThemeColor());

        int centerX = sx() + SCREEN_W / 2;

        switch (clockTab) {
            case 0 -> renderClockTabMain(g, centerX, textColor, subColor);
            case 1 -> renderClockTabStopwatch(g, centerX, textColor, subColor);
            case 2 -> renderClockTabTimer(g, centerX, textColor, subColor);
        }
    }

    private void renderClockTabMain(GuiGraphics g, int centerX, int textColor, int subColor) {
        int top = clockTabContentTop(CLOCK_TAB0_CONTENT_H);

        long fullTime = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getDayTime() : 6000;
        long timeOfDay = fullTime % 24000;
        int mcHours = (int) (6 + timeOfDay / 1000) % 24;
        int mcMins  = (int) ((timeOfDay % 1000) * 60 / 1000);
        String mcStr = String.format("%02d:%02d", mcHours, mcMins);
        long mcDay = fullTime / 24000L + 1;

        int tabsBottom = appY() + 17 + 13;
        int overworldLabelY = top + 22;
        float hourScale = 2.2f;
        int hourTextH = (int) (font.lineHeight * hourScale);
        int hourY = tabsBottom + Math.max(0, (overworldLabelY - tabsBottom - hourTextH) / 2);

        drawScaledCenteredString(g, mcStr, centerX, hourY, hourScale, textColor);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.clock.overworld_time"), centerX, overworldLabelY, subColor);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.clock.day_label", mcDay), centerX, top + 32, getThemeColor());

        g.fill(centerX - SCREEN_W / 2 + 10, top + 44, centerX + SCREEN_W / 2 - 10, top + 45, 0x44AAAAAA);

        LocalDateTime now = LocalDateTime.now();
        String localTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String localDateStr = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        g.drawCenteredString(font, PhoneLang.get("icraft.phone.clock.local_time"), centerX, top + 54, subColor);
        drawScaledCenteredString(g, localTimeStr, centerX, top + 64, 1.6f, getThemeColor());
        g.drawCenteredString(font, localDateStr, centerX, top + 88, subColor);
    }

    private void renderClockTabStopwatch(GuiGraphics g, int centerX, int textColor, int subColor) {
        int top = clockTabContentTop(CLOCK_TAB1_CONTENT_H);

        long elapsed = getStopwatchElapsedMs();
        long h = elapsed / 3_600_000L;
        long m = (elapsed / 60_000L) % 60;
        long s = (elapsed / 1000L) % 60;
        String text = h > 0
                ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);

        int accent = stopwatchRunning ? 0xFF4CAF50 : textColor;
        drawScaledCenteredString(g, text, centerX, top, 2.2f, accent);
        g.drawCenteredString(font, stopwatchRunning ? PhoneLang.get("icraft.phone.clock.stopwatch_running") : PhoneLang.get("icraft.phone.clock.stopwatch_stopped"), centerX, top + 22, subColor);
    }

    private void renderClockTabTimer(GuiGraphics g, int centerX, int textColor, int subColor) {
        int contentH = timerRunning ? CLOCK_TAB2_CONTENT_H_RUNNING : CLOCK_TAB2_CONTENT_H_IDLE;
        int top = clockTabContentTop(contentH);

        long remaining = timerRunning
                ? Math.max(0, timerEndAt - System.currentTimeMillis())
                : timerRemainingMs;
        long m = remaining / 60_000L;
        long s = (remaining / 1000L) % 60;
        String text = String.format("%02d:%02d", m, s);

        int color = timerFinished ? 0xFFFF5555
                : (timerRunning && remaining <= 10_000L) ? 0xFFFF9800
                : textColor;
        drawScaledCenteredString(g, text, centerX, top, 2.2f, color);

        String status = timerFinished ? PhoneLang.get("icraft.phone.clock.timer_done")
                : timerRunning ? PhoneLang.get("icraft.phone.clock.timer_counting") : PhoneLang.get("icraft.phone.clock.timer_ready");
        g.drawCenteredString(font, status, centerX, top + 22, timerFinished ? 0xFFFF5555 : subColor);
    }

    private void renderNotes(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFDE7;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF333333;
        int subColor  = phoneData.darkMode ? 0xFF888888 : 0xFF666666;

        blitIcon(g, 5, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.notes.title"), sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        renderNotesList(g, textColor, subColor);

        if (notePendingDeleteIndex >= 0) {
            renderNoteDeleteConfirm(g);
        }
    }

    private void renderNoteDeleteConfirm(GuiGraphics g) {
        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int menuH = 46;
        int menuTop = appY() + (APP_H - NAV_H - menuH) / 2;
        int menuBottom = menuTop + menuH;

        g.fill(menuX - 1, menuTop - 1, menuX + menuW + 1, menuBottom + 1, 0xFF333333);
        g.fill(menuX, menuTop, menuX + menuW, menuBottom, phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.notes.delete_confirm_title"),
                menuX + menuW / 2, menuTop + 6, phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222);

        int btnY = menuTop + 20;
        int btnH = 14;
        int btnGap = 4;
        int btnW = (menuW - btnGap) / 2;

        g.fill(menuX, btnY, menuX + btnW, btnY + btnH, 0xAA555555);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.common.cancel_btn"), menuX + btnW / 2, btnY + 3, 0xFFFFFFFF);

        int delX = menuX + btnW + btnGap;
        g.fill(delX, btnY, delX + btnW, btnY + btnH, 0xAAFF3333);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.notes.delete_confirm_yes"), delX + btnW / 2, btnY + 3, 0xFFFFFFFF);
    }

    private void renderNotesList(GuiGraphics g, int textColor, int subColor) {
        int noteMaxW = SCREEN_W - 16;
        List<PhoneData.Note> all = phoneData.notes;
        List<Integer> indices = getSortedNoteIndices();

        int y = appY() + 20;
        int bottomLimit = appBottom() - NAV_H - 20;
        for (int idx : indices) {
            if (y + 16 > bottomLimit) break;
            PhoneData.Note n = all.get(idx);

            int rowBg = phoneData.darkMode ? 0xFF16213E : 0xFFFFF9C4;
            g.fill(sx() + 1, y - 1, sx() + SCREEN_W - 1, y + 16, rowBg);

            String firstLine = n.text.split("\n", 2)[0];
            g.drawString(font, truncate(firstLine, noteMaxW), sx() + 3, y + 3, textColor, false);

            g.drawString(font, "X", sx() + SCREEN_W - 11, y + 3, 0xFFFF6666, false);

            y += 18;
        }

        if (indices.isEmpty()) {

            int emptyTop = appY() + 20;
            int emptyCenterY = emptyTop + (bottomLimit - emptyTop) / 2 - 4;
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.notes.empty_all"), sx() + SCREEN_W / 2, emptyCenterY, subColor);
        }
    }

    private int noteInputMaxY() {
        return appBottom() - NAV_H - (10 + 2 + 11);
    }

    private int noteInputY() {
        int textTop = noteTextTop(false);
        int committedH = wrapNoteText(noteDraftBuffer, SCREEN_W - 6).size() * (font.lineHeight + 2);
        return Math.min(textTop + committedH, noteInputMaxY());
    }

    private int noteTextTop(boolean viewMode) {
        return viewMode ? appY() + 19 : appY() + 29;
    }

    private int noteEditMaxScroll() {
        String fullText;
        int textBottom;
        if (noteViewMode) {
            fullText = (selectedNote >= 0 && selectedNote < phoneData.notes.size())
                    ? phoneData.notes.get(selectedNote).text : "";
            textBottom = appBottom() - NAV_H - 4;
        } else {
            fullText = noteDraftBuffer;
            textBottom = noteInputY() - 1;
        }
        int textTop = noteTextTop(noteViewMode);
        int maxW = SCREEN_W - 6;
        int totalH   = wrapNoteText(fullText, maxW).size() * (font.lineHeight + 2);
        int visibleH = Math.max(0, textBottom - textTop);
        return Math.max(0, totalH - visibleH);
    }

    private void renderNoteEdit(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFDE7;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF333333;
        int subColor  = phoneData.darkMode ? 0xFF888888 : 0xFF666666;

        g.drawString(font, PhoneLang.get("icraft.phone.notes.title"), sx() + 24, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        if (!noteViewMode) {
            renderNoteFormatToolbar(g, mx, my);
        }

        String fullText;
        int textBottom;
        if (noteViewMode) {
            fullText = (selectedNote >= 0 && selectedNote < phoneData.notes.size())
                    ? phoneData.notes.get(selectedNote).text : "";
            textBottom = appBottom() - NAV_H - 4;
        } else {
            fullText = noteDraftBuffer;
            textBottom = noteInputY() - 1;
        }

        int textTop = noteTextTop(noteViewMode);
        int maxW = SCREEN_W - 6;
        List<String> wrapped = wrapNoteText(fullText, maxW);

        boolean nothingWrittenYet = wrapped.isEmpty()
                && (noteViewMode || noteInput == null || noteInput.getValue().isEmpty());
        if (nothingWrittenYet) {
            if (!noteViewMode) {
                g.drawString(font, PhoneLang.get("icraft.phone.notes.new_note_status"), sx() + 3, textTop, subColor, false);
            }
            return;
        }

        int noteLineH = font.lineHeight + 2;
        int totalH   = wrapped.size() * noteLineH;
        int visibleH = Math.max(0, textBottom - textTop);
        int maxScroll = Math.max(0, totalH - visibleH);
        if (noteEditScrollOffset > maxScroll) noteEditScrollOffset = maxScroll;
        if (noteEditScrollOffset < 0) noteEditScrollOffset = 0;

        g.enableScissor(sx(), textTop, sx() + SCREEN_W, textBottom);
        int y = textTop - noteEditScrollOffset;
        for (String line : wrapped) {
            if (y + noteLineH >= textTop && y <= textBottom) {
                g.drawString(font, line, sx() + 3, y, textColor, false);
            }
            y += noteLineH;
        }
        g.disableScissor();
    }

    private String mapaError = null;

    private Screen xaeroGuiMap = null;

    private void initMapa() {
        mapaError   = null;
        xaeroGuiMap = null;
        destroyMapaTarget();

        try {
            ClassLoader cl = Minecraft.class.getClassLoader();

            Class<?> sessionClass = Class.forName("xaero.map.WorldMapSession", false, cl);
            Object session = sessionClass.getMethod("getCurrentSession").invoke(null);
            if (session == null) {
                mapaError = PhoneLang.get("icraft.phone.map.err_not_init");
                return;
            }

            Object processor = sessionClass.getMethod("getMapProcessor").invoke(session);
            if (processor == null) {
                mapaError = PhoneLang.get("icraft.phone.map.err_processor_not_ready");
                return;
            }

            Screen found = null;

            ICraftConstants.LOGGER.info("[iCraft/Mapa] Campos de {}: ", processor.getClass().getName());
            for (java.lang.reflect.Field f : processor.getClass().getDeclaredFields()) {
                ICraftConstants.LOGGER.info("  {} : {}", f.getName(), f.getType().getName());
                if (found == null && Screen.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(processor);
                    if (v != null) {
                        found = (Screen) v;
                        try {
                            found.init(Minecraft.getInstance(),
                                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                                Minecraft.getInstance().getWindow().getGuiScaledHeight());
                        } catch (Exception ignored) {}
                        ICraftConstants.LOGGER.info("[iCraft/Mapa] Usando campo '{}' = {}",
                            f.getName(), v.getClass().getName());
                    }
                }
            }

            if (found == null) {
                ICraftConstants.LOGGER.warn("[iCraft/Mapa] No se encontró campo Screen en MapProcessor. "
                    + "Intentando instanciar GuiMap(Screen, Screen, MapProcessor, Entity).");
                try {
                    Class<?> guiMapClass  = Class.forName("xaero.map.gui.GuiMap",   false, cl);
                    Class<?> mapProcClass = Class.forName("xaero.map.MapProcessor", false, cl);
                    java.lang.reflect.Constructor<?> ctor = guiMapClass.getDeclaredConstructor(
                        Screen.class, Screen.class, mapProcClass, net.minecraft.world.entity.Entity.class);
                    ctor.setAccessible(true);
                    net.minecraft.world.entity.Entity player = Minecraft.getInstance().player;
                    found = (Screen) ctor.newInstance(null, null, processor, player);
                    Minecraft mc2 = Minecraft.getInstance();
                    found.init(mc2,
                        mc2.getWindow().getGuiScaledWidth(),
                        mc2.getWindow().getGuiScaledHeight());
                    ICraftConstants.LOGGER.info("[iCraft/Mapa] GuiMap instanciada con (null, null, processor, player) OK.");
                } catch (Exception ex2) {
                    mapaError = PhoneLang.get("icraft.phone.map.err_guimap_unavailable", ex2.getClass().getSimpleName());
                    ICraftConstants.LOGGER.warn("[iCraft/Mapa] No se pudo instanciar GuiMap", ex2);
                    return;
                }
            }

            xaeroGuiMap = found;
            setXaeroZoom(2.88);

        } catch (ClassNotFoundException e) {
            mapaError = PhoneLang.get("icraft.phone.map.err_not_installed");
            ICraftConstants.LOGGER.info("[iCraft/Mapa] Xaero World Map no está en el classpath.");
        } catch (Exception e) {
            String msg = e.getMessage() != null
                ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 32))
                : "(sin mensaje)";
            mapaError = PhoneLang.get("icraft.phone.map.err_generic", e.getClass().getSimpleName(), msg);
            ICraftConstants.LOGGER.warn("[iCraft/Mapa] Error en initMapa()", e);
        }
    }

    private void destroyMapaTarget() {
    }

    private void setXaeroZoom(double zoom) {
        if (xaeroGuiMap == null) return;
        try {
            for (java.lang.reflect.Field f : xaeroGuiMap.getClass().getDeclaredFields()) {
                Class<?> t = f.getType();
                if (t != double.class && t != float.class) continue;
                f.setAccessible(true);
                double v = t == double.class ? f.getDouble(xaeroGuiMap) : f.getFloat(xaeroGuiMap);
                ICraftConstants.LOGGER.debug("[iCraft/Mapa] campo '{}' = {}", f.getName(), v);
                if (v > 0 && v < 1000) {
                    if (t == double.class) f.setDouble(xaeroGuiMap, zoom);
                    else                   f.setFloat(xaeroGuiMap,  (float) zoom);
                }
            }
            Class<?> sup = xaeroGuiMap.getClass().getSuperclass();
            while (sup != null && sup != Screen.class) {
                for (java.lang.reflect.Field f : sup.getDeclaredFields()) {
                    Class<?> t = f.getType();
                    if (t != double.class && t != float.class) continue;
                    f.setAccessible(true);
                    double v = t == double.class ? f.getDouble(xaeroGuiMap) : f.getFloat(xaeroGuiMap);
                    ICraftConstants.LOGGER.debug("[iCraft/Mapa] superclase campo '{}' = {}", f.getName(), v);
                    if (v > 0 && v < 1000) {
                        if (t == double.class) f.setDouble(xaeroGuiMap, zoom);
                        else                   f.setFloat(xaeroGuiMap,  (float) zoom);
                    }
                }
                sup = sup.getSuperclass();
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft/Mapa] No se pudo fijar zoom", e);
        }
    }

    private void renderMapa(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();

        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), 0xFF111111);
        blitIcon(g, 6, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.map.title"), sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        if (xaeroGuiMap == null || mapaError != null) {
            int centerX  = sx() + SCREEN_W / 2;
            int centerY  = appY() + APP_H / 2 - NAV_H / 2;
            int subColor = phoneData.darkMode ? 0xFF888888 : 0xFF666666;
            int txtColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222;
            if (mapaError != null) {
                String[] lines = mapaError.split("\\n");
                int lineH  = 10;
                int totalH = lines.length * lineH;
                int startY = centerY - totalH / 2;
                for (int i = 0; i < lines.length; i++) {
                    g.drawCenteredString(font, lines[i], centerX, startY + i * lineH,
                        i == 0 ? txtColor : subColor);
                }
                g.drawCenteredString(font, PhoneLang.get("icraft.phone.map.need_xaero_1"), centerX, startY + totalH + 8,  subColor);
                g.drawCenteredString(font, PhoneLang.get("icraft.phone.map.need_xaero_2"),    centerX, startY + totalH + 18, subColor);
                g.drawCenteredString(font, PhoneLang.get("icraft.phone.map.need_xaero_3"),    centerX, startY + totalH + 28, subColor);
            } else {
                g.drawCenteredString(font, PhoneLang.get("icraft.phone.map.loading"), centerX, centerY, subColor);
            }
            return;
        }

        final int HEADER_H = 15;
        int mapGX = sx();
        int mapGY = appY() + HEADER_H;
        int mapGW = SCREEN_W;
        int mapGH = APP_H - HEADER_H - NAV_H;
        if (mapGW <= 0 || mapGH <= 0) return;

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();

        final float ZOOM = 1.40f;
        float scaleX = (float) mapGW / guiW;
        float scaleY = (float) mapGH / guiH;
        float scale  = Math.max(scaleX, scaleY) * ZOOM;
        float offX   = mapGX + (mapGW - guiW * scale) / 2f;
        float offY   = mapGY + (mapGH - guiH * scale) / 2f;

        int xaeroMX = (int) ((mx - offX) / scale);
        int xaeroMY = (int) ((my - offY) / scale);

        g.flush();
        g.enableScissor(mapGX, mapGY, mapGX + mapGW, mapGY + mapGH);

        setXaeroZoom(2.88);

        g.pose().pushPose();
        g.pose().translate(offX, offY, 0f);
        g.pose().scale(scale, scale, 1f);

        try {
            xaeroGuiMap.render(g, xaeroMX, xaeroMY, lastDelta);
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft/Mapa] Error en render de GuiMap", e);
            mapaError = PhoneLang.get("icraft.phone.map.err_render", e.getClass().getSimpleName());
        }

        g.flush();
        g.pose().popPose();
        g.disableScissor();
    }

    private void renderSettings(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, PhoneLang.get("icraft.phone.settings.title"), sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);
    }

    private void renderSound(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + SCREEN_W / 2 - 8, appY() + 1, ICON_SIZE);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.settings.sound_title"), sx() + SCREEN_W / 2, appY() + 19, getThemeColor());
        g.fill(sx(), appY() + 30, sx() + SCREEN_W, appY() + 31, 0x44AAAAAA);
    }

    private void initWallpaperPicker() {
        clearWidgets();
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.SETTINGS);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        wallpaperScrollOffset = 0;
    }

    private static final int BASE_WALLPAPER_HEADER_H = 46;
    private static final int BASE_WALLPAPER_CELL_PAD = 4;
    private static final int BASE_WALLPAPER_LABEL_GAP = 8;
    private static final int BASE_WALLPAPER_LABEL_H = 10;

    private int wallpaperHeaderH() { return Math.round(BASE_WALLPAPER_HEADER_H * uiScale); }
    private int wallpaperCellPad()  { return Math.round(BASE_WALLPAPER_CELL_PAD * uiScale); }
    private int wallpaperLabelGap() { return Math.round(BASE_WALLPAPER_LABEL_GAP * uiScale); }
    private int wallpaperLabelH()   { return Math.round(BASE_WALLPAPER_LABEL_H * uiScale); }

    private int wallpaperThumbW() {
        int cellW = SCREEN_W / WALLPAPER_GRID_COLS;
        return cellW - wallpaperCellPad() * 2;
    }

    private int wallpaperThumbH() {
        return Math.round(wallpaperThumbW() * (WP_TEX_H / (float) WP_TEX_W));
    }

    private int wallpaperCellH() {
        return wallpaperThumbH() + wallpaperCellPad() * 2 + wallpaperLabelGap() + wallpaperLabelH();
    }

    private int wallpaperMaxScroll() {
        int rows = (int) Math.ceil(WALLPAPER_IDS.length / (double) WALLPAPER_GRID_COLS);
        int totalH = rows * wallpaperCellH();
        int visibleH = (appBottom() - NAV_H) - (appY() + wallpaperHeaderH());
        return Math.max(0, totalH - visibleH);
    }

    private void renderWallpaperPicker(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + SCREEN_W / 2 - 8, appY() + 2, ICON_SIZE);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.settings.wallpaper_title"),
                sx() + SCREEN_W / 2, appY() + 19, getThemeColor());
        g.fill(sx(), appY() + 30, sx() + SCREEN_W, appY() + 31, 0x44AAAAAA);

        int maxScroll = wallpaperMaxScroll();
        if (wallpaperScrollOffset > maxScroll) wallpaperScrollOffset = maxScroll;
        if (wallpaperScrollOffset < 0) wallpaperScrollOffset = 0;

        int cellW = SCREEN_W / WALLPAPER_GRID_COLS;
        int cellH = wallpaperCellH();
        int thumbW = wallpaperThumbW();
        int thumbH = wallpaperThumbH();

        int gridTop = appY() + wallpaperHeaderH();
        int gridBottom = appBottom() - NAV_H;

        g.enableScissor(sx(), gridTop, sx() + SCREEN_W, gridBottom);

        String[] names = wallpaperNames();
        for (int i = 0; i < WALLPAPER_IDS.length; i++) {
            int col = i % WALLPAPER_GRID_COLS;
            int row = i / WALLPAPER_GRID_COLS;
            int cellX = sx() + col * cellW;
            int cellY = gridTop + row * cellH - wallpaperScrollOffset;

            if (cellY + cellH < gridTop) continue;
            if (cellY > gridBottom) break;

            int thumbX = cellX + wallpaperCellPad();
            int thumbY = cellY + wallpaperCellPad();

            boolean selected = WALLPAPER_IDS[i].equals(phoneData.wallpaper);
            int borderColor = selected ? getThemeColor() : (phoneData.darkMode ? 0xFF444466 : 0xFFCCCCCC);
            int borderW = selected ? 2 : 1;
            g.fill(thumbX - borderW, thumbY - borderW, thumbX + thumbW + borderW, thumbY + thumbH + borderW, borderColor);
            blitWallpaperCover(g, WALLPAPER_LOCS[i], thumbX, thumbY, thumbW, thumbH);

            int labelColor = selected ? getThemeColor() : (phoneData.darkMode ? 0xFFCCCCCC : 0xFF444444);
            g.drawCenteredString(font, truncate(names[i], cellW - 4),
                    cellX + cellW / 2, thumbY + thumbH + wallpaperLabelGap(), labelColor);
        }

        g.disableScissor();
    }

    private boolean handleWallpaperClick(double mx, double my) {
        int gridTop = appY() + wallpaperHeaderH();
        int gridBottom = appBottom() - NAV_H;
        if (my < gridTop || my >= gridBottom) return false;

        int cellW = SCREEN_W / WALLPAPER_GRID_COLS;
        int cellH = wallpaperCellH();

        for (int i = 0; i < WALLPAPER_IDS.length; i++) {
            int col = i % WALLPAPER_GRID_COLS;
            int row = i / WALLPAPER_GRID_COLS;
            int cellX = sx() + col * cellW;
            int cellY = gridTop + row * cellH - wallpaperScrollOffset;

            if (mx >= cellX && mx < cellX + cellW && my >= cellY && my < cellY + cellH) {
                playClickSound();
                if (!WALLPAPER_IDS[i].equals(phoneData.wallpaper)) {
                    phoneData.wallpaper = WALLPAPER_IDS[i];
                    saveSettings();
                }
                return true;
            }
        }
        return false;
    }

    private static final int THEME_GRID_COLS = 5;
    private static final int BASE_THEME_HEADER_H = 44;
    private static final int BASE_THEME_SWATCH_GAP = 4;
    private static final int SWATCH_SELECT_PAD = 2;

    private int themeHeaderH()  { return Math.round(BASE_THEME_HEADER_H  * uiScale); }
    private int themeSwatchGap(){ return Math.round(BASE_THEME_SWATCH_GAP * uiScale); }

    private int themeSwatchW() {
        int margin = settingsSideMargin();
        int gap = themeSwatchGap();
        return (SCREEN_W - margin * 2 - (THEME_GRID_COLS - 1) * gap) / THEME_GRID_COLS;
    }
    private int themeRowH() { return themeSwatchW() + themeSwatchGap(); }

    private int themeMaxScroll() {
        int rows = (int) Math.ceil(THEMES.length / (double) THEME_GRID_COLS);
        int totalH = rows * themeRowH();
        int visibleH = (appBottom() - NAV_H) - (appY() + themeHeaderH() + SWATCH_SELECT_PAD);
        return Math.max(0, totalH - visibleH);
    }

    private void initTheme() {
        clearWidgets();
        addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.SETTINGS);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        themeScrollOffset = 0;
    }

    private void renderTheme(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + SCREEN_W / 2 - 8, appY() + 2, ICON_SIZE);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.settings.theme_title"),
                sx() + SCREEN_W / 2, appY() + 19, getThemeColor());
        g.fill(sx(), appY() + 30, sx() + SCREEN_W, appY() + 31, 0x44AAAAAA);

        int maxScroll = themeMaxScroll();
        if (themeScrollOffset > maxScroll) themeScrollOffset = maxScroll;
        if (themeScrollOffset < 0) themeScrollOffset = 0;

        int gridTop = appY() + themeHeaderH();
        int contentTop = gridTop + SWATCH_SELECT_PAD;
        int gridBottom = appBottom() - NAV_H;
        int marginX = sx() + settingsSideMargin();
        int swatchW = themeSwatchW();
        int gap = themeSwatchGap();
        int rowH = themeRowH();

        g.enableScissor(sx(), gridTop, sx() + SCREEN_W, gridBottom);
        for (int i = 0; i < THEMES.length; i++) {
            int col = i % THEME_GRID_COLS;
            int row = i / THEME_GRID_COLS;
            int bx = marginX + col * (swatchW + gap);
            int by = contentTop + row * rowH - themeScrollOffset;

            if (by + swatchW < gridTop) continue;
            if (by > gridBottom) break;

            boolean selected = THEMES[i].equals(phoneData.theme);
            int borderColor = selected ? 0xFFFFFFFF : 0x55000000;
            int borderW = selected ? 2 : 1;
            g.fill(bx - borderW, by - borderW, bx + swatchW + borderW, by + swatchW + borderW, borderColor);
            g.fill(bx, by, bx + swatchW, by + swatchW, THEME_COLORS[i]);
        }
        g.disableScissor();
    }

    private boolean handleThemeClick(double mx, double my) {
        int gridTop = appY() + themeHeaderH();
        int contentTop = gridTop + SWATCH_SELECT_PAD;
        int gridBottom = appBottom() - NAV_H;
        if (my < gridTop || my >= gridBottom) return false;

        int marginX = sx() + settingsSideMargin();
        int swatchW = themeSwatchW();
        int gap = themeSwatchGap();
        int rowH = themeRowH();

        for (int i = 0; i < THEMES.length; i++) {
            int col = i % THEME_GRID_COLS;
            int row = i / THEME_GRID_COLS;
            int bx = marginX + col * (swatchW + gap);
            int by = contentTop + row * rowH - themeScrollOffset;

            if (mx >= bx && mx < bx + swatchW && my >= by && my < by + swatchW) {
                playClickSound();
                if (!THEMES[i].equals(phoneData.theme)) {
                    phoneData.theme = THEMES[i];
                    saveSettings();
                }
                return true;
            }
        }
        return false;
    }

    private void renderPrivacy(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + SCREEN_W / 2 - 8, appY() + 1, ICON_SIZE);
        g.drawCenteredString(font, PhoneLang.get("icraft.phone.settings.privacy_btn"), sx() + SCREEN_W / 2, appY() + 19, getThemeColor());
        g.fill(sx(), appY() + 30, sx() + SCREEN_W, appY() + 31, 0x44AAAAAA);

        int textColor = phoneData.darkMode ? 0xFFDDDDDD : 0xFF333333;
        int labelColor = getThemeColor();
        int maxTextW = SCREEN_W - 10;
        int textX = sx() + 5;

        int contentTop = appY() + 36;
        int contentBottom = appBottom() - NAV_H;

        String[] paragraphs = {
                PhoneLang.get("icraft.phone.privacy.para1"),
                PhoneLang.get("icraft.phone.privacy.para2"),
                PhoneLang.get("icraft.phone.privacy.para3"),
        };

        record Line(net.minecraft.util.FormattedCharSequence text, boolean isLabel, int gapAfter) {}
        List<Line> lines = new ArrayList<>();

        lines.add(new Line(wrapMessageLines("iCraft", maxTextW).get(0), true, 10));
        lines.add(new Line(wrapMessageLines("Version 0.2.1", maxTextW).get(0), false, 14));

        lines.add(new Line(wrapMessageLines(PhoneLang.get("icraft.phone.privacy.your_data_heading"), maxTextW).get(0), true, 10));
        for (String para : paragraphs) {
            var wrapped = wrapMessageLines(para, maxTextW);
            for (int i = 0; i < wrapped.size(); i++) {
                boolean lastOfPara = i == wrapped.size() - 1;
                lines.add(new Line(wrapped.get(i), false, lastOfPara ? 13 : 10));
            }
        }

        int totalH = 0;
        for (Line l : lines) totalH += l.gapAfter();

        int visibleH = contentBottom - contentTop;
        int maxScroll = Math.max(0, totalH - visibleH);
        if (privacyScrollOffset > maxScroll) privacyScrollOffset = maxScroll;
        if (privacyScrollOffset < 0) privacyScrollOffset = 0;

        int y = contentTop - privacyScrollOffset;
        for (Line l : lines) {
            if (y + 9 >= contentTop && y <= contentBottom) {
                g.drawString(font, l.text(), textX, y, l.isLabel() ? labelColor : textColor, false);
            }
            y += l.gapAfter();
        }

        if (maxScroll > 0) {
            float ratio = (float) privacyScrollOffset / maxScroll;
            int barTrackH = contentBottom - contentTop;
            int barH = Math.max(10, barTrackH * visibleH / totalH);
            int barY = contentTop + (int) ((barTrackH - barH) * ratio);
            g.fill(sx() + SCREEN_W - 3, contentTop, sx() + SCREEN_W - 1, contentBottom, 0x22AAAAAA);
            g.fill(sx() + SCREEN_W - 3, barY, sx() + SCREEN_W - 1, barY + barH, 0x88AAAAAA);
        }
    }

    private int editingIconIndex = -1;
    private int iconGridStartY, iconGridCellW, iconGridCellH;
    private static final int WALLPAPER_GRID_COLS = 2;

    private static final int[] ICON_COLOR_PALETTE = java.util.Arrays.copyOfRange(THEME_COLORS, 1, THEME_COLORS.length);
    private int colorSwatchX, colorSwatchY, colorSwatchW, colorSwatchH, colorSwatchGap, colorSwatchCols;
    private boolean colorSwatchVisible = false;

    private int iconColorMaxScroll() {
        if (!colorSwatchVisible) return 0;
        int rows = (int) Math.ceil(ICON_COLOR_PALETTE.length / (double) colorSwatchCols);
        int totalH = rows * (colorSwatchH + colorSwatchGap) - colorSwatchGap;
        int visibleH = (appBottom() - NAV_H) - (colorSwatchY + SWATCH_SELECT_PAD);
        return Math.max(0, totalH - visibleH);
    }

    private void initIconEditor() {
        clearWidgets();

        if (editingIconIndex >= 0 && editingIconIndex < phoneData.appIconLabels.length) {
            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal("<"), b -> {
                editingIconIndex = -1;
                initIconEditor();
            }).pos(sx() + 2, appY() + 2).size(18, 10).build());

            int avatarBottom = appY() + 68;
            int colorLabelY = avatarBottom + 8;

            colorSwatchVisible = true;
            iconColorScrollOffset = 0;
            int colCols = 6;
            int colRows = (int) Math.ceil(ICON_COLOR_PALETTE.length / (double) colCols);
            int colGap = 3;
            int cy = colorLabelY + 12;
            int cx = sx() + 5;
            int colBtnW = (SCREEN_W - 10 - (colCols - 1) * colGap) / colCols;
            int colBtnH = colBtnW;

            colorSwatchX = cx;
            colorSwatchY = cy;
            colorSwatchW = colBtnW;
            colorSwatchH = colBtnH;
            colorSwatchGap = colGap;
            colorSwatchCols = colCols;

        } else {
            colorSwatchVisible = false;
            int cols = 3;
            int headerH = 30;
            int bottomBarH = 30;
            int rows = (int) Math.ceil(phoneData.appIconLabels.length / (double) cols);
            int usableH = APP_H - NAV_H;
            int gridH = Math.max(rows * 30, usableH - headerH - bottomBarH);
            int cellW = SCREEN_W / cols;
            int cellH = gridH / rows;
            int startY = appY() + headerH;

            iconGridStartY = startY;
            iconGridCellW  = cellW;
            iconGridCellH  = cellH;

            for (int i = 0; i < phoneData.appIconLabels.length; i++) {
                final int idx = i;
                int col = i % cols;
                int row = i / cols;
                int bx = sx() + col * cellW + 2;
                int by = startY + row * cellH + 2;

                addRenderableWidget(PhoneButton.phoneBuilder(
                        Component.literal(effectiveAppLabel(i)),
                        b -> {
                            editingIconIndex = idx;
                            initIconEditor();
                        }
                ).pos(bx, by).size(cellW - 4, cellH - 4).build());
            }

            int bottomY = startY + rows * cellH + 4;
            int resetW = Math.min(130, SCREEN_W - 10);
            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.icons.reset_all_btn")), b -> {
                phoneData.resetIconDefaults();
                initIconEditor();
            }).pos(sx() + (SCREEN_W - resetW) / 2, bottomY).size(resetW, 12).build());

            int backW = 60;
            addRenderableWidget(PhoneButton.phoneBuilder(Component.literal(PhoneLang.get("icraft.phone.common.back_btn2")), b -> {
                if (!goBack()) goToApp(App.SETTINGS);
            }).pos(sx() + (SCREEN_W - backW) / 2, bottomY + 15).size(backW, 11).build());
        }
    }
    private void renderIconEditor(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + SCREEN_W / 2 - 8, appY() + 2, ICON_SIZE);

        if (editingIconIndex >= 0 && editingIconIndex < phoneData.appIconLabels.length) {
            String appName = effectiveAppLabel(editingIconIndex);
            int    color   = getEffectiveAppColor(editingIconIndex);

            g.drawCenteredString(font, truncate(PhoneLang.get("icraft.phone.icons.editing_label", appName), SCREEN_W - 12),
                    sx() + SCREEN_W / 2, appY() + 18, getThemeColor());

            int px = sx() + SCREEN_W / 2 - 18;
            int py = appY() + 32;
            g.fill(px - 1, py - 1, px + 37, py + 37, 0x55FFFFFF);
            g.fill(px, py, px + 36, py + 36, color);
            int previewIconSize = 22;
            blitIcon(g, editingIconIndex, 0, px + 18 - previewIconSize / 2, py + 18 - previewIconSize / 2, previewIconSize);

            g.drawString(font, PhoneLang.get("icraft.phone.icons.color_label"), sx() + 5, py + 44, 0xFFAAAAAA, false);

            if (colorSwatchVisible) {
                int gridBottom = appBottom() - NAV_H;
                int contentTop = colorSwatchY + SWATCH_SELECT_PAD;
                int maxScroll = iconColorMaxScroll();
                if (iconColorScrollOffset > maxScroll) iconColorScrollOffset = maxScroll;
                if (iconColorScrollOffset < 0) iconColorScrollOffset = 0;

                g.enableScissor(sx(), colorSwatchY, sx() + SCREEN_W, gridBottom);
                for (int c = 0; c < ICON_COLOR_PALETTE.length; c++) {
                    int col = c % colorSwatchCols;
                    int row = c / colorSwatchCols;
                    int bx = colorSwatchX + col * (colorSwatchW + colorSwatchGap);
                    int by = contentTop + row * (colorSwatchH + colorSwatchGap) - iconColorScrollOffset;

                    if (by + colorSwatchH < colorSwatchY) continue;
                    if (by > gridBottom) break;

                    int swatchColor = ICON_COLOR_PALETTE[c];

                    boolean isCurrent = (swatchColor | 0xFF000000) == (color | 0xFF000000);
                    int borderColor = isCurrent ? 0xFFFFFFFF : 0x55000000;
                    int borderW = isCurrent ? 2 : 1;

                    g.fill(bx - borderW, by - borderW, bx + colorSwatchW + borderW, by + colorSwatchH + borderW, borderColor);
                    g.fill(bx, by, bx + colorSwatchW, by + colorSwatchH, swatchColor);
                }
                g.disableScissor();
            }

        } else {
            g.drawCenteredString(font, PhoneLang.get("icraft.phone.settings.icon_editor_btn"), sx() + SCREEN_W / 2, appY() + 20, getThemeColor());

            int cols = 3;
            for (int i = 0; i < phoneData.appIconColors.length; i++) {
                int col = i % cols;
                int row = i / cols;
                int bx = sx() + col * iconGridCellW + 2;
                int by = iconGridStartY + row * iconGridCellH + 2;
                g.fill(bx, by, bx + iconGridCellW - 4, by + iconGridCellH - 4, getEffectiveAppColor(i));
            }
        }
    }

    private void renderNavBar(GuiGraphics g, int mx, int my) {
        int navY = appBottom() - NAV_H + 1;
        int navBg = phoneData.darkMode ? 0xFF16213E : 0xFFDDDDDD;
        g.fill(sx(), navY, sx() + SCREEN_W, navY + NAV_H - 1, navBg);

        int[] navIconCols = {0, 1, 2, 3};
        String[] navKeys  = {"HOME", "CHAT", "CAMERA", "SETTINGS"};

        int itemW = SCREEN_W / navKeys.length;
        int iconY = navY + (NAV_H - ICON_SIZE) / 2;
        for (int i = 0; i < navKeys.length; i++) {
            int nx = sx() + i * itemW + itemW / 2;
            boolean active = currentApp.name().startsWith(navKeys[i]);

            if (active) {
                g.fill(sx() + i * itemW + 3, navY, sx() + (i + 1) * itemW - 3, navY + 2, getThemeColor());
            }

            blitIcon(g, navIconCols[i], 1, nx - ICON_SIZE / 2, iconY, ICON_SIZE);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (cameraLayout) {
            int delta = scrollY > 0 ? -2 : 2;
            cameraFov = Math.max(CAM_FOV_MIN, Math.min(CAM_FOV_MAX, cameraFov + delta));
            return true;
        }
        if (isCallLocked() && currentApp == App.CONTACTS) {
            return true;
        }
        int delta = (int)(scrollY * 10);
        if (currentApp == App.CHAT) {
            chatListScrollOffset = Math.max(0, chatListScrollOffset - delta);
        } else if (currentApp == App.CHAT_CONV) {
            chatConvScrollOffset = Math.max(0, chatConvScrollOffset - delta);
        } else if (currentApp == App.CONTACTS && contactOptionsFor == null) {
            contactsScrollOffset = Math.max(0, contactsScrollOffset - delta);
        } else if (currentApp == App.PRIVACY) {
            privacyScrollOffset = Math.max(0, privacyScrollOffset - delta);
        } else if (currentApp == App.WALLPAPER) {
            wallpaperScrollOffset = Math.max(0, Math.min(wallpaperMaxScroll(), wallpaperScrollOffset - delta));
        } else if (currentApp == App.THEME) {
            themeScrollOffset = Math.max(0, Math.min(themeMaxScroll(), themeScrollOffset - delta));
        } else if (currentApp == App.ICON_EDITOR) {
            iconColorScrollOffset = Math.max(0, Math.min(iconColorMaxScroll(), iconColorScrollOffset - delta));
        } else if (currentApp == App.NOTE_EDIT) {
            noteEditScrollOffset = Math.max(0, Math.min(noteEditMaxScroll(), noteEditScrollOffset - delta));
        } else if (currentApp == App.PHOTOS && photoViewerIndex < 0) {
            photosScrollOffset = Math.max(0, photosScrollOffset - delta);
        } else if (currentApp == App.MAPA && xaeroGuiMap != null) {
            Minecraft mc2s = Minecraft.getInstance();
            int guiW2 = mc2s.getWindow().getGuiScaledWidth();
            int guiH2 = mc2s.getWindow().getGuiScaledHeight();
            final int HEADER_H2 = 15;
            int mGX = sx(), mGY = appY() + HEADER_H2, mGW = SCREEN_W, mGH = APP_H - HEADER_H2 - NAV_H;
            float sX2 = (float) mGW / guiW2, sY2 = (float) mGH / guiH2;
            float sc2 = Math.max(sX2, sY2) * 1.40f;
            float oX2 = mGX + (mGW - guiW2 * sc2) / 2f, oY2 = mGY + (mGH - guiH2 * sc2) / 2f;
            xaeroGuiMap.mouseScrolled((mouseX - oX2) / sc2, (mouseY - oY2) / sc2, scrollX, scrollY);
            setXaeroZoom(2.88);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentApp == App.NOTE_EDIT) {
            ICraftConstants.LOGGER.info("[iCraft-DEBUG] mouseClicked x={} y={} button={} noteInput={} noteInputBounds={}",
                    mouseX, mouseY, button, noteInput,
                    noteInput != null ? (noteInput.getX() + "," + noteInput.getY() + "," + noteInput.getWidth() + "," + noteInput.getHeight()) : "null");
        }
        if (cameraLayout) {
            return true;
        }

        if (locked) {
            int[] btn = lockUnlockButtonRect();
            if (mouseX >= btn[0] && mouseX < btn[0] + btn[2]
                    && mouseY >= btn[1] && mouseY < btn[1] + btn[3]) {
                playClickSound();
                locked = false;
                currentApp = App.HOME;
                appHistory.clear();
                initCurrentApp();
            }
            return true;
        }

        double relX = mouseX - sx();
        double relY = mouseY - appY();

        int navY = appBottom() - NAV_H + 1 - appY();
        boolean onNavBar = relY > navY && relY < navY + NAV_H && relX >= 0 && relX < SCREEN_W;

        if (onNavBar) {
            int itemW = SCREEN_W / 4;
            int item = (int)(relX / itemW);
            App[] navApps = {App.HOME, App.CHAT, App.CAMERA, App.SETTINGS};
            if (item >= 0 && item < navApps.length) {
                playClickSound();
                appHistory.clear();
                currentApp = navApps[item];
                initCurrentApp();
                return true;
            }
        }

        if (isCallLocked() && currentApp == App.CONTACTS) {

            return handleCallScreenClick(mouseX, mouseY);
        }

        if (currentApp == App.HOME) {
            return handleHomeClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.CHAT) {
            return handleChatClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.CHAT_CONV) {
            return handleChatConvClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.PHOTOS) {
            return handlePhotosClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.CONTACTS) {
            return handleContactsClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.NOTES) {
            return handleNotesClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.ICON_EDITOR) {
            return handleIconEditorClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.WALLPAPER) {
            return handleWallpaperClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.THEME) {
            return handleThemeClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.CREATE_GROUP) {
            return handleCreateGroupClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.NOTE_EDIT) {
            return handleNoteFormatToolbarClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (currentApp == App.MAPA && xaeroGuiMap != null) {
            Minecraft mc2 = Minecraft.getInstance();
            int guiW2 = mc2.getWindow().getGuiScaledWidth();
            int guiH2 = mc2.getWindow().getGuiScaledHeight();
            final int HEADER_H2 = 15;
            int mGX = sx(), mGY = appY() + HEADER_H2, mGW = SCREEN_W, mGH = APP_H - HEADER_H2 - NAV_H;
            float sX2 = (float) mGW / guiW2, sY2 = (float) mGH / guiH2;
            float sc2 = Math.max(sX2, sY2) * 1.40f;
            float oX2 = mGX + (mGW - guiW2 * sc2) / 2f, oY2 = mGY + (mGH - guiH2 * sc2) / 2f;
            return xaeroGuiMap.mouseClicked((mouseX - oX2) / sc2, (mouseY - oY2) / sc2, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleIconEditorClick(double mx, double my) {
        if (!colorSwatchVisible || editingIconIndex < 0 || editingIconIndex >= phoneData.appIconLabels.length) {
            return false;
        }
        int gridBottom = appBottom() - NAV_H;
        int contentTop = colorSwatchY + SWATCH_SELECT_PAD;
        if (my < colorSwatchY || my >= gridBottom) return false;

        for (int c = 0; c < ICON_COLOR_PALETTE.length; c++) {
            int col = c % colorSwatchCols;
            int row = c / colorSwatchCols;
            int bx = colorSwatchX + col * (colorSwatchW + colorSwatchGap);
            int by = contentTop + row * (colorSwatchH + colorSwatchGap) - iconColorScrollOffset;
            if (mx >= bx && mx < bx + colorSwatchW && my >= by && my < by + colorSwatchH) {
                playClickSound();
                phoneData.appIconColors[editingIconIndex] = ICON_COLOR_PALETTE[c];
                saveSettings();
                return true;
            }
        }
        return false;
    }

    private boolean handleHomeClick(double mx, double my) {
        int cols = 3;
        int cellW = SCREEN_W / cols;
        int cellH = 55;
        int startY = appY() + 5;

        App[] gridApps = {
            App.CHAT, App.CAMERA, App.PHOTOS,
            App.WEATHER, App.CLOCK, App.NOTES,
            App.MAPA, App.SETTINGS, App.CONTACTS
        };

        for (int i = 0; i < gridApps.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = sx() + col * cellW;
            int cy = startY + row * cellH;

            if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH) {
                playClickSound();
                goToApp(gridApps[i]);
                return true;
            }
        }
        return false;
    }

    private boolean handleChatConvClick(double mx, double my) {
        if (!chatAtBottom) {
            int[] bb = scrollToBottomBtnBounds();
            if (mx >= bb[0] && mx < bb[0] + bb[2] && my >= bb[1] && my < bb[1] + bb[3]) {
                chatConvScrollOffset = Integer.MAX_VALUE;
                chatAtBottom = true;
                return true;
            }
        }
        return false;
    }

    private boolean handleChatClick(double mx, double my) {
        int y = appY() + 16;
        for (PhoneData.ChatConversation conv : phoneData.conversations) {
            if (my >= y - 1 && my < y + 21 && mx >= sx() + 1 && mx < sx() + SCREEN_W - 1) {
                currentConv = conv;
                chatConvScrollOffset = Integer.MAX_VALUE;
                chatAtBottom = true;
                goToApp(App.CHAT_CONV);
                return true;
            }
            y += 22;
        }
        return false;
    }

    private boolean handlePhotosClick(double mx, double my) {
        if (photoViewerIndex >= 0) {
            if (photoDeleteConfirmOpen) {
                return handlePhotoDeleteConfirmClick(mx, my);
            }

            if (photoShareMenuOpen && photoShareIndex == photoViewerIndex) {
                int menuX = sx() + 5;
                int menuW = SCREEN_W - 10;
                int rowH = 18;
                List<PhoneData.ChatConversation> shareableConvs = phoneData.conversations.stream()
                        .filter(c -> GlobalImagesState.isEnabled()
                                || !com.icraft.server.PhoneServerHandler.GLOBAL_GROUP_ID.equals(c.id))
                        .toList();
                int maxRows = Math.min(shareableConvs.size(), 6);
                int titleH = 17;
                int cancelH = 14;
                int cancelGap = 6;
                int menuH = titleH + maxRows * rowH + cancelGap + cancelH;
                int contentTop = appY() + 14;
                int contentBottom = appBottom() - NAV_H;
                int menuTop = contentTop + Math.max(0, (contentBottom - contentTop - menuH) / 2);
                int menuBottom = menuTop + menuH;

                int cancelY = menuBottom - cancelH;
                if (my >= cancelY && my <= menuBottom && mx >= menuX && mx < menuX + menuW) {
                    photoShareMenuOpen = false;
                    return true;
                }
                int y = menuTop + titleH;
                int shown = 0;
                for (PhoneData.ChatConversation conv : shareableConvs) {
                    if (shown >= maxRows) break;
                    if (my >= y && my < y + rowH && mx >= menuX && mx < menuX + menuW) {
                        PhoneData.PhotoEntry photo = phoneData.photos.get(photoViewerIndex);
                        sharePhotoToConversation(conv, photo.filename);
                        return true;
                    }
                    y += rowH;
                    shown++;
                }
                photoShareMenuOpen = false;
                return true;
            }

            String delIcon = "🗑";
            int delBtnW = font.width(delIcon) + 6;
            int delBtnX = sx() + SCREEN_W - delBtnW;
            if (my >= appY() && my < appY() + 14 && mx >= delBtnX && mx < delBtnX + delBtnW) {
                photoDeleteConfirmOpen = true;
                playClickSound();
                return true;
            }

            if (my >= appY() && my < appY() + 14 && mx >= sx() + 3 && mx < sx() + 60) {
                photoShareMenuOpen = false;
                photoViewerIndex = -1;
                return true;
            }

            int contentBottom2 = appBottom() - NAV_H;
            int navBtnH2 = 14, navBtnGap2 = 4;
            int navAreaY2 = contentBottom2 - navBtnH2 - navBtnGap2;
            int shareBtnH2 = 14, shareBtnGap2 = 3;
            int shareAreaY2 = navAreaY2 - shareBtnH2 - shareBtnGap2;
            int shareW = SCREEN_W - 10;
            int shareBtnX = sx() + 5;

            if (my >= shareAreaY2 && my < shareAreaY2 + shareBtnH2 && mx >= shareBtnX && mx < shareBtnX + shareW) {
                photoShareMenuOpen = !photoShareMenuOpen;
                photoShareIndex = photoViewerIndex;
                return true;
            }

            if (phoneData.photos.size() > 1) {
                int navW = 26, navGap = 14;
                int navPairW = navW * 2 + navGap;
                int navX = sx() + (SCREEN_W - navPairW) / 2;
                int navY = navAreaY2;
                int prevX = navX;
                int nextX = navX + navW + navGap;

                boolean canPrev = photoViewerIndex > 0;
                boolean canNext = photoViewerIndex < phoneData.photos.size() - 1;

                if (canPrev && mx >= prevX && mx < prevX + navW && my >= navY && my < navY + navBtnH2) {
                    photoShareMenuOpen = false;
                    photoViewerIndex--;
                    return true;
                }
                if (canNext && mx >= nextX && mx < nextX + navW && my >= navY && my < navY + navBtnH2) {
                    photoShareMenuOpen = false;
                    photoViewerIndex++;
                    return true;
                }
            }
            return true;
        }

        int cols = 3;
        int cellSize = (SCREEN_W - 6) / cols;
        int headerH = 18;
        int gridStartY = appY() + headerH;
        int contentBottom = appBottom() - NAV_H;

        for (int i = 0; i < phoneData.photos.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int px = sx() + 3 + col * cellSize;
            int py = gridStartY + row * cellSize - photosScrollOffset;

            if (mx >= px && mx < px + cellSize - 2 && my >= py && my < py + cellSize - 2
                    && py >= gridStartY && py + cellSize - 2 <= contentBottom) {
                photoViewerIndex = i;
                return true;
            }
        }
        return false;
    }

    private boolean handlePhotoDeleteConfirmClick(double mx, double my) {
        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int menuH = 46;
        int headerH = 14;
        int coverTop = appY() + headerH;
        int coverBottom = appBottom() - NAV_H;
        int menuTop = coverTop + (coverBottom - coverTop - menuH) / 2;

        int btnY = menuTop + 20;
        int btnH = 14;
        int btnGap = 4;
        int btnW = (menuW - btnGap) / 2;
        int delX = menuX + btnW + btnGap;

        if (my >= btnY && my < btnY + btnH) {
            if (mx >= menuX && mx < menuX + btnW) {

                photoDeleteConfirmOpen = false;
                playClickSound();
                return true;
            }
            if (mx >= delX && mx < delX + btnW) {

                deleteCurrentViewerPhoto();
                photoDeleteConfirmOpen = false;
                playClickSound();
                return true;
            }
        }
        return true;
    }

    private void deleteCurrentViewerPhoto() {
        if (photoViewerIndex < 0 || photoViewerIndex >= phoneData.photos.size()) return;
        PhoneData.PhotoEntry photo = phoneData.photos.get(photoViewerIndex);
        String filename = photo.filename;

        try {
            Path photoFile = getPhotosDir().resolve(filename);
            Files.deleteIfExists(photoFile);
            Path metaFile = getPhotosDir().resolve(filename.replace(".png", ".meta"));
            Files.deleteIfExists(metaFile);
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudo borrar el archivo de la foto \"{}\": {}", filename, e.getMessage());
        }

        ResourceLocation cachedTex = photoTextureCache.remove(filename);
        if (cachedTex != null) {
            Minecraft.getInstance().getTextureManager().release(cachedTex);
        }
        DynamicTexture dynTex = photoTextures.remove(filename);
        if (dynTex != null) {
            dynTex.close();
        }
        photoDimsCache.remove(filename);

        phoneData.photos.remove(photoViewerIndex);
        photoShareMenuOpen = false;

        if (phoneData.photos.isEmpty()) {
            photoViewerIndex = -1;
        } else if (photoViewerIndex >= phoneData.photos.size()) {
            photoViewerIndex = phoneData.photos.size() - 1;
        }

        notifications.add(PhoneLang.get("icraft.phone.photos.deleted_notif"));
    }

    private boolean handleNotesClick(double mx, double my) {
        if (notePendingDeleteIndex >= 0) {
            return handleNoteDeleteConfirmClick(mx, my);
        }

        List<Integer> indices = getSortedNoteIndices();
        int y = appY() + 20;
        for (int idx : indices) {
            if (my >= y - 1 && my < y + 17) {
                if (mx >= sx() + SCREEN_W - 15) {
                    notePendingDeleteIndex = idx;
                    playClickSound();
                    return true;
                }
                selectedNote = idx;
                noteViewMode = true;
                noteEditScrollOffset = 0;
                goToApp(App.NOTE_EDIT);
                return true;
            }
            y += 18;
        }
        return false;
    }

    private boolean handleNoteDeleteConfirmClick(double mx, double my) {
        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int menuH = 46;
        int menuTop = appY() + (APP_H - NAV_H - menuH) / 2;

        int btnY = menuTop + 20;
        int btnH = 14;
        int btnGap = 4;
        int btnW = (menuW - btnGap) / 2;
        int delX = menuX + btnW + btnGap;

        if (my >= btnY && my < btnY + btnH) {
            if (mx >= menuX && mx < menuX + btnW) {

                notePendingDeleteIndex = -1;
                playClickSound();
                return true;
            }
            if (mx >= delX && mx < delX + btnW) {

                if (notePendingDeleteIndex >= 0 && notePendingDeleteIndex < phoneData.notes.size()) {
                    phoneData.notes.remove(notePendingDeleteIndex);
                }
                notePendingDeleteIndex = -1;
                playClickSound();
                return true;
            }
        }
        return true;
    }

    private static volatile PendingPhoto pendingPhoto = null;

    public static volatile boolean suppressRender = false;

    private static class PendingPhoto {
        final String filename;
        final int wx, wy, wz;
        final String dim;
        final String filter;
        final boolean selfie;
        final int fbLeft, fbTop, fbRight, fbBottom;
        PendingPhoto(String filename, int wx, int wy, int wz, String dim, String filter, boolean selfie,
                     int fbLeft, int fbTop, int fbRight, int fbBottom) {
            this.filename = filename; this.wx = wx; this.wy = wy; this.wz = wz;
            this.dim = dim; this.filter = filter; this.selfie = selfie;
            this.fbLeft = fbLeft; this.fbTop = fbTop; this.fbRight = fbRight; this.fbBottom = fbBottom;
        }
    }

    private void capturePhoto() {
        if (Minecraft.getInstance().player == null) return;
        var pos = Minecraft.getInstance().player.blockPosition();
        String dim = "overworld";
        if (Minecraft.getInstance().level != null) {
            dim = Minecraft.getInstance().level.dimension().location().getPath();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
        String filename = "photo_" + timestamp + ".png";

        int fbLeft = -1, fbTop = -1, fbRight = -1, fbBottom = -1;
        try {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            int[] vp = cameraViewportRect();
            int vpLeft   = vp[0];
            int vpTop    = vp[1];
            int vpRight  = vp[2];
            int vpBottom = vp[3];
            fbLeft   = (int)(vpLeft   * guiScale);
            fbTop    = (int)(vpTop    * guiScale);
            fbRight  = (int)(vpRight  * guiScale);
            fbBottom = (int)(vpBottom * guiScale);
        } catch (Exception ignored) {  }

        pendingCaptureFov = cameraFov;
        captureFovActive  = true;

        suppressRender = true;

        pendingPhoto = new PendingPhoto(filename,
                pos.getX(), pos.getY(), pos.getZ(), dim,
                selectedFilter, selfieMode,
                fbLeft, fbTop, fbRight, fbBottom);
    }

    private static void applyFilterToImage(NativeImage img, String filter) {
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = img.getPixelRGBA(x, y);
                int a =  (abgr >> 24) & 0xFF;
                int b =  (abgr >> 16) & 0xFF;
                int g2 = (abgr >>  8) & 0xFF;
                int r =  (abgr)       & 0xFF;

                int lum = (int)(r * 0.299 + g2 * 0.587 + b * 0.114);

                switch (filter) {
                    case "sepia" -> {
                        r  = Math.min(255, (int)(lum * 1.08 + 35));
                        g2 = Math.min(255, (int)(lum * 0.86 + 10));
                        b  = Math.min(255, (int)(lum * 0.55));
                    }
                    case "vivid" -> {
                        r  = clamp((int)(r  * 1.2 - 20));
                        g2 = clamp((int)(g2 * 1.3 - 20));
                        b  = clamp((int)(b  * 1.1 - 10));
                    }
                    case "cool" -> {
                        r  = clamp(r  - 25);
                        g2 = clamp(g2 - 5);
                        b  = clamp(b  + 40);
                    }
                    case "warm" -> {
                        r  = clamp(r  + 45);
                        g2 = clamp(g2 + 10);
                        b  = clamp(b  - 25);
                    }
                    case "noir" -> {
                        r = lum; g2 = lum; b = lum;
                        int c = clamp((int)((lum - 128) * 1.4 + 128));
                        r = c; g2 = c; b = c;
                    }
                    case "retro" -> {
                        r  = clamp((int)(r  * 0.85 + 30));
                        g2 = clamp((int)(g2 * 0.75 + 20));
                        b  = clamp((int)(b  * 0.55));
                    }
                    case "fade" -> {
                        int grey = (int)(lum * 0.7 + 128 * 0.3);
                        r  = clamp((int)(r  * 0.55 + grey * 0.45));
                        g2 = clamp((int)(g2 * 0.55 + grey * 0.45));
                        b  = clamp((int)(b  * 0.55 + grey * 0.45));
                    }
                    case "creeper" -> {
                        int blockSize = Math.max(3, Math.min(16, Math.min(w, h) / 32));
                        int ax = (x / blockSize) * blockSize;
                        int ay = (y / blockSize) * blockSize;
                        if (ax == x && ay == y) {
                            int g_mono = (int) (r * 0.1 + g2 * 0.8 + b * 0.1);
                            g_mono = (g_mono / 48) * 48;
                            r  = clamp((int) (g_mono * 0.15));
                            g2 = clamp((int) (g_mono * 1.35));
                            b  = clamp((int) (g_mono * 0.10));
                        } else {
                            int anchorAbgr = img.getPixelRGBA(ax, ay);
                            r  = anchorAbgr        & 0xFF;
                            g2 = (anchorAbgr >> 8)  & 0xFF;
                            b  = (anchorAbgr >> 16) & 0xFF;
                        }
                    }
                    case "enderman" -> {
                        r  = clamp(255 - r);
                        g2 = clamp(255 - g2);
                        b  = clamp(255 - b);
                    }
                    case "skeleton" -> {
                        int sk = clamp((int)((lum - 128) * 1.5 + 128));
                        r  = clamp((int)(sk * 0.90));
                        g2 = clamp((int)(sk * 0.92));
                        b  = clamp(Math.min(255, (int)(sk * 1.05)));
                    }
                    case "blaze" -> {
                        int bl = clamp((int)((lum - 80) * 1.8 + 80));
                        r  = clamp(Math.min(255, (int)(bl * 1.4 + 30)));
                        g2 = clamp((int)(bl * 0.55));
                        b  = clamp((int)(bl * 0.05));
                    }
                    case "bat" -> {
                        int edge = Math.abs(r - (x > 0 ? lum : r)) + Math.abs(g2 - lum);
                        int bt = clamp((int)(lum * 0.15));
                        r  = clamp(bt + edge / 6);
                        g2 = clamp(bt + edge / 3);
                        b  = clamp(Math.min(255, bt + edge / 2 + 20));
                    }
                }
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g2 << 8) | r);
            }
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static final int MAX_PHOTO_DIMENSION = 720;

    private static void saveCompressedPhoto(NativeImage img, File outFile) throws IOException {
        BufferedImage buffered = nativeImageToBufferedImage(img);
        buffered = downscaleIfNeeded(buffered, MAX_PHOTO_DIMENSION);

        boolean written = ImageIO.write(buffered, "png", outFile);
        if (!written) {

            img.writeToFile(outFile);
        }
    }

    private static BufferedImage nativeImageToBufferedImage(NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = img.getPixelRGBA(x, y);
                int a = (abgr >> 24) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                int g = (abgr >> 8)  & 0xFF;
                int r =  abgr        & 0xFF;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static BufferedImage downscaleIfNeeded(BufferedImage src, int maxDimension) {
        int w = src.getWidth(), h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxDimension) return src;

        double scale = maxDimension / (double) longest;
        int newW = Math.max(1, Math.round(w * (float) scale));
        int newH = Math.max(1, Math.round(h * (float) scale));

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = scaled.createGraphics();
        try {
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(src, 0, 0, newW, newH, null);
        } finally {
            g2d.dispose();
        }
        return scaled;
    }

    public static void takeScheduledPhoto() {
        PendingPhoto pending = pendingPhoto;
        if (pending == null) return;
        pendingPhoto = null;

        Minecraft mc = Minecraft.getInstance();
        try {
            Path photosDir = getPhotosDirStatic();
            Files.createDirectories(photosDir);
            File outFile = photosDir.resolve(pending.filename).toFile();

            com.mojang.blaze3d.pipeline.RenderTarget mainTarget = mc.getMainRenderTarget();
            try (NativeImage fullImg = Screenshot.takeScreenshot(mainTarget)) {
                NativeImage nativeImg = fullImg;
                boolean cropApplied = false;

                if (pending.fbLeft >= 0 && pending.fbRight > pending.fbLeft
                        && pending.fbBottom > pending.fbTop) {
                    int imgW = fullImg.getWidth();
                    int imgH = fullImg.getHeight();
                    int cropX = Math.max(0, pending.fbLeft);
                    int cropY = Math.max(0, pending.fbTop);
                    int cropW = Math.min(pending.fbRight,  imgW) - cropX;
                    int cropH = Math.min(pending.fbBottom, imgH) - cropY;

                    if (cropW > 0 && cropH > 0) {
                        NativeImage cropped = new NativeImage(cropW, cropH, false);
                        for (int cy = 0; cy < cropH; cy++) {
                            for (int cx = 0; cx < cropW; cx++) {
                                cropped.setPixelRGBA(cx, cy, fullImg.getPixelRGBA(cropX + cx, cropY + cy));
                            }
                        }
                        nativeImg = cropped;
                        cropApplied = true;
                    }
                }

                if (!pending.filter.equals("none")) {
                    applyFilterToImage(nativeImg, pending.filter);
                }

                saveCompressedPhoto(nativeImg, outFile);

                if (cropApplied) nativeImg.close();
            }

            ICraftConstants.LOGGER.info("[iCraft] Foto guardada en: {}", outFile.getAbsolutePath());
            captureFovActive = false;
            suppressRender   = false;

            PhoneData.PhotoEntry photo = new PhoneData.PhotoEntry(
                    pending.filename,
                    pending.wx, pending.wy, pending.wz, pending.dim
            );
            photo.filter = pending.filter;
            photo.selfie = pending.selfie;
            phoneData.photos.add(photo);
            savePhotoMeta(photo);
            notifications.add(PhoneLang.get("icraft.phone.camera.photo_saved", pending.filename));

        } catch (Exception e) {
            ICraftConstants.LOGGER.error("[iCraft] Error al guardar foto: {}", e.getMessage());
            notifications.add(PhoneLang.get("icraft.phone.camera.photo_save_error"));
            captureFovActive = false;
            suppressRender   = false;
        }
    }

    public static boolean hasPendingPhoto() {
        return pendingPhoto != null;
    }

    public boolean isCameraActive() {
        return cameraLayout;
    }

    private Path getPhotosDir() {
        return getPhotosDirStatic();
    }

    public static Path getPhotosDirStatic() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("iCraft").resolve("photos");
    }

    public static boolean receiveMessageStatic(String convId, String sender, String content, long timestamp, boolean isGroup, String messageId) {
        if ("§§CLEARCHATS".equals(content)) {
            if (messageId != null && !messageId.isEmpty()) receivedMessageIds.add(messageId);
            clearAllChats();
            return true;
        }

        if (content != null && content.startsWith("§§GROUP_INVITE:")) {
            String[] parts = content.split(":", 4);
            if (parts.length >= 3) {
                String gId   = parts[1];
                String gName = parts[2];
                receiveGroupInvite(gId, gName);
            }
            if (messageId != null && !messageId.isEmpty()) receivedMessageIds.add(messageId);
            return true;
        }

        if (messageId != null && !messageId.isEmpty() && !receivedMessageIds.add(messageId)) {
            return false;
        }

        String myName = Minecraft.getInstance().getUser() != null
                ? Minecraft.getInstance().getUser().getName() : "";
        String convName;
        if ("mundial".equals(convId)) {
            convName = PhoneLang.get("icraft.phone.chats.global_name");
        } else if (!isGroup && sender.equals(myName)) {
            String other = extractOtherFromDmId(convId, myName);
            convName = other != null ? other : sender;
        } else {
            convName = sender;
        }

        PhoneData.ChatConversation conv = phoneData.conversations.stream()
                .filter(c -> c.id.equals(convId)).findFirst()
                .orElseGet(() -> {
                    PhoneData.ChatConversation nc = new PhoneData.ChatConversation(convId, convName, isGroup);
                    if ("mundial".equals(convId)) phoneData.conversations.add(0, nc);
                    else phoneData.conversations.add(nc);
                    return nc;
                });

        PhoneData.ChatMessage msg = new PhoneData.ChatMessage(sender, content);
        if (messageId != null && !messageId.isEmpty()) msg.id = messageId;
        msg.timestamp = timestamp;

        if (messageId != null && !messageId.isEmpty() && persistedReadIds.contains(messageId)) {
            msg.read = true;
        }

        conv.messages.add(msg);

        if (convId.equals(activeChatConvId)) {
            newMessageForActiveConv = true;
        }

        boolean isOwnMessage  = sender.equals(myName);
        boolean alreadyRead   = messageId != null && !messageId.isEmpty() && persistedReadIds.contains(messageId);
        if (!isOwnMessage && !alreadyRead && !phoneData.doNotDisturb && !conv.muted) {
            notifications.add("💬 " + displaySenderName(sender) + ": " + displayMessageContent(content));

            PhoneToast.push(displaySenderName(sender), displayMessageContent(content), timestamp);

            if (timestamp > PhoneToast.getScreenOpenedAt()) {
                com.icraft.client.NotificationSounds.play(phoneData.notificationSound);
            }
        }
        return true;
    }

    public void receiveMessage(String convId, String sender, String content, long timestamp, boolean isGroup, String messageId) {
        receiveMessageStatic(convId, sender, content, timestamp, isGroup, messageId);
    }

    public void updateWeather(WeatherPacket packet) {
    }

    public void updatePlayerList(List<String> players) {
        this.onlinePlayers = new ArrayList<>(players);
        String myName = Minecraft.getInstance().getUser().getName();
        onlinePlayers.remove(myName);
    }

    public static void applyWorldIcon(String base64Png) {
        if (base64Png == null || base64Png.isEmpty()) {
            worldIconLocation = null;
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Png);
            NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    "icraft", "dynamic/world_icon_" + System.nanoTime());
            Minecraft.getInstance().getTextureManager().register(loc, tex);

            if (worldIconDynamicTexture != null) worldIconDynamicTexture.close();

            worldIconDynamicTexture = tex;
            worldIconW = img.getWidth();
            worldIconH = img.getHeight();
            worldIconLocation = loc;
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudo decodificar el icon.png del mundo: {}", e.getMessage());
            worldIconLocation = null;
        }
    }

    public static void clearAllChats() {
        for (PhoneData.ChatConversation conv : phoneData.conversations) {
            conv.messages.clear();
        }
        receivedMessageIds.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneScreen ps) {
            if (ps.currentApp == App.CHAT_CONV) {
                ps.goToApp(App.CHAT);
            }
        }
        notifications.add(I18n.get("icraft.chat.admin_cleared_msg"));
    }

    public static void receiveAdminPhoto(String filename, String base64Png) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Png);

            Path photosDir = getPhotosDirStatic();
            Files.createDirectories(photosDir);
            Path dest = photosDir.resolve(filename);
            Files.write(dest, bytes);

            PhotoFrameRenderer.invalidateCache(filename);

            Minecraft mc = Minecraft.getInstance();
            mc.submit(() -> {
                try {
                    NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                    DynamicTexture tex = new DynamicTexture(img);
                    ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                            "icraft", "dynamic/photo/" + filename.replace(".png", "").replace(".", "_"));
                    mc.getTextureManager().register(loc, tex);

                    if (mc.screen instanceof PhoneScreen ps) {
                        ps.photoTextureCache.put(filename, loc);
                        ps.photoTextures.put(filename, tex);
                        ps.photoDimsCache.put(filename, new int[]{img.getWidth(), img.getHeight()});
                    }

                    ICraftConstants.LOGGER.info("[iCraft] Admin photo \"{}\" recibida y cargada ({}x{})",
                            filename, img.getWidth(), img.getHeight());
                } catch (Exception e) {
                    ICraftConstants.LOGGER.warn("[iCraft] Error al cargar textura de admin photo \"{}\": {}", filename, e.getMessage());
                }
            });

        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft] Error al recibir admin photo \"{}\": {}", filename, e.getMessage());
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastRequestTime > 5000) {
            lastRequestTime = now;
            if (Minecraft.getInstance().getConnection() != null) {
                NetworkManager.sendToServer(new RequestDataPacket("weather"));
                NetworkManager.sendToServer(new RequestDataPacket("players"));
            }
        }

        checkTimerCompletion();
        if (timerNeedsUiRefresh && currentApp == App.CLOCK && clockTab == 2) {
            timerNeedsUiRefresh = false;
            initClock();
        }

        if (callState == CallState.INCOMING) {

            if (!CallSounds.isPlaying()) {
                if (ringTickCounter <= 0) {
                    if (!phoneData.callSound.isEmpty()) {
                        CallSounds.play(phoneData.callSound);
                    }
                    ringTickCounter = 40;
                } else {
                    ringTickCounter--;
                }
            }
        }

        if (cameraLayout) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                long window = mc.getWindow().getWindow();
                setKeyDown(mc.options.keyUp, isGlfwKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_W));
                setKeyDown(mc.options.keyDown, isGlfwKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_S));
                setKeyDown(mc.options.keyLeft, isGlfwKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_A));
                setKeyDown(mc.options.keyRight, isGlfwKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_D));
                setKeyDown(mc.options.keyJump, isGlfwKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE));
                setKeyDown(mc.options.keySprint, isGlfwKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL));
            }
        }
    }

    private void renderWallpaper(GuiGraphics g, int x, int y, int w, int h) {
        ResourceLocation loc = getWallpaperLocation();
        blitWallpaperCover(g, loc, x, y, w, h);
    }

    private void blitWallpaperCover(GuiGraphics g, ResourceLocation loc, int x, int y, int w, int h) {
        float scale = Math.max(w / (float) WP_TEX_W, h / (float) WP_TEX_H);
        int srcW = Math.min(WP_TEX_W, Math.round(w / scale));
        int srcH = Math.min(WP_TEX_H, Math.round(h / scale));
        int srcX = (WP_TEX_W - srcW) / 2;
        int srcY = (WP_TEX_H - srcH) / 2;
        g.blit(loc, x, y, w, h, srcX, srcY, srcW, srcH, WP_TEX_W, WP_TEX_H);
    }

    private ResourceLocation getWallpaperLocation() {
        for (int i = 0; i < WALLPAPER_IDS.length; i++) {
            if (WALLPAPER_IDS[i].equals(phoneData.wallpaper)) return WALLPAPER_LOCS[i];
        }
        return WALLPAPER_LOCS[0];
    }

    private void blitIcon(GuiGraphics g, int col, int row, int x, int y, int size) {
        int u = col * ICON_SIZE;
        int v = row * ICON_SIZE;
        if (size == ICON_SIZE) {
            g.blit(HUD_ICONS, x, y, u, v, ICON_SIZE, ICON_SIZE, SHEET_W, SHEET_H);
            return;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float scale = size / (float) ICON_SIZE;
        pose.scale(scale, scale, 1f);
        g.blit(HUD_ICONS, 0, 0, u, v, ICON_SIZE, ICON_SIZE, SHEET_W, SHEET_H);
        pose.popPose();
    }

    private void ensureWeatherForecast(long day) {
        if (day == weatherForecastDay) return;
        weatherForecastDay = day;
        java.util.Random rnd = new java.util.Random(day * 31L + 7L);
        for (int i = 0; i < weatherForecastConditions.length; i++) {
            weatherForecastConditions[i] = rollForecastCondition(rnd);
        }
    }

    private int rollForecastCondition(java.util.Random rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 6) return WX_STORM;        // 6% tormenta
        if (roll < 6 + 18) return WX_RAIN;    // 18% lluvia
        return WX_CLEAR;                      // 76% despejado
    }

    private void blitWeatherIcon(GuiGraphics g, int condition, int timeOfDay, int x, int y, int size) {
        int u = WEATHER_ICON_COL[timeOfDay] * WEATHER_ICON_SIZE;
        int v = condition * WEATHER_ICON_SIZE;
        if (size == WEATHER_ICON_SIZE) {
            g.blit(WEATHER_ICONS, x, y, u, v, WEATHER_ICON_SIZE, WEATHER_ICON_SIZE, WEATHER_SHEET_W, WEATHER_SHEET_H);
            return;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float scale = size / (float) WEATHER_ICON_SIZE;
        pose.scale(scale, scale, 1f);
        g.blit(WEATHER_ICONS, 0, 0, u, v, WEATHER_ICON_SIZE, WEATHER_ICON_SIZE, WEATHER_SHEET_W, WEATHER_SHEET_H);
        pose.popPose();
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 1 && font.width(text + "…") > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    private void drawScaledCenteredString(GuiGraphics g, String text, int centerX, int y, float scale, int color) {
        drawScaledCenteredString(g, text, centerX, y, scale, color, false);
    }

    private void drawScaledCenteredString(GuiGraphics g, String text, int centerX, int y, float scale, int color, boolean shadow) {
        var pose = g.pose();
        pose.pushPose();
        float textW = font.width(text) * scale;
        pose.translate(centerX - textW / 2f, y, 0);
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    private int getCaseColor() {
        return switch (phoneData.currentCase) {
            case "black"   -> 0xFF1A1A1A;
            case "white"   -> 0xFFEEEEEE;
            case "neon"    -> 0xFF00FF88;
            case "diamond" -> 0xFF88CCFF;
            default        -> 0xFF2D2D44;
        };
    }

    private int getThemeColor() {
        int idx = Arrays.asList(THEMES).indexOf(phoneData.theme);
        if (idx < 0) idx = Arrays.asList(THEMES).indexOf("blue");
        return idx >= 0 ? THEME_COLORS[idx] : 0xFF3C44AA;
    }

    private static final int CONTACTS_ICON_INDEX = 8;
    private static final int CONTACTS_FIXED_COLOR = 0xFF9C27B0;

    private int getEffectiveAppColor(int index) {
        if (index < 0 || index >= phoneData.appIconColors.length) return getThemeColor();
        return phoneData.appIconColors[index];
    }

    private int getAppIconColor(String appKey) {
        String[] keys = {"CHAT","CAMERA","PHOTOS","WEATHER","CLOCK","NOTES","MAPA","SETTINGS","CONTACTS"};
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(appKey)) return getEffectiveAppColor(i);
        }
        return getThemeColor();
    }

    private int getFilterColor() {
        return switch (selectedFilter) {
            case "sepia"  -> 0x55AA7733;
            case "vivid"  -> 0x2200CC66;
            case "cool"   -> 0x443366CC;
            case "warm"   -> 0x44FF7722;
            case "noir"   -> 0x77000000;
            case "retro"  -> 0x44CC8833;
            case "fade"      -> 0x33AAAAAA;
            case "creeper"   -> 0x6600CC00;
            case "enderman"  -> 0x881A0033;
            case "skeleton"  -> 0x33CCCCCC;
            case "blaze"     -> 0x55FF8800;
            case "bat"       -> 0xCC000011;
            default          -> 0;
        };
    }

    private int getPhotoColor(String filter) {
        return switch (filter) {
            case "sepia" -> 0xFFAA8844;
            case "vivid" -> 0xFF44FF88;
            case "cool"  -> 0xFF4488FF;
            case "warm"  -> 0xFFFF8844;
            case "noir"     -> 0xFF444444;
            case "creeper"  -> 0xFF1A6B1A;
            case "enderman" -> 0xFF330066;
            case "skeleton" -> 0xFFCCCCCC;
            case "blaze"    -> 0xFFCC6600;
            case "bat"      -> 0xFF111133;
            default         -> 0xFF5588AA;
        };
    }

    @Override
    public void removed() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.stopUsingItem();
        }
        if (!closedNotified) {
            closedNotified = true;
            NetworkManager.sendToServer(new com.icraft.network.PhoneOpenStatePacket(false));
        }
        destroyMapaTarget();
        super.removed();
    }

    @Override
    public void onClose() {
        if (!closedNotified) {
            closedNotified = true;
            NetworkManager.sendToServer(new com.icraft.network.PhoneOpenStatePacket(false));
        }
        if (cameraLayout) {
            cameraLayout = false;
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            Minecraft.getInstance().mouseHandler.releaseMouse();
            releaseCameraMouse();
        }
        saveSettings();
        savePersistedReadIds();
        activeChatConvId = null;
        super.onClose();
        for (Map.Entry<String, DynamicTexture> e : photoTextures.entrySet()) {
            ResourceLocation loc = photoTextureCache.get(e.getKey());
            if (loc != null) {
                Minecraft.getInstance().getTextureManager().release(loc);
            }
            e.getValue().close();
        }
        photoTextures.clear();
        photoTextureCache.clear();
        photoDimsCache.clear();
    }

    private static Path getSettingsFile() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("iCraft").resolve("settings.properties");
    }

    public static boolean applyCaseFromItem(String caseId) {
        if (phoneData.currentCase.equals(caseId)) return false;
        phoneData.currentCase = caseId;
        saveSettings();
        if (Minecraft.getInstance().screen instanceof PhoneScreen ps) {
            ps.initCurrentApp();
        }
        return true;
    }

    private static void saveSettings() {
        try {
            Path settingsFile = getSettingsFile();
            Files.createDirectories(settingsFile.getParent());
            Properties props = new Properties();
            props.setProperty("theme",        phoneData.theme);
            props.setProperty("wallpaper",    phoneData.wallpaper);
            props.setProperty("currentCase",  phoneData.currentCase);
            props.setProperty("notificationSound", phoneData.notificationSound);
            props.setProperty("callSound", phoneData.callSound);
            props.setProperty("clickSound", phoneData.clickSound);
            props.setProperty("doNotDisturb", String.valueOf(phoneData.doNotDisturb));
            props.setProperty("language", phoneData.language);
            try (FileOutputStream fos = new FileOutputStream(settingsFile.toFile())) {
                props.store(fos, "iCraft settings");
            }
            syncCallRingtoneToServer();
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("Could not save iCraft settings: {}", e.getMessage());
        }
    }

    private static void syncCallRingtoneToServer() {
        if (Minecraft.getInstance().getConnection() != null) {
            NetworkManager.sendToServer(new CallRingtonePacket(phoneData.callSound));
        }
    }

    private static Path getReadIdsFile() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("iCraft").resolve("read_messages.properties");
    }

    public static void ensureReadIdsLoaded() {
        if (!readIdsLoaded) {
            loadPersistedReadIds();
            readIdsLoaded = true;
        }
    }

    private static void loadPersistedReadIds() {
        try {
            Path file = getReadIdsFile();
            if (!Files.exists(file)) return;
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(file.toFile())) {
                props.load(fis);
            }
            String raw = props.getProperty("ids", "");
            if (!raw.isEmpty()) {
                for (String id : raw.split(",")) {
                    String trimmed = id.trim();
                    if (!trimmed.isEmpty()) persistedReadIds.add(trimmed);
                }
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudieron cargar IDs de mensajes leídos: {}", e.getMessage());
        }
    }

    private static void savePersistedReadIds() {
        try {
            Path file = getReadIdsFile();
            Files.createDirectories(file.getParent());
            Properties props = new Properties();
            props.setProperty("ids", String.join(",", persistedReadIds));
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                props.store(fos, "iCraft read message IDs");
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudieron guardar IDs de mensajes leídos: {}", e.getMessage());
        }
    }

    private void loadSettings() {
        try {
            Path settingsFile = getSettingsFile();
            if (!Files.exists(settingsFile)) {

                if (!phoneData.notificationSound.isEmpty() && !Arrays.asList(sounds()).contains(phoneData.notificationSound)) {
                    phoneData.notificationSound = "";
                }
                if (!phoneData.callSound.isEmpty() && !Arrays.asList(callSoundsArr()).contains(phoneData.callSound)) {
                    phoneData.callSound = "";
                }
                if (!phoneData.clickSound.isEmpty() && !Arrays.asList(clickSoundsArr()).contains(phoneData.clickSound)) {
                    phoneData.clickSound = "";
                }
                return;
            }
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(settingsFile.toFile())) {
                props.load(fis);
            }
            String theme = props.getProperty("theme");
            if (theme != null && Arrays.asList(THEMES).contains(theme))
                phoneData.theme = theme;

            String wallpaper = props.getProperty("wallpaper");
            if (wallpaper != null && Arrays.asList(WALLPAPER_IDS).contains(wallpaper))
                phoneData.wallpaper = wallpaper;

            String currentCase = props.getProperty("currentCase");
            if (currentCase != null && Arrays.asList(CASES).contains(currentCase))
                phoneData.currentCase = currentCase;

            phoneData.darkMode = true;

            String sound = props.getProperty("notificationSound");
            if (sound != null && (sound.isEmpty() || Arrays.asList(sounds()).contains(sound)))
                phoneData.notificationSound = sound;

            if (!phoneData.notificationSound.isEmpty() && !Arrays.asList(sounds()).contains(phoneData.notificationSound)) {
                phoneData.notificationSound = "";
            }

            String callSound = props.getProperty("callSound");
            if (callSound != null && (callSound.isEmpty() || Arrays.asList(callSoundsArr()).contains(callSound)))
                phoneData.callSound = callSound;
            if (!phoneData.callSound.isEmpty() && !Arrays.asList(callSoundsArr()).contains(phoneData.callSound)) {
                phoneData.callSound = "";
            }

            String clickSound = props.getProperty("clickSound");
            if (clickSound != null && (clickSound.isEmpty() || Arrays.asList(clickSoundsArr()).contains(clickSound)))
                phoneData.clickSound = clickSound;
            if (!phoneData.clickSound.isEmpty() && !Arrays.asList(clickSoundsArr()).contains(phoneData.clickSound)) {
                phoneData.clickSound = "";
            }

            String dnd = props.getProperty("doNotDisturb");
            if (dnd != null)
                phoneData.doNotDisturb = Boolean.parseBoolean(dnd);

            String language = props.getProperty("language");
            if (language != null && Arrays.asList(PhoneLang.SUPPORTED).contains(language))
                phoneData.language = language;

        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("Could not load iCraft settings: {}", e.getMessage());
        }
    }

    private void applyDefaultAppColorsOnce() {
        try {
            Path settingsFile = getSettingsFile();
            Properties props = new Properties();
            if (Files.exists(settingsFile)) {
                try (FileInputStream fis = new FileInputStream(settingsFile.toFile())) {
                    props.load(fis);
                }
            }
            if (Boolean.parseBoolean(props.getProperty("appColorsDefaultsApplied", "false"))) {
                return;
            }

            int[] defaults = {
                    0xFF4CAF50,
                    0xFF212121,
                    0xFFFF9800,
                    0xFF29B6F6,
                    0xFF607D8B,
                    0xFF795548,
                    0xFF4FC3F7,
                    0xFFF06292,
                    CONTACTS_FIXED_COLOR,
            };
            for (int i = 0; i < defaults.length && i < phoneData.appIconColors.length; i++) {
                phoneData.appIconColors[i] = defaults[i];
            }

            Files.createDirectories(settingsFile.getParent());
            props.setProperty("appColorsDefaultsApplied", "true");
            try (FileOutputStream fos = new FileOutputStream(settingsFile.toFile())) {
                props.store(fos, "iCraft settings");
            }
        } catch (Exception e) {
            ICraftConstants.LOGGER.warn("Could not apply default app colors: {}", e.getMessage());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {

        if (currentApp == App.NOTE_EDIT && !noteViewMode && noteInput != null) {
            return noteInput.charTyped(codePoint, modifiers);
        }
        if (currentApp == App.CHAT_CONV && chatInput != null) {
            return chatInput.charTyped(codePoint, modifiers);
        }
        if (currentApp == App.CREATE_GROUP && groupNameInput != null) {
            return groupNameInput.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && currentApp == App.NOTE_EDIT
                && noteInput != null && noteInput.isFocused()) {
            String current = noteInput.getValue();

            if (!current.isEmpty() || !noteDraftBuffer.isEmpty()) {
                noteDraftBuffer += (noteDraftBuffer.isEmpty() ? "" : "\n") + current;
                noteInput.setValue("");
                noteEditScrollOffset = Integer.MAX_VALUE;
                initCurrentApp();
            }
            return true;
        }

        if (keyCode == 259 && currentApp == App.NOTE_EDIT && !noteViewMode
                && noteInput != null && noteInput.isFocused()
                && noteInput.getCursorPosition() == 0 && noteInput.getValue().isEmpty()
                && !noteDraftBuffer.isEmpty()) {

            int lastBreak = noteDraftBuffer.lastIndexOf('\n');
            if (lastBreak >= 0) {
                pendingNoteInputValue = noteDraftBuffer.substring(lastBreak + 1);
                noteDraftBuffer = noteDraftBuffer.substring(0, lastBreak);
            } else {
                pendingNoteInputValue = noteDraftBuffer;
                noteDraftBuffer = "";
            }
            noteEditScrollOffset = Integer.MAX_VALUE;
            initCurrentApp();
            return true;
        }

        if ((keyCode == 257 || keyCode == 335) && currentApp == App.CHAT_CONV
                && chatInput != null && chatInput.isFocused()) {
            String val = chatInput.getValue().trim();
            if (!val.isEmpty()) {
                sendChatMessage(val);
                chatInput.setValue("");
            }
            return true;
        }

        if (keyCode == 256) {
            if (currentApp == App.PHOTOS && photoViewerIndex >= 0) {
                photoViewerIndex = -1;
                return true;
            }
            onClose();
            return true;
        }

        if (currentApp == App.PHOTOS && photoViewerIndex >= 0) {
            if (keyCode == 263 && photoViewerIndex > 0) {
                photoViewerIndex--;
                return true;
            }
            if (keyCode == 262 && photoViewerIndex < phoneData.photos.size() - 1) {
                photoViewerIndex++;
                return true;
            }
        }

        if ((keyCode == 340 || keyCode == 344) && !cameraLayout) {
            boolean typing = (chatInput != null && chatInput.isFocused())
                    || (noteInput != null && noteInput.isFocused());
            if (!typing) {
                if (currentApp == App.PHOTOS && photoViewerIndex >= 0) {
                    photoViewerIndex = -1;
                    return true;
                }
                if (goBack()) {
                    return true;
                }
            }
        }

        if (cameraLayout) {
            if (keyCode == 82) {
                if (System.currentTimeMillis() - cameraEnteredAt < 250) {
                    return true;
                }
                capturePhoto();
                return true;
            }
            if (keyCode == 70) {
                cameraPerspective = (cameraPerspective + 1) % 3;
                CameraType next = switch (cameraPerspective) {
                    case 1  -> CameraType.THIRD_PERSON_BACK;
                    case 2  -> CameraType.THIRD_PERSON_FRONT;
                    default -> CameraType.FIRST_PERSON;
                };
                Minecraft.getInstance().options.setCameraType(next);
                return true;
            }
            if (keyCode == 71) {
                filterIndex = (filterIndex + 1) % FILTERS.length;
                selectedFilter = FILTERS[filterIndex];
                return true;
            }
            if (keyCode == 265) {
                cameraFov = Math.max(CAM_FOV_MIN, cameraFov - 2);
                return true;
            }
            if (keyCode == 264) {
                cameraFov = Math.min(CAM_FOV_MAX, cameraFov + 2);
                return true;
            }
            if (keyCode == 340 || keyCode == 344) {
                notifications.add(PhoneLang.get("icraft.phone.camera.cancelled"));
                currentApp = App.HOME;
                initCurrentApp();
                return true;
            }

            int[] movementKeys = {87, 65, 83, 68, 32, 341, 262, 263};
            for (int mk : movementKeys) {
                if (keyCode == mk) return false;
            }
        }

        if (currentApp == App.NOTE_EDIT && !noteViewMode && noteInput != null
                && noteInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (currentApp == App.CHAT_CONV && chatInput != null
                && chatInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (currentApp == App.CREATE_GROUP && groupNameInput != null
                && groupNameInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (cameraLayout) {
            return true;
        }
        if (currentApp == App.MAPA && xaeroGuiMap != null) {
            Minecraft mc2 = Minecraft.getInstance();
            int guiW2 = mc2.getWindow().getGuiScaledWidth();
            int guiH2 = mc2.getWindow().getGuiScaledHeight();
            final int HEADER_H2 = 15;
            int mGX = sx(), mGY = appY() + HEADER_H2, mGW = SCREEN_W, mGH = APP_H - HEADER_H2 - NAV_H;
            float sX2 = (float) mGW / guiW2, sY2 = (float) mGH / guiH2;
            float sc2 = Math.max(sX2, sY2) * 1.40f;
            float oX2 = mGX + (mGW - guiW2 * sc2) / 2f, oY2 = mGY + (mGH - guiH2 * sc2) / 2f;
            return xaeroGuiMap.mouseReleased((mouseX - oX2) / sc2, (mouseY - oY2) / sc2, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (cameraLayout) {
            int[] movementKeys = {87, 65, 83, 68, 32, 340, 341, 264, 265, 266, 267};
            for (int mk : movementKeys) {
                if (keyCode == mk) return false;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean isGlfwKeyDown(long window, int glfwKey) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, glfwKey) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private void setKeyDown(net.minecraft.client.KeyMapping mapping, boolean down) {
        mapping.setDown(down);
    }
}
