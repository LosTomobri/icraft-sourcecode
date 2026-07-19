package com.icraft.client;

import com.icraft.ICraftMod;
import com.icraft.data.PhoneData;
import com.icraft.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.Screenshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Properties;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class PhoneScreen extends Screen {

    // ==================== STATICS & CONSTANTS ====================

    private static int getTotalUnreadCount() {
        String myName = Minecraft.getInstance().getUser() != null
                ? Minecraft.getInstance().getUser().getName() : "";
        return phoneData.conversations.stream()
                .mapToInt(c -> c.getUnreadCount(myName))
                .sum();
    }

    
    private static final ResourceLocation HUD_ICONS =
            ResourceLocation.fromNamespaceAndPath("icraft", "textures/item/phone_hud.png");
    private static final int ICON_SIZE    = 16;
    private static final int SHEET_W     = 144;
    private static final int SHEET_H     = 48;

    
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

    // ==================== PHONE DIMENSIONS ====================

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

    // ==================== APPS ====================

    public enum App { HOME, CHAT, CHAT_CONV, CAMERA, PHOTOS, WEATHER, CLOCK, NOTES, MAPA, SETTINGS, ICON_EDITOR, CONTACTS, PRIVACY, CREATE_GROUP }
    private App currentApp = App.HOME;

    private final Deque<App> appHistory = new ArrayDeque<>();

    
    private void goToApp(App app) {
        if (currentApp != app) {
            appHistory.push(currentApp);
        }
        currentApp = app;
        initCurrentApp();
    }

    
    private boolean goBack() {
        if (appHistory.isEmpty()) return false;
        currentApp = appHistory.pop();
        initCurrentApp();
        return true;
    }

    // ==================== DATA ====================

    private static PhoneData phoneData = new PhoneData();
    private static List<String> onlinePlayers = new ArrayList<>();
    private static List<String> knownContacts = new ArrayList<>();

    private PhoneData.ChatConversation currentConv = null;
    private EditBox chatInput;
    private int chatListScrollOffset = 0;
    private int chatConvScrollOffset = 0;
    private int contactsScrollOffset = 0;
    private int privacyScrollOffset = 0;

    
    private EditBox groupNameInput = null;
    
    private final Set<String> groupSelectedMembers = new LinkedHashSet<>();
    
    private static final Map<String, String> pendingGroupInvites = new java.util.LinkedHashMap<>();
    
    private static volatile boolean hasNewGroupInvite = false;
    private boolean chatAtBottom = true;

    private int settingsContentBottom = 0;

    // ==================== CAMERA STATE ====================

    private boolean selfieMode = false;
    private String selectedFilter = "none";
    private static final String[] FILTERS = {"none","sepia","vivid","cool","warm","noir","retro","fade","creeper","enderman","skeleton","blaze","bat"};
    private int filterIndex = 0;
    private int cameraPerspective = 0;
    private static final int CAM_FOV_MIN = 2;     // zoom máximo (estilo catalejo)
    private static final int CAM_FOV_MAX = 50;    // gran angular, valor por defecto
    private static final int CAM_FOV_TELE_THRESHOLD = 15;
    private int cameraFov = CAM_FOV_MAX;          // FOV actual del slider de la cámara
    private static volatile int pendingCaptureFov  = CAM_FOV_MAX;
    private static volatile boolean captureFovActive = false;
    private boolean cameraLayout = false;
    
    private boolean closedNotified = false;
    private boolean cameraMouseCaptured = false;
    private double camPrevCursorX = 0, camPrevCursorY = 0;
    
    private long cameraEnteredAt = 0;
    
    private static App pendingOpenApp = null;
    private static App reopenAfterPhotoApp = null;

    // ==================== CLOCK ====================

    private int clockTab = 0; // 0 = Reloj, 1 = Cronómetro, 2 = Temporizador

    private static final int CLOCK_TAB0_CONTENT_H = 96; // Reloj: hora MC + día + hora local
    private static final int CLOCK_TAB1_CONTENT_H = 50; // Cronómetro: texto + fila de botones
    private static final int CLOCK_TAB2_CONTENT_H_IDLE    = 68; // Temporizador parado: + ajustes
    private static final int CLOCK_TAB2_CONTENT_H_RUNNING = 50; // Temporizador corriendo: sin ajustes

    private static boolean stopwatchRunning = false;
    private static long stopwatchStartedAt = 0;     // System.currentTimeMillis() del último "Iniciar"
    private static long stopwatchAccumulatedMs = 0; // tiempo acumulado de tramos ya detenidos

    private static boolean timerRunning = false;
    private static boolean timerFinished = false;
    private static long timerEndAt = 0;                     // timestamp en el que debe sonar
    private static long timerRemainingMs = 5 * 60 * 1000L;   // tiempo restante (pausado / sin iniciar)
    private static long timerDurationMs  = 5 * 60 * 1000L;   // duración configurada, usada al "Reiniciar"
    private static final long TIMER_MIN_MS = 10_000L;             // mínimo 10s
    private static final long TIMER_MAX_MS = 99 * 60_000L + 59_000L; // máximo 99:59
    private static boolean timerNeedsUiRefresh = false;

    static {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(PhoneScreen.class);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        checkTimerCompletion();
    }

    
    private static void checkTimerCompletion() {
        if (timerRunning && System.currentTimeMillis() >= timerEndAt) {
            timerRunning = false;
            timerFinished = true;
            timerRemainingMs = 0;
            timerNeedsUiRefresh = true;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
            }
            PhoneToast.pushTimer("¡Tiempo cumplido!");
        }
    }

    // ==================== NOTES ====================

    private EditBox noteInput;
    private int selectedNote = -1;
    
    private String noteDraftBuffer = "";
    
    private String noteEditFolder = "";
    
    private boolean noteEditPinned = false;
    
    private boolean namingNewFolder = false;
    
    private String notesFolderFilter = null;

    // ==================== SETTINGS ====================

    private static final String[] THEMES = {"blue","green","purple","pink","red","orange"};
    private static final String[] CASES = {"default","black","white","neon","diamond"};
    private static final String[] SOUNDS = {"ding","chime","buzz","none"};
    private static final int WP_TEX_SIZE = 256;
    private static final String[] WALLPAPER_IDS = {
        "wp_space", "wp_sunset", "wp_forest", "wp_ocean",
        "wp_neoncity", "wp_minecraft", "wp_galaxy", "wp_retro"
    };
    private static final String[] WALLPAPER_NAMES = {
        "Espacio", "Atardecer", "Bosque", "Océano",
        "Neon City", "Minecraft", "Galaxia", "Retro"
    };
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

    // ==================== NOTIFICATIONS ====================

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

    
    // ==================== PHOTO STATE ====================

    private boolean photoShareMenuOpen = false;
    
    private int photoShareIndex = -1;

    private final Map<String, ResourceLocation> photoTextureCache = new LinkedHashMap<>();
    private final Map<String, DynamicTexture>   photoTextures     = new LinkedHashMap<>();
    private final Map<String, int[]> photoDimsCache = new LinkedHashMap<>();

    private int photoViewerIndex = -1;
    private int photosScrollOffset = 0;

    // ==================== CONSTRUCTOR ====================

    public PhoneScreen() {
        super(Component.literal("iCraft"));
        loadSettings();
        if (!readIdsLoaded) {
            loadPersistedReadIds();
            readIdsLoaded = true;
        }
        phoneData.darkMode = true;
        applyDefaultAppColorsOnce();
        syncPhotosFromDisk();
        PacketDistributor.sendToServer(new com.icraft.network.PhoneOpenStatePacket(true));
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
                loadPhotoMeta(photo, photosDir); // cargar metadatos persistidos si existen
                phoneData.photos.add(photo);
                known.add(filename);
            }
        } catch (Exception e) {
            ICraftMod.LOGGER.warn("Could not sync photos from disk: {}", e.getMessage());
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
            ICraftMod.LOGGER.warn("Could not save photo meta for {}: {}", photo.filename, e.getMessage());
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
            ICraftMod.LOGGER.warn("Could not load photo meta for {}: {}", photo.filename, e.getMessage());
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

        // Registrar el momento de apertura para que PhoneToast filtre el historial
        // por timestamp: solo los mensajes con timestamp posterior a este instante
        // serán notificados como toasts. Así no dependemos de un timer fijo.
        PhoneToast.markScreenOpened();
        PacketDistributor.sendToServer(new RequestDataPacket("all"));

        if (pendingOpenApp != null) {
            currentApp = pendingOpenApp;
            pendingOpenApp = null;
        }

        initCurrentApp();
    }

    
    // ==================== LAYOUT ====================

    private void recalcLayout() {
        int margin = 10; // px to spare on every side

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
        } else {
            float scaleH = (height - margin * 2) / (float) BASE_PHONE_H;
            float scaleW = (width  - margin * 2) / (float) BASE_PHONE_W;
            float scale  = Math.min(scaleH, scaleW);   // keep aspect ratio
            scale = Math.max(scale, 0.75f);             // never shrink below 75%
            scale = Math.min(scale, 2.5f);              // never grow beyond 2.5x

            PHONE_W      = Math.round(BASE_PHONE_W      * scale);
            PHONE_H      = Math.round(BASE_PHONE_H      * scale);
            SCREEN_X_OFF = Math.round(BASE_SCREEN_X_OFF * scale);
            SCREEN_Y_OFF = Math.round(BASE_SCREEN_Y_OFF * scale);
            STATUS_H     = Math.round(BASE_STATUS_H     * scale);
            NAV_H        = Math.round(BASE_NAV_H        * scale);
            int bottomMargin = Math.round(8 * scale);
            SCREEN_H     = PHONE_H - SCREEN_Y_OFF - bottomMargin;
            SCREEN_W     = Math.round(BASE_SCREEN_W     * scale);
            APP_H        = SCREEN_H - STATUS_H;
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

    // ==================== INIT APPS ====================

    private void initCurrentApp() {
        clearWidgets();
        if (currentApp != App.CHAT_CONV) activeChatConvId = null;

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
            case HOME       -> {} // Home renders in renderHome
            case CHAT       -> initChat();
            case CHAT_CONV  -> initChatConversation();
            case CAMERA     -> initCamera();
            case PHOTOS     -> {} // TODO: implement photos
            case WEATHER    -> {} // Weather renders in renderWeather
            case CLOCK      -> initClock();
            case NOTES      -> initNotes();
            case MAPA       -> initMapa();
            case SETTINGS   -> initSettings();
            case ICON_EDITOR -> initIconEditor();
            case CONTACTS   -> {} // Contacts renders in renderContacts
            case PRIVACY    -> initPrivacy();
            case CREATE_GROUP -> initCreateGroup();
        }
    }

    private void initChat() {
        int btnW = 42;
        addRenderableWidget(Button.builder(Component.literal("+ Grupo"), b -> {
            groupSelectedMembers.clear();
            goToApp(App.CREATE_GROUP);
        }).pos(sx() + SCREEN_W - btnW - 2, appY() + 1).size(btnW, 11).build());
    }

    
    private void initCreateGroup() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.CHAT);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        groupNameInput = new EditBox(font, sx() + 5, appY() + 18, SCREEN_W - 10, 12,
                Component.literal("Nombre del grupo..."));
        groupNameInput.setMaxLength(32);
        addRenderableWidget(groupNameInput);

        addRenderableWidget(Button.builder(Component.literal("✔ Crear grupo"), b -> {
            String gName = groupNameInput != null ? groupNameInput.getValue().trim() : "";
            if (gName.isEmpty() || groupSelectedMembers.isEmpty()) {
                notifications.add("Ponele un nombre y seleccioná al menos un miembro.");
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
            PacketDistributor.sendToServer(new SendChatPacket(
                    "GROUP_INVITE:" + groupId + ":" + groupName, member,
                    "__group_invite__",
                    false, true, java.util.UUID.randomUUID().toString()));
        }

        notifications.add("Grupo \"" + groupName + "\" creado. Invitaciones enviadas.");
        currentConv = grpConv;
        chatConvScrollOffset = Integer.MAX_VALUE;
        chatAtBottom = true;
        goToApp(App.CHAT_CONV);
    }

    
    public static void receiveGroupInvite(String groupId, String groupName) {
        pendingGroupInvites.put(groupId, groupName);
        hasNewGroupInvite = true;
        notifications.add("📨 Invitación al grupo \"" + groupName + "\"");
    }

    
    private void sendChatMessage(String content) {
        if (currentConv == null || content == null || content.isEmpty()) return;
        String myName = Minecraft.getInstance().getUser().getName();
        PhoneData.ChatMessage m = new PhoneData.ChatMessage(myName, content);
        m.read = true; // los mensajes propios siempre están "leídos"
        currentConv.messages.add(m);
        receivedMessageIds.add(m.id);
        persistedReadIds.add(m.id); // persistir para que no vuelva como no leído al reconectar
        PacketDistributor.sendToServer(new SendChatPacket(
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
        int inputW   = SCREEN_W - sendBtnW - locBtnW - 10;  // espacio para botón ubicación + enviar
        int inputY   = appBottom() - NAV_H - 14;

        chatInput = new EditBox(font, inputX, inputY, inputW, 11,
                Component.literal("Mensaje..."));
        chatInput.setMaxLength(200);
        addRenderableWidget(chatInput);

        addRenderableWidget(Button.builder(Component.literal("📍"), b -> {
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            net.minecraft.core.BlockPos pos = player.blockPosition();
            String desc = chatInput != null ? chatInput.getValue().trim() : "";
            String content = "📍 X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ();
            if (!desc.isEmpty()) content += "\n" + desc;
            sendChatMessage(content);
            if (chatInput != null) chatInput.setValue("");
        }).pos(locX, inputY).size(locBtnW, 11).build());

        addRenderableWidget(Button.builder(Component.literal(">>"), b -> {
            if (chatInput != null && !chatInput.getValue().trim().isEmpty()) {
                sendChatMessage(chatInput.getValue().trim());
                chatInput.setValue("");
            }
        }).pos(inputX + inputW + 2, inputY).size(sendBtnW, 11).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.CHAT);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());

        addRenderableWidget(Button.builder(
                Component.literal(currentConv != null && currentConv.muted ? "[M]" : "[S]"),
                b -> { if (currentConv != null) currentConv.muted = !currentConv.muted; initCurrentApp(); }
        ).pos(sx() + SCREEN_W - 26, appY() + 2).size(24, 10).build());
    }

    private void initCamera() {
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        cameraPerspective = 0;
        notifications.add("Camera mode. R = photo, F = perspective, G = filter, Shift = cancel");
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

    
    private boolean isEditingNote() {
        return selectedNote >= 0 || !noteDraftBuffer.isEmpty()
                || (noteInput != null && !noteInput.getValue().isEmpty())
                || namingNewFolder;
    }

    private void initNotes() {
        boolean editingNow = isEditingNote();

        int bottomBlockH = 11 + 2 + 14 + 2 + 11;
        int toolbarY = appBottom() - NAV_H - bottomBlockH;
        int inputY   = toolbarY + 13;
        int saveY    = inputY + 16;

        if (!editingNow) {
            String filterLabel = "\uD83D\uDCC1 " + (notesFolderFilter == null ? "Todas" : notesFolderFilter);
            addRenderableWidget(Button.builder(Component.literal(truncate(filterLabel, SCREEN_W - 8)),
                    b -> cycleFolderFilter()
            ).pos(sx() + 2, appY() + 16).size(SCREEN_W - 4, 11).build());
        }

        int unit = (SCREEN_W - 4) / 6;
        int tx = sx() + 2;

        addRenderableWidget(Button.builder(
                Component.literal(noteEditPinned ? "\uD83D\uDCCC\u2713" : "\uD83D\uDCCC"),
                b -> {
                    if (namingNewFolder) return;
                    noteEditPinned = !noteEditPinned;
                    initNotes();
                }
        ).pos(tx, toolbarY).size(unit - 1, 11).build());

        addRenderableWidget(Button.builder(Component.literal("B"), b -> {
            if (namingNewFolder || noteInput == null) return;
            int cursor = noteInput.getCursorPosition();
            noteInput.insertText("****");
            noteInput.setCursorPosition(cursor + 2);
        }).pos(tx + unit, toolbarY).size(unit - 1, 11).build());

        addRenderableWidget(Button.builder(Component.literal("\u2022"), b -> {
            if (namingNewFolder || noteInput == null) return;
            String current = noteInput.getValue();
            if (!current.trim().isEmpty()) {
                noteDraftBuffer += (noteDraftBuffer.isEmpty() ? "" : "\n") + current;
            }
            noteInput.setValue("- ");
            noteInput.setCursorPosition(2);
            initNotes();
        }).pos(tx + unit * 2, toolbarY).size(unit - 1, 11).build());

        addRenderableWidget(Button.builder(Component.literal("\uD83D\uDCCD"), b -> {
            if (namingNewFolder || noteInput == null) return;
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            net.minecraft.core.BlockPos pos = player.blockPosition();
            noteInput.insertText("\uD83D\uDCCD X:" + pos.getX() + " Y:" + pos.getY() + " Z:" + pos.getZ());
        }).pos(tx + unit * 3, toolbarY).size(unit - 1, 11).build());

        String folderLabel = namingNewFolder ? "+ Carpeta"
                : ("\uD83D\uDCC1 " + (noteEditFolder.isEmpty() ? "Sin carpeta" : noteEditFolder));
        addRenderableWidget(Button.builder(Component.literal(truncate(folderLabel, unit * 2 - 4)), b -> {
            if (!namingNewFolder) cycleEditFolder();
        }).pos(tx + unit * 4, toolbarY).size(unit * 2 - 1, 11).build());

        String preserveValue  = noteInput != null ? noteInput.getValue() : "";
        int    preserveCursor = noteInput != null ? noteInput.getCursorPosition() : 0;

        Component hint = namingNewFolder
                ? Component.literal("Nombre de la carpeta...")
                : Component.literal("Escribe una línea...");
        noteInput = new EditBox(font, sx() + 2, inputY, SCREEN_W - 4, 14, hint);
        noteInput.setMaxLength(500);
        noteInput.setValue(preserveValue);
        noteInput.setCursorPosition(preserveCursor);
        addRenderableWidget(noteInput);
        setInitialFocus(noteInput);

        int btnW = (SCREEN_W - 6) / 2;
        String saveLabel  = namingNewFolder ? "Crear" : "Guardar";
        String otherLabel = namingNewFolder ? "Cancelar" : "+ Nueva";

        addRenderableWidget(Button.builder(Component.literal(saveLabel), b -> {
            if (namingNewFolder) {
                String name = noteInput != null ? noteInput.getValue().trim() : "";
                if (!name.isEmpty()) noteEditFolder = name;
                namingNewFolder = false;
                if (noteInput != null) noteInput.setValue("");
                initNotes();
            } else {
                saveCurrentNote();
            }
        }).pos(sx() + 2, saveY).size(btnW - 1, 11).build());

        addRenderableWidget(Button.builder(Component.literal(otherLabel), b -> {
            if (namingNewFolder) {
                namingNewFolder = false;
                if (noteInput != null) noteInput.setValue("");
                initNotes();
            } else {
                resetNoteEditor();
            }
        }).pos(sx() + btnW + 3, saveY).size(btnW - 1, 11).build());
    }

    
    private List<String> collectFolders() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("");
        for (PhoneData.Note n : phoneData.notes) {
            if (n.folder != null && !n.folder.isEmpty()) set.add(n.folder);
        }
        if (noteEditFolder != null && !noteEditFolder.isEmpty()) set.add(noteEditFolder);
        return new ArrayList<>(set);
    }

    
    private void cycleEditFolder() {
        List<String> folders = collectFolders();
        int idx = folders.indexOf(noteEditFolder);
        if (idx < 0) idx = 0;
        if (idx + 1 < folders.size()) {
            noteEditFolder = folders.get(idx + 1);
        } else {
            namingNewFolder = true;
            if (noteInput != null) noteInput.setValue("");
        }
        initNotes();
    }

    
    private void cycleFolderFilter() {
        List<String> folders = new ArrayList<>();
        folders.add(null); // "Todas"
        for (String f : collectFolders()) {
            if (!f.isEmpty()) folders.add(f);
        }
        int idx = folders.indexOf(notesFolderFilter);
        if (idx < 0) idx = 0;
        notesFolderFilter = folders.get((idx + 1) % folders.size());
        initNotes();
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
            n.folder = noteEditFolder;
            n.pinned = noteEditPinned;
            n.updatedAt = System.currentTimeMillis();
        } else {
            PhoneData.Note n = new PhoneData.Note(finalText);
            n.folder = noteEditFolder;
            n.pinned = noteEditPinned;
            phoneData.notes.add(n);
        }
        resetNoteEditor();
    }

    
    private void resetNoteEditor() {
        selectedNote = -1;
        noteDraftBuffer = "";
        noteEditFolder = "";
        noteEditPinned = false;
        namingNewFolder = false;
        if (noteInput != null) noteInput.setValue("");
        initNotes();
    }

    
    private void startEditNote(int index) {
        PhoneData.Note n = phoneData.notes.get(index);
        selectedNote = index;
        String[] lines = n.text.split("\n", -1);
        if (lines.length > 1) {
            noteDraftBuffer = String.join("\n", Arrays.copyOf(lines, lines.length - 1));
            if (noteInput != null) noteInput.setValue(lines[lines.length - 1]);
        } else {
            noteDraftBuffer = "";
            if (noteInput != null) noteInput.setValue(n.text);
        }
        noteEditFolder = n.folder != null ? n.folder : "";
        noteEditPinned = n.pinned;
        namingNewFolder = false;
        initNotes();
    }

    
    private void togglePinNote(int index) {
        if (index >= 0 && index < phoneData.notes.size()) {
            phoneData.notes.get(index).pinned = !phoneData.notes.get(index).pinned;
        }
    }

    
    private List<Integer> getFilteredSortedNoteIndices() {
        List<PhoneData.Note> all = phoneData.notes;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            String f = all.get(i).folder != null ? all.get(i).folder : "";
            if (notesFolderFilter == null || f.equals(notesFolderFilter)) indices.add(i);
        }
        indices.sort((a, b) -> {
            boolean pa = all.get(a).pinned, pb = all.get(b).pinned;
            if (pa != pb) return pa ? -1 : 1;
            return Long.compare(all.get(b).updatedAt, all.get(a).updatedAt);
        });
        return indices;
    }

    
    private String stripMarkup(String line) {
        String s = line;
        if (s.startsWith("- ") || s.startsWith("* ")) s = "\u2022 " + s.substring(2);
        return s.replace("**", "");
    }

    
    private void drawFormattedLine(GuiGraphics g, String rawLine, int x, int y, int color, int maxW) {
        String line = rawLine;
        String prefix = "";
        if (line.startsWith("- ") || line.startsWith("* ")) {
            prefix = "\u2022 ";
            line = line.substring(2);
        }
        String plain = prefix + line.replace("**", "");
        if (font.width(plain) > maxW) {
            g.drawString(font, truncate(plain, maxW), x, y, color, false);
            return;
        }
        MutableComponent comp = Component.literal(prefix);
        String[] parts = line.split("\\*\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            boolean bold = (i % 2 == 1);
            comp.append(Component.literal(parts[i]).withStyle(s -> s.withBold(bold)));
        }
        g.drawString(font, comp, x, y, color, false);
    }

    private void initSettings() {
        int y = appY() + 18; // Dejar espacio para el header (título + separador)
        int bw = SCREEN_W - 10;

        addRenderableWidget(Button.builder(Component.literal("Tema: " + phoneData.theme), b -> {
            int idx = Arrays.asList(THEMES).indexOf(phoneData.theme);
            phoneData.theme = THEMES[(idx + 1) % THEMES.length];
            saveSettings(); initCurrentApp();
        }).pos(sx() + 5, y).size(bw, 12).build());

        y += 15;
        addRenderableWidget(Button.builder(Component.literal("Carcasa: " + phoneData.currentCase), b -> {
            int idx = Arrays.asList(CASES).indexOf(phoneData.currentCase);
            phoneData.currentCase = CASES[(idx + 1) % CASES.length];
            saveSettings(); initCurrentApp();
        }).pos(sx() + 5, y).size(bw, 12).build());

        y += 15;
        addRenderableWidget(Button.builder(Component.literal("Sonido: " + phoneData.notificationSound), b -> {
            int idx = Arrays.asList(SOUNDS).indexOf(phoneData.notificationSound);
            phoneData.notificationSound = SOUNDS[(idx + 1) % SOUNDS.length];
            saveSettings(); initCurrentApp();
        }).pos(sx() + 5, y).size(bw, 12).build());

        y += 15;
        addRenderableWidget(Button.builder(
                Component.literal("No molestar: " + (phoneData.doNotDisturb ? "ON" : "OFF")),
                b -> { phoneData.doNotDisturb = !phoneData.doNotDisturb; saveSettings(); initCurrentApp(); }
        ).pos(sx() + 5, y).size(bw, 12).build());

        y += 15;
        addRenderableWidget(Button.builder(Component.literal("Privacidad"), b -> {
            goToApp(App.PRIVACY);
        }).pos(sx() + 5, y).size(bw, 12).build());

        y += 15;
        addRenderableWidget(Button.builder(Component.literal("Editor de Iconos"), b -> {
            goToApp(App.ICON_EDITOR);
        }).pos(sx() + 5, y).size(bw, 12).build());

        y += 15;
        int wpIdx = 0;
        for (int i = 0; i < WALLPAPER_IDS.length; i++) {
            if (WALLPAPER_IDS[i].equals(phoneData.wallpaper)) { wpIdx = i; break; }
        }
        String wpName = WALLPAPER_NAMES[wpIdx];
        addRenderableWidget(Button.builder(Component.literal("Fondo: " + wpName), b -> {
            int idx = 0;
            for (int i = 0; i < WALLPAPER_IDS.length; i++) {
                if (WALLPAPER_IDS[i].equals(phoneData.wallpaper)) { idx = i; break; }
            }
            phoneData.wallpaper = WALLPAPER_IDS[(idx + 1) % WALLPAPER_IDS.length];
            saveSettings(); initCurrentApp();
        }).pos(sx() + 5, y).size(bw, 12).build());

        settingsContentBottom = y + 12; // borde inferior de este último botón
    }

    private void initPrivacy() {
        privacyScrollOffset = 0;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (!goBack()) goToApp(App.SETTINGS);
        }).pos(sx() + 2, appY() + 2).size(18, 10).build());
    }

    
    private float lastDelta = 1.0f;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        lastDelta = delta;
        if (suppressRender) return;

        if (cameraLayout) {
            updateCameraLook();
            renderCurrentApp(g, mouseX, mouseY);
        } else {
            renderBackground(g, mouseX, mouseY, delta);

            renderPhoneFrame(g);
            renderStatusBar(g);
            renderCurrentApp(g, mouseX, mouseY);
            renderNavBar(g, mouseX, mouseY);
        }

        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, delta);
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
        return CASE_LOCS[0]; // fallback to default
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
            case MAPA      -> renderMapa(g, mouseX, mouseY);
            case SETTINGS  -> renderSettings(g, mouseX, mouseY);
            case ICON_EDITOR -> renderIconEditor(g, mouseX, mouseY);
            case CONTACTS  -> renderContacts(g, mouseX, mouseY);
            case PRIVACY   -> renderPrivacy(g, mouseX, mouseY);
            case CREATE_GROUP -> renderCreateGroup(g, mouseX, mouseY);
        }
    }

    private void renderHome(GuiGraphics g, int mx, int my) {
        renderWallpaper(g, sx(), appY(), SCREEN_W, APP_H);

        g.fill(sx(), appY(), sx() + SCREEN_W, appY() + 30, 0x22000000);

        int[] iconCols = {0, 1, 2, 3, 4, 5, 6, 7, 8}; // columna en spritesheet

        String[] labels = phoneData.appIconLabels;
        String[] appKeys = {"CHAT","CAMERA","PHOTOS","WEATHER","CLOCK","NOTES","MAPA","SETTINGS","CONTACTS"};

        int cols    = 3;
        int cellW   = SCREEN_W / cols;           // 46px por celda
        int usableH = APP_H - NAV_H;
        int cellH   = usableH / 3;                // alto por fila, sin invadir la nav bar
        int iconSz  = 22;                         // tamaño visual del ícono (escalado 16→22)
        int iconHalf = iconSz / 2;
        int labelMaxW = cellW - 4;               // ancho máximo para el label

        for (int i = 0; i < 9; i++) {
            int col = i % cols;
            int row = i / cols;

            int cx = sx() + col * cellW + cellW / 2;
            int cy = appY() + row * cellH + cellH / 2 - 4;

            int iconBg = getAppIconColor(appKeys[i]);
            g.fill(cx - iconHalf + 1, cy - iconHalf + 1, cx + iconHalf + 1, cy + iconHalf + 1, 0x33000000); // sombra
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

            if (appKeys[i].equals("CHAT") && getTotalUnreadCount() > 0) {
                int bx = cx + iconHalf - 3;
                int by = cy - iconHalf - 3;
                g.fill(bx, by, bx + 8, by + 8, 0xFFFF3333);
                String unreadStr = String.valueOf(Math.min(getTotalUnreadCount(), 9));
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
        g.drawString(font, "Contactos", sx() + 20, appY() + 4, getThemeColor(), false);
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
            String status = online ? "En línea" : "Desconectado";
            g.drawString(font, status, sx() + 23, y + 12, online ? 0xFF4CAF50 : subColor, false);

            y += rowH;
        }

        if (ordered.isEmpty()) {
            g.drawCenteredString(font, "Todavía no hay otros jugadores", sx() + SCREEN_W / 2, appY() + 90, subColor);
        }
    }

    
    private void renderCreateGroup(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222;
        int subColor = phoneData.darkMode ? 0xFF888888 : 0xFF666666;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        g.drawString(font, "Nuevo grupo", sx() + 24, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        g.drawString(font, "Nombre:", sx() + 5, appY() + 16, subColor, false);

        g.drawString(font, "Seleccionar miembros:", sx() + 5, appY() + 34, subColor, false);

        List<String> contacts = orderedContacts();
        int rowH = 20;
        int listTop = appY() + 44;
        int listBottom = appBottom() - NAV_H - 16; // dejar espacio para el botón crear

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
            g.drawString(font, online ? "En línea" : "Desconectado",
                    sx() + 33, y + 11, online ? 0xFF4CAF50 : subColor, false);

            y += rowH;
        }

        if (contacts.isEmpty()) {
            g.drawCenteredString(font, "No hay contactos disponibles",
                    sx() + SCREEN_W / 2, listTop + 20, subColor);
        }

        if (!groupSelectedMembers.isEmpty()) {
            String sel = groupSelectedMembers.size() + " seleccionado(s)";
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
        List<String> ordered = orderedContacts();
        int y = appY() + 17 - contactsScrollOffset;
        for (String name : ordered) {
            if (my >= y - 1 && my < y + 21 && mx >= sx() + 1 && mx < sx() + SCREEN_W - 1) {
                openChatWith(name);
                return true;
            }
            y += 22;
        }
        return false;
    }

    
    private void openChatWith(String player) {
        String myName = Minecraft.getInstance().getUser().getName();
        if (player.equals(myName)) return; // no hablar con uno mismo

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
        String rest = convId.substring(3); // quitar "dm_"
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
        g.drawString(font, "Chats", sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        int nameMaxW  = SCREEN_W - 26 - 22;   // desde x=23 hasta badge
        int previewMaxW = SCREEN_W - 26 - 22;

        int totalConvH = phoneData.conversations.size() * 22;
        int visibleConvH = appBottom() - NAV_H - (appY() + 17);
        int maxConvScroll = Math.max(0, totalConvH - visibleConvH);
        if (chatListScrollOffset > maxConvScroll) chatListScrollOffset = maxConvScroll;

        int y = appY() + 17 - chatListScrollOffset;
        for (PhoneData.ChatConversation conv : phoneData.conversations) {
            if (y + 21 < appY() + 17) { y += 22; continue; } // fuera de vista arriba
            if (y + 21 > appBottom() - NAV_H) break;
            int rowBg = phoneData.darkMode ? 0xFF16213E : 0xFFF5F5F5;
            g.fill(sx() + 1, y - 1, sx() + SCREEN_W - 1, y + 20, rowBg);

            drawConversationAvatar(g, conv, sx() + 3, y + 1, 16);

            String mutedPrefix = conv.muted ? "[M] " : "";
            String name = truncate(mutedPrefix + conv.name, nameMaxW);
            g.drawString(font, name, sx() + 23, y + 2, textColor, false);

            String preview = conv.messages.isEmpty() ? "Sin mensajes"
                    : conv.messages.get(conv.messages.size() - 1).content;
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
            g.drawCenteredString(font, "Sin conversaciones", sx() + SCREEN_W / 2, appY() + 90, subColor);
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
                i++; // saltar el carácter del código
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
        int avatarX = sx() + 22; // espacio para el botón "<" a la izquierda
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
        int nameMaxW = (sx() + SCREEN_W - 28) - nameX; // hasta el botón [S]/[M] de la derecha
        g.drawString(font,
                truncate(currentConv.name, nameMaxW),
                nameX, appY() + 5,
                phoneData.darkMode ? 0xFFFFFFFF : 0xFF222222, false);

        String myName = Minecraft.getInstance().getUser().getName();
        int maxBubW = SCREEN_W - 10;
        int maxTextW = maxBubW - 10; // ancho máximo de texto dentro de la burbuja

        java.util.List<Integer> msgHeights = new java.util.ArrayList<>();
        for (PhoneData.ChatMessage msg : currentConv.messages) {
            if (msg.deletedForAll) { msgHeights.add(12); continue; }
            boolean isMe2 = msg.sender.equals(myName);
            int h;
            if (msg.content.startsWith("§§PHOTO:")) {
                h = 56;
            } else {
                java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                    wrapMessageLines(msg.content, maxTextW);
                h = lines.size() * 10 + 8; // padding vertical
            }
            if (currentConv.isGroup && !isMe2) h += 10; // espacio nombre
            msgHeights.add(h + 4); // +4 gap entre mensajes
        }
        boolean hasPinned = !currentConv.pinnedMessages.isEmpty();
        int pinnedH = hasPinned ? 8 : 0;
        int msgsTop = appY() + 20 + pinnedH;
        if (hasPinned) {
            g.fill(sx(), appY() + 18, sx() + SCREEN_W, appY() + 18 + pinnedH, 0x44FFD700);
            g.drawString(font, "📌 " + currentConv.pinnedMessages.size() + " fijado(s)",
                    sx() + 3, appY() + 19, 0xFFFFD700, false);
        }

        int totalH = msgHeights.stream().mapToInt(Integer::intValue).sum();
        int visibleH = appBottom() - NAV_H - msgsTop - 20; // -20 para input
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

        for (int i = 0; i < currentConv.messages.size(); i++) {
            PhoneData.ChatMessage msg = currentConv.messages.get(i);
            int mh = msgHeights.get(i);
            if (msgY + mh < clipTop) { msgY += mh; continue; } // fuera de pantalla arriba
            if (msgY > clipBottom) break;                        // fuera de pantalla abajo

            if (msg.deletedForAll) {
                if (msgY >= clipTop) g.drawString(font, "[Eliminado]", sx() + 5, msgY, 0xFF888888, false);
                msgY += 12;
                continue;
            }
            boolean isMe = msg.sender.equals(myName);
            int bubbleBg = isMe ? getThemeColor() : (phoneData.darkMode ? 0xFF2D2D44 : 0xFFDDDDDD);
            int textC = isMe ? 0xFFFFFFFF : (phoneData.darkMode ? 0xFFEEEEEE : 0xFF222222);

            if (currentConv.isGroup && !isMe) {
                if (msgY >= clipTop && msgY + 9 <= clipBottom)
                    g.drawString(font, msg.sender, sx() + 5, msgY, 0xFFAAAAAA, false);
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
                wrapMessageLines(msg.content, maxTextW);
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
        String recLabel = "R: Foto";
        g.drawString(font, recLabel, vpLeft + 6, vpTop + 4, 0xFFFFFFFF, false);
        boolean teleZoom = cameraFov <= CAM_FOV_TELE_THRESHOLD;
        String perspName = switch (cameraPerspective) {
            case 1 -> "3ra Atrás";
            case 2 -> "3ra Frente";
            default -> "1ra";
        };
        String hint = "F: " + perspName + "  G: Filtro  Shift: Salir";
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

        if (teleZoom) {
            float depth = (float) (CAM_FOV_TELE_THRESHOLD - cameraFov) / (CAM_FOV_TELE_THRESHOLD - CAM_FOV_MIN);
            depth = Math.max(0f, Math.min(1f, depth));
            int vignetteAlpha = (int) (0x18 + depth * 0x70); // 0x18..0x88
            int vc = vignetteAlpha << 24; // negro semitransparente
            int band = (int) (4 + depth * 18);
            g.fill(vpLeft, innerTop, vpRight, innerTop + band, vc);
            g.fill(vpLeft, vpBottom - band, vpRight, vpBottom, vc);
            g.fill(vpLeft, innerTop, vpLeft + band, vpBottom, vc);
            g.fill(vpRight - band, innerTop, vpRight, vpBottom, vc);
        }

        int cx = (vpLeft + vpRight) / 2;

        int vpW = vpRight - vpLeft;
        int vpH = vpBottom - innerTop;
        long now_ms = System.currentTimeMillis();
        switch (selectedFilter) {
            case "enderman" -> {
                float pulse = 0.5f + 0.5f * (float)Math.sin(now_ms / 200.0);
                int band = (int)(vpW * 0.28 + pulse * vpW * 0.06);
                int ea = (int)(0x99 + pulse * 0x33);
                int ec = (ea << 24) | 0x1A0033;
                g.fill(vpLeft, innerTop, vpLeft + band, vpBottom, ec);
                g.fill(vpRight - band, innerTop, vpRight, vpBottom, ec);
                g.fill(vpLeft + band, innerTop, vpRight - band, innerTop + band / 2, ec);
                g.fill(vpLeft + band, vpBottom - band / 2, vpRight - band, vpBottom, ec);
            }
            case "bat" -> {
                int bb = vpW * 2 / 5;
                int batAlpha = 0xCC;
                int bc = (batAlpha << 24);
                g.fill(vpLeft, innerTop, vpLeft + bb, vpBottom, bc);
                g.fill(vpRight - bb, innerTop, vpRight, vpBottom, bc);
                g.fill(vpLeft + bb, innerTop, vpRight - bb, innerTop + bb / 3, bc);
                g.fill(vpLeft + bb, vpBottom - bb / 3, vpRight - bb, vpBottom, bc);
                float bpulse = (now_ms % 1200) / 1200.0f;
                int bpx = vpLeft + vpW / 2;
                int bpy = innerTop + vpH / 2;
                int bpr = (int)(bpulse * vpW / 3);
                if (bpr > 0) {
                    int bpa = (int)((1f - bpulse) * 0x44);
                    int bpc = (bpa << 24) | 0x00CCCC;
                    g.fill(bpx - bpr, bpy - 1, bpx + bpr, bpy + 1, bpc);
                    g.fill(bpx - 1, bpy - bpr, bpx + 1, bpy + bpr, bpc);
                }
            }
            case "creeper" -> {
                int cl = 0x22003300;
                for (int sy = innerTop; sy < vpBottom; sy += 4) {
                    g.fill(vpLeft, sy + 2, vpRight, sy + 3, cl);
                }
                int chl = 0xFF00FF44;
                int chSize = 10;
                g.fill(cx - chSize, innerTop + vpH / 2, cx + chSize, innerTop + vpH / 2 + 1, chl);
                g.fill(cx, innerTop + vpH / 2 - chSize, cx + 1, innerTop + vpH / 2 + chSize, chl);
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
            default -> {}  // los demás filtros solo usan el tinte ARGB de getFilterColor()
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

        String count = "Fotos: " + phoneData.photos.size();
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
        if (bw < 4) return; // bisel demasiado fino (no debería pasar)

        int margin = Math.max(8, (pfBottom - pfTop) / 8);
        int minTopMargin = 23; // alto label de grados + separación + "+" + separación
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
        g.drawString(font, "Album de Fotos", sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        int subColor = phoneData.darkMode ? 0xFF888888 : 0xFF666666;

        int cols = 3;
        int cellSize = (SCREEN_W - 6) / cols;
        int headerH = 18; // altura del header (hasta el separador)
        int gridStartY = appY() + headerH;

        int totalPhotos = phoneData.photos.size();
        int totalRows = (totalPhotos + cols - 1) / cols;
        int totalGridH = totalRows * cellSize;
        int visibleH = appBottom() - gridStartY;
        int maxScroll = Math.max(0, totalGridH - visibleH + 4); // +4px padding para ver la última fila completa
        if (photosScrollOffset > maxScroll) photosScrollOffset = maxScroll;
        if (photosScrollOffset < 0) photosScrollOffset = 0;

        for (int i = 0; i < totalPhotos; i++) {
            PhoneData.PhotoEntry photo = phoneData.photos.get(i);
            int col = i % cols;
            int row = i / cols;
            int px = sx() + 3 + col * cellSize;
            int py = gridStartY + row * cellSize - photosScrollOffset;

            if (py + cellSize <= gridStartY) continue;
            if (py >= appBottom()) break;

            int drawY = Math.max(py, gridStartY);
            int clipH = Math.min(py + cellSize, appBottom()) - drawY;
            if (clipH <= 0) continue;

            ResourceLocation texLoc = getOrLoadPhotoTexture(photo.filename);
            if (texLoc != null) {
                int[] dims = photoDimsCache.get(photo.filename);
                int srcW = dims != null ? dims[0] : (cellSize - 2);
                int srcH = dims != null ? dims[1] : (cellSize - 2);
                int squareSrc = Math.min(srcW, srcH);
                int uvX = (srcW - squareSrc) / 2;
                int uvY = (srcH - squareSrc) / 2;
                int dstSize = cellSize - 2; // tamaño destino cuadrado en pantalla

                int clipTop = Math.max(0, gridStartY - py);
                int clipBot = Math.max(0, (py + dstSize) - appBottom());
                int visH = dstSize - clipTop - clipBot;
                if (visH <= 0) continue;

                int uvClipTop = clipTop * squareSrc / dstSize;
                int uvClipBot = clipBot * squareSrc / dstSize;
                int uvH = squareSrc - uvClipTop - uvClipBot;

                g.blit(texLoc,
                        px, py + clipTop,          // destino X, Y
                        dstSize, visH,              // destino W, H
                        uvX, uvY + uvClipTop,       // UV X, Y
                        squareSrc, uvH,             // UV W, H
                        srcW, srcH);               // textura total
            } else {
                g.fill(px, drawY, px + cellSize - 2, drawY + clipH, getPhotoColor(photo.filter));
            }

            if (py + (cellSize - 2) > gridStartY && py < appBottom()) {
                if (photo.selfie) {
                    g.drawString(font, "🤳", px + cellSize - 14, Math.max(py, gridStartY) + 2, 0xFFFFFFFF, false);
                }
            }
        }

        if (totalPhotos == 0) {
            g.drawCenteredString(font, "Sin fotos", sx() + SCREEN_W / 2, appY() + 74, subColor);
            g.drawCenteredString(font, "Usa la cámara", sx() + SCREEN_W / 2, appY() + 86, subColor);
            g.drawCenteredString(font, "para tomar fotos", sx() + SCREEN_W / 2, appY() + 98, subColor);
        }

        if (totalGridH > visibleH && totalPhotos > 0) {
            int trackX  = sx() + SCREEN_W - 3;
            int trackTop = gridStartY + 2;
            int trackBot = appBottom() - 2;
            int trackH  = trackBot - trackTop;
            g.fill(trackX, trackTop, trackX + 2, trackBot, 0x44FFFFFF);
            float ratio = (float) photosScrollOffset / maxScroll;
            int thumbH = Math.max(8, trackH * visibleH / totalGridH);
            int thumbY = trackTop + (int)((trackH - thumbH) * ratio);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF);
        }
    }

    
    private void renderPhotoViewer(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF0D0D1A : 0xFF111111;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        PhoneData.PhotoEntry photo = phoneData.photos.get(photoViewerIndex);

        int headerH = 14;
        int hdrBg = phoneData.darkMode ? 0xCC16213E : 0xCC222222;
        g.fill(sx(), appY(), sx() + SCREEN_W, appY() + headerH, hdrBg);
        g.drawString(font, "< Volver", sx() + 3, appY() + 3, 0xFFFFFFFF, false);
        String counter = (photoViewerIndex + 1) + "/" + phoneData.photos.size();
        g.drawString(font, counter, sx() + SCREEN_W - font.width(counter) - 3, appY() + 3, 0xFFAAAAAA, false);

        int imgAreaY = appY() + headerH + 2;
        int totalH = appBottom() - imgAreaY - 2;
        int metaH = 36;  // altura del panel de info (3 líneas)
        int navH2 = 18;  // altura de la franja de botones nav
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
            g.drawCenteredString(font, "Sin previsualización",
                    sx() + SCREEN_W / 2, imgAreaY + dispH / 2 - 5, 0xFF888888);
        }

        int contentBottom = appBottom() - NAV_H; // límite real de contenido
        int navBtnH = 14;
        int navBtnGap = 4; // espacio entre botones de nav y borde inferior
        int navAreaY = contentBottom - navBtnH - navBtnGap; // flechas < > abajo del todo

        int shareBtnH = 14;
        int shareBtnGap = 3;
        int shareAreaY = navAreaY - shareBtnH - shareBtnGap;
        int shareW = SCREEN_W - 10; // ancho completo (menos márgenes)
        int shareBtnX = sx() + 5;

        int infoZoneTop = imgAreaY + dispH + 4;
        int infoZoneH = shareAreaY - infoZoneTop - 4; // espacio entre imagen y botón Enviar

        int maxW = SCREEN_W - 8;
        String rawName = photo.filename;
        String nameLine1, nameLine2 = null;
        if (font.width(rawName) <= maxW) {
            nameLine1 = rawName;
        } else {
            int split = rawName.length();
            while (split > 1 && font.width(rawName.substring(0, split)) > maxW) split--;
            nameLine1 = rawName.substring(0, split);
            String rest = rawName.substring(split);
            while (rest.length() > 2 && font.width(rest) > maxW) rest = rest.substring(0, rest.length() - 1);
            if (!rawName.substring(split).equals(rest)) rest = rest + "..";
            nameLine2 = rest;
        }

        int lineGap = 3;
        int lineH = 8;
        int numLines = (nameLine2 != null ? 2 : 1) + 2; // nombre(1-2) + filtro + coords
        int totalTextH = numLines * lineH + (numLines - 1) * lineGap;
        int infoStartY = infoZoneTop + Math.max(0, (infoZoneH - totalTextH) / 2);

        int curY = infoStartY;
        g.drawCenteredString(font, nameLine1, sx() + SCREEN_W / 2, curY, 0xFFDDDDDD);
        curY += lineH + lineGap;
        if (nameLine2 != null) {
            g.drawCenteredString(font, nameLine2, sx() + SCREEN_W / 2, curY, 0xFFDDDDDD);
            curY += lineH + lineGap;
        }

        String filterLabel = photo.filter.equals("none") ? "Sin filtro" : "Filtro: " + photo.filter.toUpperCase();
        int filterColor = photo.filter.equals("none") ? 0xFF888888 : 0xFF88EEFF;
        String line2 = filterLabel + (photo.selfie ? "  Selfie" : "");
        g.drawCenteredString(font, line2, sx() + SCREEN_W / 2, curY, filterColor);
        curY += lineH + lineGap;

        String coordStr = photo.worldX + ", " + photo.worldY + ", " + photo.worldZ;
        g.drawCenteredString(font, coordStr, sx() + SCREEN_W / 2, curY, 0xFFAAFFAA);

        g.fill(shareBtnX, shareAreaY, shareBtnX + shareW, shareAreaY + shareBtnH, 0xCC1565C0);
        g.drawCenteredString(font, "📤 Enviar al chat", shareBtnX + shareW / 2, shareAreaY + 3, 0xFFFFFFFF);

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

        if (photoShareMenuOpen && photoShareIndex == photoViewerIndex) {
            renderPhotoShareMenu(g, mx, my, photo.filename);
        }
    }

    
    private void renderPhotoShareMenu(GuiGraphics g, int mx, int my, String filename) {
        int menuX = sx() + 5;
        int menuW = SCREEN_W - 10;
        int menuTop = appY() + 20;
        int rowH = 18;
        int maxRows = Math.min(phoneData.conversations.size(), 6);
        int menuH = maxRows * rowH + 24;
        int menuBottom = menuTop + menuH;

        g.fill(menuX - 1, menuTop - 1, menuX + menuW + 1, menuBottom + 1, 0xFF333333);
        g.fill(menuX, menuTop, menuX + menuW, menuBottom, phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF);
        g.drawCenteredString(font, "Compartir en...", menuX + menuW / 2, menuTop + 4,
                getThemeColor());
        g.fill(menuX, menuTop + 14, menuX + menuW, menuTop + 15, 0x44AAAAAA);

        int y = menuTop + 17;
        int shown = 0;
        for (PhoneData.ChatConversation conv : phoneData.conversations) {
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

        g.fill(menuX, menuBottom - 10, menuX + menuW, menuBottom, 0xAAFF3333);
        g.drawCenteredString(font, "✖ Cancelar", menuX + menuW / 2, menuBottom - 8, 0xFFFFFFFF);
    }

    
    private void sharePhotoToConversation(PhoneData.ChatConversation conv, String filename) {
        if (conv == null || filename == null || filename.isEmpty()) return;
        if (com.icraft.server.PhoneServerHandler.GLOBAL_GROUP_ID.equals(conv.id)) {
            notifications.add("⚠ No podés compartir fotos en el chat global.");
            photoShareMenuOpen = false;
            return;
        }

        try {
            Path photoFile = getPhotosDir().resolve(filename);
            if (Files.exists(photoFile)) {
                byte[] bytes = Files.readAllBytes(photoFile);
                if (bytes.length <= 512 * 1024) {
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    PacketDistributor.sendToServer(new com.icraft.network.PhotoUploadPacket(
                            filename, base64, conv.id, conv.isGroup,
                            conv.isGroup ? "" : conv.name));
                } else {
                    notifications.add("⚠ La foto es muy grande (máx. 512 KB).");
                    photoShareMenuOpen = false;
                    return;
                }
            }
        } catch (Exception e) {
            ICraftMod.LOGGER.warn("[iCraft] No se pudo leer \"{}\" para compartir: {}", filename, e.getMessage());
        }

        String content = "§§PHOTO:" + filename;
        String myName = Minecraft.getInstance().getUser().getName();
        PhoneData.ChatMessage m = new PhoneData.ChatMessage(myName, content);
        conv.messages.add(m);
        receivedMessageIds.add(m.id);
        PacketDistributor.sendToServer(new SendChatPacket(
                conv.id, conv.isGroup ? "" : conv.name,
                content, conv.isGroup, false, m.id));
        photoShareMenuOpen = false;
        notifications.add("📤 Foto compartida en \"" + conv.name + "\"");
    }

    
    private ResourceLocation getOrLoadPhotoTexture(String filename) {
        if (photoTextureCache.containsKey(filename)) {
            return photoTextureCache.get(filename); // may be null (failed load, cached)
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
            ICraftMod.LOGGER.warn("Could not load photo texture {}: {}", filename, e.getMessage());
            photoTextureCache.put(filename, null);
            return null;
        }
    }

    private void renderWeather(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF0F3460 : 0xFF4A90D9;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 3, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, "Clima", sx() + 20, appY() + 4, 0xFFFFFFFF, false);

        String weatherStr = "Despejado";
        String tempStr = "20 C";
        String timeOfDay = "Dia";

        if (Minecraft.getInstance().level != null) {
            var level = Minecraft.getInstance().level;
            if (level.isThundering()) { weatherStr = "Tormenta"; tempStr = "8 C"; }
            else if (level.isRaining())  { weatherStr = "Lluvia";   tempStr = "14 C"; }

            long t = level.getDayTime() % 24000;
            if      (t < 6000)  timeOfDay = "Mañana";
            else if (t < 12000) timeOfDay = "Tarde";
            else if (t < 18000) timeOfDay = "Noche";
            else                timeOfDay = "Madrugada";
        }

        int boxSize = 56;
        int boxX = sx() + SCREEN_W / 2 - boxSize / 2;
        int boxY = appY() + 20;
        g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0x22FFFFFF);
        int bigIconSize = 40;
        int bigIconX = boxX + (boxSize - bigIconSize) / 2;
        int bigIconY = boxY + (boxSize - bigIconSize) / 2;
        blitIcon(g, 3, 0, bigIconX, bigIconY, bigIconSize);

        int textY = boxY + boxSize + 8;
        g.drawCenteredString(font, weatherStr, sx() + SCREEN_W / 2, textY,      0xFFFFFFFF);
        g.drawCenteredString(font, tempStr,    sx() + SCREEN_W / 2, textY + 12, 0xFFFFFF88);
        g.drawCenteredString(font, timeOfDay,  sx() + SCREEN_W / 2, textY + 24, 0xFFCCFFFF);

        int dividerY = textY + 40;
        g.fill(sx() + 10, dividerY, sx() + SCREEN_W - 10, dividerY + 1, 0x44FFFFFF);

        String[] days  = {"Mañana","Tarde","Noche"};
        String[] temps = {"22 C",  "18 C", "12 C"};
        int rowY = dividerY + 10;
        int rowMargin = 10;
        int rowW = SCREEN_W - rowMargin * 2;
        int colW = rowW / 3;
        int rowX = sx() + rowMargin;
        float dayScale = 1f;
        for (String d : days) {
            float w = font.width(d);
            if (w * dayScale > colW - 4) dayScale = (colW - 4) / w;
        }
        dayScale = Math.min(dayScale, 1f);
        for (int i = 0; i < 3; i++) {
            int colCenterX = rowX + i * colW + colW / 2;
            blitIcon(g, 3, 0, colCenterX - ICON_SIZE / 2, rowY, ICON_SIZE);
            drawScaledCenteredString(g, days[i], colCenterX, rowY + 20, dayScale, 0xFFCCCCCC);
            g.drawCenteredString(font, temps[i], colCenterX, rowY + 30, 0xFFFFFF88);
        }
    }

    
    private void initClock() {
        clearWidgets();

        int tabY = appY() + 17;
        int tabH = 13;
        int tabGap = 2;
        String[] tabLabels = {"Reloj", "Cron", "Temp"};
        int tabW = (SCREEN_W - 6 - tabGap * 2) / 3;

        for (int i = 0; i < tabLabels.length; i++) {
            final int idx = i;
            int tx = sx() + 3 + i * (tabW + tabGap);
            addRenderableWidget(Button.builder(Component.literal(tabLabels[i]), b -> {
                clockTab = idx;
                initClock();
            }).pos(tx, tabY).size(tabW, tabH).build());
        }

        if (clockTab == 1) {
            int top = clockTabContentTop(CLOCK_TAB1_CONTENT_H);
            int btnY = top + 36;
            int btnW = (SCREEN_W - 16) / 2;
            addRenderableWidget(Button.builder(
                    Component.literal(stopwatchRunning ? "Pausar" : "Iniciar"),
                    b -> toggleStopwatch()
            ).pos(sx() + 5, btnY).size(btnW, 14).build());

            addRenderableWidget(Button.builder(Component.literal("Reiniciar"), b -> resetStopwatch())
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
                    addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> adjustTimer(d))
                            .pos(sx() + 4 + i * (adjW + 2), adjY).size(adjW, 12).build());
                }
            }

            int btnY = top + (timerRunning ? 36 : 54);
            int btnW = (SCREEN_W - 16) / 2;
            addRenderableWidget(Button.builder(
                    Component.literal(timerRunning ? "Pausar" : "Iniciar"),
                    b -> toggleTimer()
            ).pos(sx() + 5, btnY).size(btnW, 14).build());

            addRenderableWidget(Button.builder(Component.literal("Reiniciar"), b -> resetTimer())
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
        g.drawString(font, "Reloj", sx() + 20, appY() + 4, getThemeColor(), false);
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
        g.drawCenteredString(font, "Hora del Overworld", centerX, overworldLabelY, subColor);
        g.drawCenteredString(font, "Día " + mcDay, centerX, top + 32, getThemeColor());

        g.fill(centerX - SCREEN_W / 2 + 10, top + 44, centerX + SCREEN_W / 2 - 10, top + 45, 0x44AAAAAA);

        LocalDateTime now = LocalDateTime.now();
        String localTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String localDateStr = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        g.drawCenteredString(font, "Hora local (PC)", centerX, top + 54, subColor);
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
        g.drawCenteredString(font, stopwatchRunning ? "En marcha..." : "Detenido", centerX, top + 22, subColor);
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

        String status = timerFinished ? "¡Tiempo cumplido!"
                : timerRunning ? "Cuenta regresiva..." : "Listo para iniciar";
        g.drawCenteredString(font, status, centerX, top + 22, timerFinished ? 0xFFFF5555 : subColor);
    }

    private void renderNotes(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFDE7;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        int textColor = phoneData.darkMode ? 0xFFFFFFFF : 0xFF333333;
        int subColor  = phoneData.darkMode ? 0xFF888888 : 0xFF666666;

        blitIcon(g, 5, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, "Notas", sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        if (isEditingNote()) {
            renderNoteEditorPreview(g, textColor, subColor);
        } else {
            renderNotesList(g, textColor, subColor);
        }
    }

    private void renderNotesList(GuiGraphics g, int textColor, int subColor) {
        int noteMaxW = SCREEN_W - 24; // espacio para el pin a la izq. y la X a la der.
        List<PhoneData.Note> all = phoneData.notes;
        List<Integer> indices = getFilteredSortedNoteIndices();

        int y = appY() + 29;
        int bottomLimit = appBottom() - NAV_H - 53;
        for (int idx : indices) {
            if (y + 16 > bottomLimit) break;
            PhoneData.Note n = all.get(idx);

            int rowBg = phoneData.darkMode ? 0xFF16213E : 0xFFFFF9C4;
            g.fill(sx() + 1, y - 1, sx() + SCREEN_W - 1, y + 16, rowBg);

            if (n.pinned) {
                g.drawString(font, "\uD83D\uDCCC", sx() + 2, y + 3, 0xFFFFD54F, false);
            }

            String firstLine = n.text.split("\n", 2)[0];
            String preview = stripMarkup(firstLine);
            g.drawString(font, truncate(preview, noteMaxW), sx() + 12, y + 3, textColor, false);

            g.drawString(font, "X", sx() + SCREEN_W - 11, y + 3, 0xFFFF6666, false);

            y += 18;
        }

        if (indices.isEmpty()) {
            String msg = notesFolderFilter == null ? "Sin notas" : "Sin notas en esta carpeta";
            g.drawCenteredString(font, msg, sx() + SCREEN_W / 2, appY() + 80, subColor);
            g.drawCenteredString(font, "Escribe tu primera nota abajo", sx() + SCREEN_W / 2, appY() + 92, subColor);
        }
    }

    private void renderNoteEditorPreview(GuiGraphics g, int textColor, int subColor) {
        String status = namingNewFolder ? "Escribiendo el nombre de la carpeta..."
                : ("\uD83D\uDCC1 " + (noteEditFolder.isEmpty() ? "Sin carpeta" : noteEditFolder)
                        + (noteEditPinned ? "   \uD83D\uDCCC anclada" : ""));
        g.drawString(font, truncate(status, SCREEN_W - 6), sx() + 3, appY() + 19, subColor, false);

        if (namingNewFolder) return; // mientras se nombra la carpeta no mostramos preview de líneas

        String current = noteInput != null ? noteInput.getValue() : "";
        String fullDraft = noteDraftBuffer;
        if (!current.isEmpty()) {
            fullDraft = fullDraft.isEmpty() ? current : fullDraft + "\n" + current;
        }
        String[] lines = fullDraft.isEmpty() ? new String[0] : fullDraft.split("\n", -1);

        int previewTop    = appY() + 30;
        int toolbarY       = appBottom() - NAV_H - (11 + 2 + 14 + 2 + 11);
        int previewBottom = toolbarY - 2;
        int maxLines = Math.max(0, (previewBottom - previewTop) / 10);

        if (lines.length == 0) {
            g.drawString(font, "Escribiendo una nota nueva...", sx() + 3, previewTop, subColor, false);
            return;
        }

        int start = Math.max(0, lines.length - maxLines);
        int y = previewTop;
        for (int i = start; i < lines.length; i++) {
            drawFormattedLine(g, lines[i], sx() + 3, y, textColor, SCREEN_W - 6);
            y += 10;
        }
    }

    
    
    private String mapaError = null;
    
    private Screen xaeroGuiMap = null;

    private void initMapa() {
        mapaError   = null;
        xaeroGuiMap = null;
        destroyMapaTarget(); // limpiar framebuffer anterior si existe

        try {
            ClassLoader cl = Minecraft.class.getClassLoader();

            Class<?> sessionClass = Class.forName("xaero.map.WorldMapSession", false, cl);
            Object session = sessionClass.getMethod("getCurrentSession").invoke(null);
            if (session == null) {
                mapaError = "Mapa no inicializado.\nAbrí el mundo primero.";
                return;
            }

            Object processor = sessionClass.getMethod("getMapProcessor").invoke(session);
            if (processor == null) {
                mapaError = "MapProcessor no listo.\nIntentá de nuevo.";
                return;
            }

            Screen found = null;

            ICraftMod.LOGGER.info("[iCraft/Mapa] Campos de {}: ", processor.getClass().getName());
            for (java.lang.reflect.Field f : processor.getClass().getDeclaredFields()) {
                ICraftMod.LOGGER.info("  {} : {}", f.getName(), f.getType().getName());
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
                        ICraftMod.LOGGER.info("[iCraft/Mapa] Usando campo '{}' = {}",
                            f.getName(), v.getClass().getName());
                    }
                }
            }

            if (found == null) {
                ICraftMod.LOGGER.warn("[iCraft/Mapa] No se encontró campo Screen en MapProcessor. "
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
                    ICraftMod.LOGGER.info("[iCraft/Mapa] GuiMap instanciada con (null, null, processor, player) OK.");
                } catch (Exception ex2) {
                    mapaError = "GuiMap no disponible.\n" + ex2.getClass().getSimpleName();
                    ICraftMod.LOGGER.warn("[iCraft/Mapa] No se pudo instanciar GuiMap", ex2);
                    return;
                }
            }

            xaeroGuiMap = found;
            setXaeroZoom(2.88);

        } catch (ClassNotFoundException e) {
            mapaError = "Xaero no instalado.";
            ICraftMod.LOGGER.info("[iCraft/Mapa] Xaero World Map no está en el classpath.");
        } catch (Exception e) {
            String msg = e.getMessage() != null
                ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 32))
                : "(sin mensaje)";
            mapaError = "Error: " + e.getClass().getSimpleName() + "\n" + msg;
            ICraftMod.LOGGER.warn("[iCraft/Mapa] Error en initMapa()", e);
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
                ICraftMod.LOGGER.debug("[iCraft/Mapa] campo '{}' = {}", f.getName(), v);
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
                    ICraftMod.LOGGER.debug("[iCraft/Mapa] superclase campo '{}' = {}", f.getName(), v);
                    if (v > 0 && v < 1000) {
                        if (t == double.class) f.setDouble(xaeroGuiMap, zoom);
                        else                   f.setFloat(xaeroGuiMap,  (float) zoom);
                    }
                }
                sup = sup.getSuperclass();
            }
        } catch (Exception e) {
            ICraftMod.LOGGER.warn("[iCraft/Mapa] No se pudo fijar zoom", e);
        }
    }

    private void renderMapa(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();

        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), 0xFF111111);
        blitIcon(g, 6, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, "Mapa", sx() + 20, appY() + 4, getThemeColor(), false);
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
                g.drawCenteredString(font, "Instalá Xaero's", centerX, startY + totalH + 8,  subColor);
                g.drawCenteredString(font, "World Map para",    centerX, startY + totalH + 18, subColor);
                g.drawCenteredString(font, "usar esta app.",    centerX, startY + totalH + 28, subColor);
            } else {
                g.drawCenteredString(font, "Cargando mapa...", centerX, centerY, subColor);
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
            ICraftMod.LOGGER.warn("[iCraft/Mapa] Error en render de GuiMap", e);
            mapaError = "Error render:\n" + e.getClass().getSimpleName();
        }

        g.flush();
        g.pose().popPose();
        g.disableScissor();
    }

    private void renderSettings(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + 2, appY() + 1, ICON_SIZE);
        g.drawString(font, "Ajustes", sx() + 20, appY() + 4, getThemeColor(), false);
        g.fill(sx(), appY() + 14, sx() + SCREEN_W, appY() + 15, 0x44AAAAAA);

        int labelH = 10;
        int gapAbove = 6;
        int gapBelowNav = 3;
        int availableH = (appBottom() - NAV_H - gapBelowNav) - (settingsContentBottom + gapAbove);
        int previewH = Math.min(34, availableH - labelH);
        if (previewH >= 14) {
            int previewW = previewH;
            int previewX = sx() + (SCREEN_W - previewW) / 2;
            int previewY = settingsContentBottom + gapAbove;

            g.fill(previewX - 1, previewY - 1, previewX + previewW + 1, previewY + previewH + 1, 0xFFFFFFFF);
            renderWallpaperThumb(g, getWallpaperIndex(), previewX, previewY, previewW, previewH);

            String wpName = WALLPAPER_NAMES[getWallpaperIndex()];
            g.drawCenteredString(font, truncate(wpName, SCREEN_W - 6),
                    previewX + previewW / 2, previewY + previewH + 2,
                    phoneData.darkMode ? 0xFFCCCCCC : 0xFF444444);
        }
    }

    private void renderPrivacy(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + SCREEN_W / 2 - 8, appY() + 1, ICON_SIZE);
        g.drawCenteredString(font, "Privacidad", sx() + SCREEN_W / 2, appY() + 19, getThemeColor());
        g.fill(sx(), appY() + 30, sx() + SCREEN_W, appY() + 31, 0x44AAAAAA);

        int textColor = phoneData.darkMode ? 0xFFDDDDDD : 0xFF333333;
        int labelColor = getThemeColor();
        int maxTextW = SCREEN_W - 10;
        int textX = sx() + 5;

        int contentTop = appY() + 36;
        int contentBottom = appBottom() - NAV_H;

        String[] paragraphs = {
                "Tus mensajes, fotos, notas y contactos se guardan únicamente en este mundo o servidor.",
                "PhoneOS no envía ninguno de tus datos a internet ni a servidores externos.",
                "Otros jugadores del mismo servidor pueden ver lo que comparas en el chat global y tu nombre en Contactos.",
        };

        record Line(net.minecraft.util.FormattedCharSequence text, boolean isLabel, int gapAfter) {}
        List<Line> lines = new ArrayList<>();

        lines.add(new Line(wrapMessageLines("iCraft", maxTextW).get(0), true, 10));
        lines.add(new Line(wrapMessageLines("BETA 1.6.23", maxTextW).get(0), false, 14));

        lines.add(new Line(wrapMessageLines("Tus datos", maxTextW).get(0), true, 10));
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

    private int editingIconIndex = -1;             // índice del ícono que se está editando (-1 = ninguno)
    private EditBox iconLabelInput = null;          // campo de texto para el nombre del ícono
    private int iconGridStartY, iconGridCellW, iconGridCellH;

    private static final int[] ICON_COLOR_PALETTE = {
            0xFF4A90D9, 0xFF4CAF50, 0xFF9C27B0, 0xFFFF9800, 0xFFF44336,
            0xFF00BCD4, 0xFFE91E8C, 0xFF607D8B, 0xFF795548, 0xFF212121,
            0xFF29B6F6};
    private int colorSwatchX, colorSwatchY, colorSwatchW, colorSwatchH, colorSwatchGap, colorSwatchCols;
    private boolean colorSwatchVisible = false;

    private void initIconEditor() {
        clearWidgets();

        if (editingIconIndex >= 0 && editingIconIndex < phoneData.appIconLabels.length) {
            int editY = appY() + 90;

            iconLabelInput = new EditBox(font, sx() + 5, editY, SCREEN_W - 10, 12,
                    Component.literal("Nombre"));
            iconLabelInput.setMaxLength(16);
            iconLabelInput.setValue(phoneData.appIconLabels[editingIconIndex]);
            addRenderableWidget(iconLabelInput);

            int colorGridBottom = editY + 16; // si no hay grilla, arrancamos justo después del nombre
            colorSwatchVisible = true;
            if (colorSwatchVisible) {
                int colCols = 6;
                int colRows = (int) Math.ceil(ICON_COLOR_PALETTE.length / (double) colCols);
                int colGap = 3;
                int cy = editY + 16;
                int cx = sx() + 5;
                int colBtnW = (SCREEN_W - 10 - (colCols - 1) * colGap) / colCols;
                int colBtnH = colBtnW; // cuadrados

                colorSwatchX = cx;
                colorSwatchY = cy;
                colorSwatchW = colBtnW;
                colorSwatchH = colBtnH;
                colorSwatchGap = colGap;
                colorSwatchCols = colCols;

                colorGridBottom = cy + colRows * (colBtnH + colGap);
            }

            int saveY = Math.min(appBottom() - NAV_H - 14, colorGridBottom + 5);
            int saveCancelGap = 6;
            int saveCancelW = (SCREEN_W - 10 - saveCancelGap) / 2;

            addRenderableWidget(Button.builder(Component.literal("✔ Guardar"), b -> {
                if (iconLabelInput != null && !iconLabelInput.getValue().isBlank())
                    phoneData.appIconLabels[editingIconIndex] = iconLabelInput.getValue().trim();
                editingIconIndex = -1;
                initIconEditor();
            }).pos(sx() + 5, saveY).size(saveCancelW, 12).build());

            addRenderableWidget(Button.builder(Component.literal("✖ Cancelar"), b -> {
                editingIconIndex = -1;
                initIconEditor();
            }).pos(sx() + 5 + saveCancelW + saveCancelGap, saveY).size(saveCancelW, 12).build());

        } else {
            colorSwatchVisible = false;
            int cols = 3;
            int headerH = 30;   // espacio para el título + subtítulo arriba
            int bottomBarH = 30; // espacio reservado para "Restablecer todo" + "Volver"
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

                addRenderableWidget(Button.builder(
                        Component.literal(phoneData.appIconLabels[i]),
                        b -> {
                            editingIconIndex = idx;
                            initIconEditor();
                        }
                ).pos(bx, by).size(cellW - 4, cellH - 4).build());
            }

            int bottomY = startY + rows * cellH + 4;
            addRenderableWidget(Button.builder(Component.literal("↩ Restablecer todo"), b -> {
                phoneData.resetIconDefaults();
                initIconEditor();
            }).pos(sx() + 5, bottomY).size(Math.min(130, SCREEN_W - 10), 12).build());

            addRenderableWidget(Button.builder(Component.literal("◀ Volver"), b -> {
                if (!goBack()) goToApp(App.SETTINGS);
            }).pos(sx() + 5, bottomY + 15).size(60, 11).build());
        }
    }
    private void renderIconEditor(GuiGraphics g, int mx, int my) {
        int bg = phoneData.darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        g.fill(sx(), appY(), sx() + SCREEN_W, appBottom(), bg);

        blitIcon(g, 7, 0, sx() + SCREEN_W / 2 - 8, appY() + 2, ICON_SIZE);

        if (editingIconIndex >= 0 && editingIconIndex < phoneData.appIconLabels.length) {
            String appName = phoneData.appIconLabels[editingIconIndex];
            int    color   = getEffectiveAppColor(editingIconIndex);

            g.drawCenteredString(font, truncate("Editando: " + appName, SCREEN_W - 12),
                    sx() + SCREEN_W / 2, appY() + 18, getThemeColor());

            int px = sx() + SCREEN_W / 2 - 18;
            int py = appY() + 32;
            g.fill(px - 1, py - 1, px + 37, py + 37, 0x55FFFFFF);
            g.fill(px, py, px + 36, py + 36, color);
            g.drawCenteredString(font, appName, px + 18, py + 16, 0xFFFFFFFF);

            g.drawString(font, "Nombre:", sx() + 5, appY() + 82, 0xFFAAAAAA, false);
            g.drawString(font, "Color:", sx() + 5, appY() + 105, 0xFFAAAAAA, false);

            if (colorSwatchVisible) {
                for (int c = 0; c < ICON_COLOR_PALETTE.length; c++) {
                    int col = c % colorSwatchCols;
                    int row = c / colorSwatchCols;
                    int bx = colorSwatchX + col * (colorSwatchW + colorSwatchGap);
                    int by = colorSwatchY + row * (colorSwatchH + colorSwatchGap);
                    int swatchColor = ICON_COLOR_PALETTE[c];

                    boolean isCurrent = (swatchColor | 0xFF000000) == (color | 0xFF000000);
                    int borderColor = isCurrent ? 0xFFFFFFFF : 0x55000000;
                    int borderW = isCurrent ? 2 : 1;

                    g.fill(bx - borderW, by - borderW, bx + colorSwatchW + borderW, by + colorSwatchH + borderW, borderColor);
                    g.fill(bx, by, bx + colorSwatchW, by + colorSwatchH, swatchColor);
                }
            }

        } else {
            g.drawCenteredString(font, "Editor de Iconos", sx() + SCREEN_W / 2, appY() + 16, getThemeColor());
            g.drawCenteredString(font, "Toca un ícono para editarlo",
                    sx() + SCREEN_W / 2, appY() + 27, 0xFF888888);

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

        int itemW = SCREEN_W / navKeys.length;   // 35px por ítem
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
            int delta = scrollY > 0 ? -2 : 2; // scroll arriba = acercar (FOV ↓)
            cameraFov = Math.max(CAM_FOV_MIN, Math.min(CAM_FOV_MAX, cameraFov + delta));
            return true;
        }
        int delta = (int)(scrollY * 10);
        if (currentApp == App.CHAT) {
            chatListScrollOffset = Math.max(0, chatListScrollOffset - delta);
        } else if (currentApp == App.CHAT_CONV) {
            chatConvScrollOffset = Math.max(0, chatConvScrollOffset - delta);
        } else if (currentApp == App.CONTACTS) {
            contactsScrollOffset = Math.max(0, contactsScrollOffset - delta);
        } else if (currentApp == App.PRIVACY) {
            privacyScrollOffset = Math.max(0, privacyScrollOffset - delta);
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
            setXaeroZoom(2.88); // re-fijar zoom inmediatamente después
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (cameraLayout) {
            return true;
        }

        double relX = mouseX - sx();
        double relY = mouseY - appY();

        int navY = appBottom() - NAV_H + 1 - appY();
        if (relY > navY && relY < navY + NAV_H && relX >= 0 && relX < SCREEN_W) {
            int itemW = SCREEN_W / 4;
            int item = (int)(relX / itemW);
            App[] navApps = {App.HOME, App.CHAT, App.CAMERA, App.SETTINGS};
            if (item >= 0 && item < navApps.length) {
                appHistory.clear();
                currentApp = navApps[item];
                initCurrentApp();
                return true;
            }
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

        if (currentApp == App.CREATE_GROUP) {
            return handleCreateGroupClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
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
        for (int c = 0; c < ICON_COLOR_PALETTE.length; c++) {
            int col = c % colorSwatchCols;
            int row = c / colorSwatchCols;
            int bx = colorSwatchX + col * (colorSwatchW + colorSwatchGap);
            int by = colorSwatchY + row * (colorSwatchH + colorSwatchGap);
            if (mx >= bx && mx < bx + colorSwatchW && my >= by && my < by + colorSwatchH) {
                phoneData.appIconColors[editingIconIndex] = ICON_COLOR_PALETTE[c];
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
            if (photoShareMenuOpen && photoShareIndex == photoViewerIndex) {
                int menuX = sx() + 5;
                int menuW = SCREEN_W - 10;
                int menuTop = appY() + 20;
                int rowH = 18;
                int maxRows = Math.min(phoneData.conversations.size(), 6);
                int menuH = maxRows * rowH + 24;
                int menuBottom = menuTop + menuH;

                if (my >= menuBottom - 10 && my <= menuBottom && mx >= menuX && mx < menuX + menuW) {
                    photoShareMenuOpen = false;
                    return true;
                }
                int y = menuTop + 17;
                int shown = 0;
                for (PhoneData.ChatConversation conv : phoneData.conversations) {
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
            return true; // consumir click para no activar otras cosas
        }

        int cols = 3;
        int cellSize = (SCREEN_W - 6) / cols;
        int headerH = 18;
        int gridStartY = appY() + headerH;

        for (int i = 0; i < phoneData.photos.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int px = sx() + 3 + col * cellSize;
            int py = gridStartY + row * cellSize - photosScrollOffset;

            if (mx >= px && mx < px + cellSize - 2 && my >= py && my < py + cellSize - 2
                    && py >= gridStartY && py + cellSize - 2 <= appBottom()) {
                photoViewerIndex = i;
                return true;
            }
        }
        return false;
    }

    private boolean handleNotesClick(double mx, double my) {
        if (isEditingNote()) return false;

        List<Integer> indices = getFilteredSortedNoteIndices();
        int y = appY() + 29;
        for (int idx : indices) {
            if (my >= y - 1 && my < y + 17) {
                if (mx >= sx() + SCREEN_W - 15) {
                    phoneData.notes.remove(idx);
                    return true;
                }
                if (mx <= sx() + 12) {
                    togglePinNote(idx);
                    return true;
                }
                startEditNote(idx);
                return true;
            }
            y += 18;
        }
        return false;
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
                            g_mono = (g_mono / 48) * 48; // posterizar — look de baja resolución
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
                        int sk = clamp((int)((lum - 128) * 1.5 + 128));  // alto contraste
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

    // ==================== COMPRESIÓN DE FOTOS ====================
    // Las capturas vienen directo del framebuffer, que en pantallas grandes
    // (1440p/4K) puede pesar varios MB en PNG sin comprimir — eso choca con
    // el límite de envío/impresión (MAX_ADMIN_PHOTO_BYTES = 512 KB, ver
    // PhoneServerHandler) y tarda más en escribirse a disco / mandarse por
    // red. Por eso, antes de guardar:
    //   1) Reducimos la resolución si excede un máximo razonable.
    //   2) Reencodeamos con ImageIO en vez de NativeImage.writeToFile, que
    //      usa un compresor PNG (deflate vía javax.imageio) más eficiente
    //      que el writer crudo de STB.
    private static final int MAX_PHOTO_DIMENSION = 720; // px, lado más largo

    private static void saveCompressedPhoto(NativeImage img, File outFile) throws IOException {
        BufferedImage buffered = nativeImageToBufferedImage(img);
        buffered = downscaleIfNeeded(buffered, MAX_PHOTO_DIMENSION);

        boolean written = ImageIO.write(buffered, "png", outFile);
        if (!written) {
            // Fallback por si no hay un writer "png" registrado (no debería pasar en un JRE normal)
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
        pendingPhoto = null; // consumir inmediatamente para evitar doble ejecución

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

            ICraftMod.LOGGER.info("[iCraft] Foto guardada en: {}", outFile.getAbsolutePath());
            captureFovActive = false;
            suppressRender   = false; // Volver a dibujar el celular

            PhoneData.PhotoEntry photo = new PhoneData.PhotoEntry(
                    pending.filename,
                    pending.wx, pending.wy, pending.wz, pending.dim
            );
            photo.filter = pending.filter;
            photo.selfie = pending.selfie;
            phoneData.photos.add(photo);
            savePhotoMeta(photo); // persistir metadatos a disco
            notifications.add("📸 ¡Foto guardada! " + pending.filename);

        } catch (Exception e) {
            ICraftMod.LOGGER.error("[iCraft] Error al guardar foto: {}", e.getMessage());
            notifications.add("❌ Error al guardar foto");
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
            return false; // ya recibido (por ejemplo, ya lo agregamos localmente al enviarlo)
        }

        String myName = Minecraft.getInstance().getUser() != null
                ? Minecraft.getInstance().getUser().getName() : "";
        String convName;
        if ("mundial".equals(convId)) {
            convName = "Global";
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
            notifications.add("💬 " + sender + ": " + content);
            // Pasar el timestamp del mensaje para que PhoneToast filtre el historial:
            // solo genera toast si el mensaje es posterior a cuando se abrió la pantalla.
            PhoneToast.push(sender, content, timestamp);
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
            worldIconLocation = null; // el mundo no tiene icon.png: usar el respaldo
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
            ICraftMod.LOGGER.warn("[iCraft] No se pudo decodificar el icon.png del mundo: {}", e.getMessage());
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
        notifications.add("🗑 El administrador vació todos los chats.");
    }

    public static void receiveAdminPhoto(String filename, String base64Png) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Png);

            Path photosDir = getPhotosDirStatic();
            Files.createDirectories(photosDir);
            Path dest = photosDir.resolve(filename);
            Files.write(dest, bytes);

            // El cuadro de foto (PhotoFrameRenderer) puede haber estado
            // esperando este mismo archivo (lo pidió porque no lo tenía
            // localmente). Invalidamos su caché para que la próxima vez que
            // se renderice vuelva a leer el disco y ahora sí lo encuentre.
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

                    ICraftMod.LOGGER.info("[iCraft] Admin photo \"{}\" recibida y cargada ({}x{})",
                            filename, img.getWidth(), img.getHeight());
                } catch (Exception e) {
                    ICraftMod.LOGGER.warn("[iCraft] Error al cargar textura de admin photo \"{}\": {}", filename, e.getMessage());
                }
            });

        } catch (Exception e) {
            ICraftMod.LOGGER.warn("[iCraft] Error al recibir admin photo \"{}\": {}", filename, e.getMessage());
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastRequestTime > 5000) { // every 5 seconds
            lastRequestTime = now;
            if (Minecraft.getInstance().getConnection() != null) {
                PacketDistributor.sendToServer(new RequestDataPacket("weather"));
                PacketDistributor.sendToServer(new RequestDataPacket("players"));
            }
        }

        checkTimerCompletion();
        if (timerNeedsUiRefresh && currentApp == App.CLOCK && clockTab == 2) {
            timerNeedsUiRefresh = false;
            initClock();
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
        g.blit(loc, x, y, w, h, 0, 0, WP_TEX_SIZE, WP_TEX_SIZE, WP_TEX_SIZE, WP_TEX_SIZE);
    }

    
    private void renderWallpaperThumb(GuiGraphics g, int idx, int x, int y, int thumbW, int thumbH) {
        if (idx < 0 || idx >= WALLPAPER_LOCS.length) return;
        g.blit(WALLPAPER_LOCS[idx], x, y, thumbW, thumbH, 0, 0, WP_TEX_SIZE, WP_TEX_SIZE, WP_TEX_SIZE, WP_TEX_SIZE);
    }

    
    private ResourceLocation getWallpaperLocation() {
        for (int i = 0; i < WALLPAPER_IDS.length; i++) {
            if (WALLPAPER_IDS[i].equals(phoneData.wallpaper)) return WALLPAPER_LOCS[i];
        }
        return WALLPAPER_LOCS[0]; // fallback
    }

    
    private int getWallpaperIndex() {
        for (int i = 0; i < WALLPAPER_IDS.length; i++) {
            if (WALLPAPER_IDS[i].equals(phoneData.wallpaper)) return i;
        }
        return 0;
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
        return switch (phoneData.theme) {
            case "green"  -> 0xFF4CAF50;
            case "purple" -> 0xFF9C27B0;
            case "pink"   -> 0xFFE91E8C;
            case "red"    -> 0xFFF44336;
            case "orange" -> 0xFFFF9800;
            default       -> 0xFF4A90D9;  // blue
        };
    }

    private static final int CONTACTS_ICON_INDEX = 8;
    private static final int CONTACTS_FIXED_COLOR = 0xFF9C27B0; // Violeta (default)

    
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
            case "creeper"   -> 0x6600CC00;   // verde pixelado
            case "enderman"  -> 0x881A0033;   // violeta oscuro denso
            case "skeleton"  -> 0x33CCCCCC;   // blanquecino óseo
            case "blaze"     -> 0x55FF8800;   // naranja fuego
            case "bat"       -> 0xCC000011;   // casi negro sonar
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
            PacketDistributor.sendToServer(new com.icraft.network.PhoneOpenStatePacket(false));
        }
        destroyMapaTarget(); // liberar framebuffer de Xaero
        super.removed();
    }

    @Override
    public void onClose() {
        if (!closedNotified) {
            closedNotified = true;
            PacketDistributor.sendToServer(new com.icraft.network.PhoneOpenStatePacket(false));
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

    
    private Path getSettingsFile() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("iCraft").resolve("settings.properties");
    }

    
    private void saveSettings() {
        try {
            Path settingsFile = getSettingsFile();
            Files.createDirectories(settingsFile.getParent());
            Properties props = new Properties();
            props.setProperty("theme",        phoneData.theme);
            props.setProperty("wallpaper",    phoneData.wallpaper);
            props.setProperty("currentCase",  phoneData.currentCase);
            props.setProperty("notificationSound", phoneData.notificationSound);
            props.setProperty("doNotDisturb", String.valueOf(phoneData.doNotDisturb));
            try (FileOutputStream fos = new FileOutputStream(settingsFile.toFile())) {
                props.store(fos, "iCraft settings");
            }
        } catch (Exception e) {
            ICraftMod.LOGGER.warn("Could not save iCraft settings: {}", e.getMessage());
        }
    }

    
    private static Path getReadIdsFile() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("iCraft").resolve("read_messages.properties");
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
            ICraftMod.LOGGER.warn("[iCraft] No se pudieron cargar IDs de mensajes leídos: {}", e.getMessage());
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
            ICraftMod.LOGGER.warn("[iCraft] No se pudieron guardar IDs de mensajes leídos: {}", e.getMessage());
        }
    }

    
    private void loadSettings() {
        try {
            Path settingsFile = getSettingsFile();
            if (!Files.exists(settingsFile)) return;
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
            if (sound != null && Arrays.asList(SOUNDS).contains(sound))
                phoneData.notificationSound = sound;

            String dnd = props.getProperty("doNotDisturb");
            if (dnd != null)
                phoneData.doNotDisturb = Boolean.parseBoolean(dnd);

        } catch (Exception e) {
            ICraftMod.LOGGER.warn("Could not load iCraft settings: {}", e.getMessage());
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
                return; // ya se aplicó antes — no tocar nada para no pisar personalizaciones
            }

            int[] defaults = {
                    0xFF4CAF50, // Chat — Verde
                    0xFF212121, // Cámara — Negro
                    0xFFFF9800, // Foto — Naranja
                    0xFF29B6F6, // Clima — Celeste
                    0xFF607D8B, // Reloj — Gris
                    0xFF795548, // Notas — Marrón
                    0xFF4CAF50, // Mapa — Verde bosque
                    0xFF607D8B, // Ajustes — Gris
                    CONTACTS_FIXED_COLOR, // Contactos — Violeta
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
            ICraftMod.LOGGER.warn("Could not apply default app colors: {}", e.getMessage());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && currentApp == App.NOTES
                && noteInput != null && noteInput.isFocused() && !namingNewFolder) {
            String current = noteInput.getValue();
            if (!current.trim().isEmpty()) {
                noteDraftBuffer += (noteDraftBuffer.isEmpty() ? "" : "\n") + current;
                noteInput.setValue("");
                initNotes();
            }
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
            if (keyCode == 263 && photoViewerIndex > 0) { // flecha izquierda
                photoViewerIndex--;
                return true;
            }
            if (keyCode == 262 && photoViewerIndex < phoneData.photos.size() - 1) { // flecha derecha
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
            if (keyCode == 265) { // UP arrow
                cameraFov = Math.max(CAM_FOV_MIN, cameraFov - 2);
                return true;
            }
            if (keyCode == 264) { // DOWN arrow
                cameraFov = Math.min(CAM_FOV_MAX, cameraFov + 2);
                return true;
            }
            if (keyCode == 340 || keyCode == 344) {
                notifications.add("Camera cancelled");
                currentApp = App.HOME;
                initCurrentApp();
                return true;
            }

            int[] movementKeys = {87, 65, 83, 68, 32, 341, 262, 263};
            for (int mk : movementKeys) {
                if (keyCode == mk) return false; // pass through
            }
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
                if (keyCode == mk) return false; // pass through
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