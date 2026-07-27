package com.icraft;

import com.icraft.command.ICraftCommand;
import com.icraft.event.ICraftClientEventsNeoForge;
import com.icraft.event.ICraftServerEventsNeoForge;
import com.icraft.init.ModBlocks;
import com.icraft.init.ModCreativeTabs;
import com.icraft.init.ModEntityTypes;
import com.icraft.init.ModItems;
import com.icraft.network.ModPackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ICraftMod.MODID)
public class ICraftMod {

    public static final String MODID = com.icraft.ICraftConstants.MODID;
    public static final Logger LOGGER = com.icraft.ICraftConstants.LOGGER;

    // Paso 4: la lógica de estos handlers ya no vive acá — se movió detrás
    // de la interfaz común ICraftClientEvents/ICraftServerEvents (ver
    // com.icraft.event en common/). Estas dos instancias son la
    // implementación NeoForge de esa interfaz; los métodos @SubscribeEvent
    // de esta clase son ahora solo adaptadores finos que traducen el evento
    // de NeoForge a la llamada correspondiente.
    private final ICraftClientEventsNeoForge clientEvents = new ICraftClientEventsNeoForge();
    private final ICraftServerEventsNeoForge serverEvents = new ICraftServerEventsNeoForge();

    public ICraftMod(IEventBus modEventBus) {

        // Paso 2: Architectury Registry — el registro ya no necesita el
        // IEventBus de NeoForge, Architectury lo resuelve internamente
        // siempre que se llame durante la construcción del mod (como acá).
        ModBlocks.BLOCKS.register();
        ModItems.ITEMS.register();
        ModCreativeTabs.CREATIVE_MODE_TABS.register();
        ModEntityTypes.ENTITY_TYPES.register();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        // Paso 3: Architectury Networking — ya no es un listener de
        // RegisterPayloadHandlersEvent, se llama directo durante la
        // construcción del mod (igual que el registro del paso 2).
        ModPackets.register();

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        LOGGER.info("iCraft Mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("iCraft common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // Paso 2/4: estas 4 clases vivían en neoforge/ auto-registrándose contra
        // el event bus nativo; ahora viven en common/ y exponen un register()
        // cross-loader (Architectury). FMLClientSetupEvent solo se dispara del
        // lado cliente, así que este es un lugar seguro para llamarlas.
        com.icraft.client.GlobalImagesState.register();
        com.icraft.client.PhoneToast.register();
        com.icraft.client.PhoneOpenTracker.register();
        com.icraft.client.ClientSetup.register();
        LOGGER.info("iCraft client setup complete.");
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        clientEvents.onRenderAfterLevel();
    }

    @SubscribeEvent
    public void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (clientEvents.onRenderGuiLayer(event.getName())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (clientEvents.onRenderHand()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        Float fov = clientEvents.onComputeFov();
        if (fov != null) {
            event.setFOV(fov);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ICraftCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                serverEvents.onPlayerJoin(player, server);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                serverEvents.onPlayerLeave(player, server);
            }
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (serverEvents.onServerChat(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        serverEvents.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        serverEvents.onServerStopping(event.getServer());
    }
}
