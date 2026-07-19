package com.icraft;

import com.icraft.command.ICraftCommand;
import com.icraft.client.PhoneScreen;
import com.icraft.init.ModItems;
import com.icraft.init.ModBlocks;
import com.icraft.init.ModCreativeTabs;
import com.icraft.init.ModEntityTypes;
import com.icraft.network.ModPackets;
import com.icraft.server.PhoneServerHandler;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ICraftMod.MODID)
public class ICraftMod {

    public static final String MODID = "icraft";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /** Guardar datos cada N ticks del servidor (20 ticks = 1 segundo, 6000 = 5 minutos) */
    private static final int SAVE_INTERVAL_TICKS = 6000;
    private int tickCounter = 0;

    public ICraftMod(IEventBus modEventBus) {
        // Registrar deferred registers
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);

        // Setup listeners (mod bus)
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(ModPackets::register);

        // Eventos del mundo/servidor (NeoForge bus)
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        LOGGER.info("iCraft Mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("iCraft common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("iCraft client setup complete.");
    }

    // ===================== CAPTURA DE FOTO (CLIENTE) =====================

    /**
     * Se dispara DESPUÉS de que el nivel (mundo 3D) se terminó de renderizar
     * pero ANTES de que cualquier GUI (HUD, teléfono) se pinte encima.
     *
     * Flujo sin flash:
     *  1. El jugador presiona R → capturePhoto() activa suppressRender=true y
     *     guarda los metadatos en pendingPhoto. El screen NO se cierra.
     *  2. En el mismo frame, render() del PhoneScreen ve suppressRender=true y
     *     devuelve sin pintar nada → el celular es invisible ese frame.
     *  3. Este handler se dispara: el framebuffer tiene solo el mundo 3D limpio.
     *     Capturamos, guardamos, y limpiamos suppressRender → el celular vuelve.
     *  4. No hay setScreen(null) ni reapertura → cero frames de flash.
     */
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (!PhoneScreen.hasPendingPhoto()) return;
        // suppressRender ya está activo: el celular no se pintó este frame.
        // El framebuffer contiene solo el mundo 3D con el FOV de zoom correcto.
        PhoneScreen.takeScheduledPhoto();
    }

    /**
     * Oculta elementos del HUD mientras el teléfono esté abierto:
     *
     * - SIEMPRE que PhoneScreen está abierto: se ocultan todos los elementos
     *   del inventario/HUD (hotbar, salud, hambre, armadura, aire, XP, efectos,
     *   boss bar, etc.) para que el celular se vea sin ruido visual.
     *
     * - Solo cuando la app de cámara está activa además: se oculta el crosshair
     *   (el visor de la cámara tiene el suyo propio).
     *
     * Los nombres de los layers son los de VanillaGuiLayers en NeoForge 1.21.1
     * (namespace "minecraft", paths listados abajo).
     */
    @SubscribeEvent
    public void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.screen instanceof PhoneScreen ps)) return;

        net.minecraft.resources.ResourceLocation name = event.getName();
        if (name == null || !"minecraft".equals(name.getNamespace())) return;

        String path = name.getPath();

        // Crosshair: solo en modo cámara (el visor ya tiene el suyo propio)
        if ("crosshair".equals(path) && ps.isCameraActive()) {
            event.setCanceled(true);
            return;
        }

        // Elementos del HUD que se ocultan siempre que el celular esté abierto
        switch (path) {
            case "hotbar",
                 "health",
                 "armor",
                 "food",
                 "air",
                 "experience_bar",
                 "jump_bar",
                 "effects_indicator",
                 "sleep_fade",
                 "vignette",
                 "spyglass",
                 "helmet_overlay",
                 "frostbite",
                 "record_overlay",
                 "subtitles",
                 "scoreboard_sidebar",
                 "boss_overlay" -> event.setCanceled(true);
            default -> {} // chat, title, tab_list, etc. se dejan pasar
        }
    }

    /**
     * Oculta el item en la mano del jugador mientras el teléfono esté abierto.
     * Evita que el arma/herramienta aparezca superpuesta al marco del celular.
     */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof PhoneScreen) {
            event.setCanceled(true);
        }
    }

    /**
     * Controla el FOV de renderizado mientras la app de cámara del celular está abierta.
     *
     * Antes, PhoneScreen escribía el FOV directamente en {@code Minecraft.options.fov()},
     * la opción real de FOV del jugador. El problema: esa opción de Minecraft sólo acepta
     * valores entre 30 y 110 grados. Al hacer mucho zoom (FOV por debajo de 30) Minecraft
     * rechazaba el valor y reseteaba el FOV a su default silenciosamente, mientras el
     * slider del celular seguía mostrando el valor "roto" — quedaba desincronizado y daba
     * la sensación de que la cámara se bloqueaba.
     *
     * La solución es la misma que usa vanilla para el zoom del catalejo/spyglass: en vez
     * de tocar la opción de FOV del jugador, se intercepta el cálculo del FOV en cada
     * frame con este evento y se fuerza directamente el valor del slider de la cámara.
     * Así no hay límite de 30-110, el zoom puede llegar a valores extremos (estilo
     * catalejo) sin romperse, y la opción de FOV real del jugador (la de las settings de
     * Minecraft) nunca se toca ni se "ensucia".
     */
    @SubscribeEvent
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // Caso 1: el screen de la cámara está abierto (uso normal del visor)
        if (mc.screen instanceof PhoneScreen ps && ps.isCameraActive()) {
            event.setFOV(ps.getCameraFov());
        }
        // Caso 2: se cerró el screen para capturar la foto pero el FOV de zoom
        // debe seguir activo durante ese frame de captura (sin esto la foto
        // se renderiza con el FOV normal y el zoom no se ve en la imagen).
        else if (PhoneScreen.isCaptureFovActive()) {
            event.setFOV(PhoneScreen.getCaptureFov());
        }
    }

    // ===================== COMANDOS =====================

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ICraftCommand.register(event.getDispatcher());
    }

    // ===================== EVENTOS DE JUGADORES =====================

    /**
     * Cuando un jugador entra al servidor:
     * - Carga sus datos desde disco
     * - Lo une al grupo mundial automáticamente
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                PhoneServerHandler.onPlayerJoin(player, server);
                LOGGER.info("[iCraft] {} entró — datos cargados y unido al grupo Mundial", player.getGameProfile().getName());
            }
        }
    }

    /**
     * Cuando un jugador sale del servidor:
     * - Guarda sus datos al disco
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                PhoneServerHandler.onPlayerLeave(player, server);
                LOGGER.info("[iCraft] {} salió — datos guardados", player.getGameProfile().getName());
            }
        }
    }

    // ===================== CHAT DE MINECRAFT DESACTIVADO =====================

    /**
     * Cancela TODOS los mensajes del chat vanilla de Minecraft.
     * Los jugadores deben usar el chat de la app del teléfono en su lugar.
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        event.setCanceled(true);
        // Informar al jugador que debe usar el celular para chatear
        ServerPlayer sender = event.getPlayer();
        sender.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§a[Sistema] §eNecesitás el celular para chatear con otros jugadores."
            )
        );
        LOGGER.debug("[iCraft] Chat vanilla bloqueado para {}", sender.getGameProfile().getName());
    }

    // ===================== AUTO-GUARDADO PERIÓDICO =====================

    /**
     * Guarda los datos de todos los jugadores cada 5 minutos.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // Mantiene el brazo levantado (animación tipo catalejo) de los
        // jugadores que tienen el celular abierto, por si vanilla lo cortó
        // solo por soltar el click derecho. Ver PhoneServerHandler para más detalle.
        PhoneServerHandler.tickPhoneArmAnimations(event.getServer());

        tickCounter++;
        if (tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            MinecraftServer server = event.getServer();
            PhoneServerHandler.saveAllPlayerData(server);
            LOGGER.debug("[iCraft] Auto-guardado de datos completado.");
        }
    }

    /**
     * Cuando el servidor se detiene, guardar todos los datos.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PhoneServerHandler.saveAllPlayerData(event.getServer());
        LOGGER.info("[iCraft] Servidor deteniéndose — todos los datos guardados.");
    }
}
