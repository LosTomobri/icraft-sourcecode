package com.icraft.server;

import com.google.gson.*;
import com.icraft.ICraftMod;
import com.icraft.data.PhoneData;
import com.icraft.network.*;
import com.icraft.network.AdminPhotoPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side handler for all phone operations.
 * Manages message routing, weather, marketplace, and the global "Mundial" group.
 *
 * == PERSISTENCIA ==
 * Los datos de cada jugador se guardan en:
 *   world/iCraft/data/<playerName>.json
 * y se cargan cuando el jugador entra al servidor.
 *
 * == GRUPO MUNDIAL ==
 * Al entrar cualquier jugador, se añade automáticamente al grupo "mundial".
 * El grupo incluye siempre a todos los jugadores que alguna vez estuvieron en el mundo.
 */
public class PhoneServerHandler {

    /** ID fijo del grupo global — no cambia nunca */
    public static final String GLOBAL_GROUP_ID   = "mundial";
    public static final String GLOBAL_GROUP_NAME = "Global";

    // In-memory store: playerName -> PhoneData
    private static final Map<String, PhoneData> playerData = new ConcurrentHashMap<>();
    /** Jugadores que ya recibieron mensaje de bienvenida en esta sesión del servidor */
    private static final java.util.Set<String> announcedJoins = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    // Global marketplace listings
    private static final List<PhoneData.MarketListing> globalMarket = new ArrayList<>();

    // Group conversations: groupId -> list of member names
    private static final Map<String, List<String>> groups = new ConcurrentHashMap<>();

    // Todos los jugadores que alguna vez se conectaron a este server (persistido
    // a disco, así sobrevive a un reinicio del server). Usado por la app de
    // "Contactos" del celular.
    private static final Set<String> knownPlayers = ConcurrentHashMap.newKeySet();
    private static boolean knownPlayersLoaded = false;

    // Directorio de datos — se inicializa en el primer uso
    private static Path dataDir = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ===== INICIALIZACIÓN DEL DIRECTORIO =====

    private static Path getDataDir(MinecraftServer server) {
        if (dataDir == null) {
            dataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("iCraft").resolve("data");
            try {
                Files.createDirectories(dataDir);
            } catch (IOException e) {
                ICraftMod.LOGGER.error("No se pudo crear el directorio iCraft/data: {}", e.getMessage());
            }
        }
        return dataDir;
    }

    // ===== PERSISTENCIA =====

    /**
     * Carga los datos del jugador desde disco al entrar al servidor.
     * Si no existe el archivo, crea uno nuevo con PhoneData vacío.
     */
    public static PhoneData loadPlayerData(String playerName, MinecraftServer server) {
        Path file = getDataDir(server).resolve(playerName + ".json");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                PhoneData data = GSON.fromJson(r, PhoneData.class);
                if (data != null) {
                    playerData.put(playerName, data);
                    ICraftMod.LOGGER.info("[iCraft] Datos cargados para {}", playerName);
                    return data;
                }
            } catch (Exception e) {
                ICraftMod.LOGGER.error("[iCraft] Error al cargar datos de {}: {}", playerName, e.getMessage());
            }
        }
        // Archivo no existe o error: crear datos nuevos
        PhoneData fresh = new PhoneData();
        playerData.put(playerName, fresh);
        return fresh;
    }

    /**
     * Guarda los datos del jugador al disco.
     * Llamar al desconectarse y periódicamente.
     */
    public static void savePlayerData(String playerName, MinecraftServer server) {
        PhoneData data = playerData.get(playerName);
        if (data == null) return;
        Path file = getDataDir(server).resolve(playerName + ".json");
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(data, w);
            ICraftMod.LOGGER.debug("[iCraft] Datos guardados para {}", playerName);
        } catch (IOException e) {
            ICraftMod.LOGGER.error("[iCraft] Error al guardar datos de {}: {}", playerName, e.getMessage());
        }
    }

    /**
     * Guarda todos los jugadores online al disco.
     * Llamar desde el tick del servidor (ej: cada 5 min) o en shutdown.
     */
    public static void saveAllPlayerData(MinecraftServer server) {
        for (String name : playerData.keySet()) {
            savePlayerData(name, server);
        }
    }

    // ===== GRUPO MUNDIAL — AUTO-JOIN =====

    /**
     * Llamado cuando un jugador entra al servidor.
     * 1. Carga sus datos desde disco.
     * 2. Lo añade al grupo "Mundial" si no está ya.
     * 3. Notifica a todos los online de que entró.
     * 4. Le envía el estado actual del servidor (weather, player list, grupo mundial).
     */
    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        String name = player.getGameProfile().getName();

        // 1. Cargar datos desde disco
        PhoneData data = loadPlayerData(name, server);

        // 1b. Registrar al jugador en la lista persistida de "conocidos" (Contactos)
        loadKnownPlayersIfNeeded(server);
        if (knownPlayers.add(name)) {
            saveKnownPlayers(server);
            broadcastContactsUpdate(server); // que los demás online lo vean sin reconectar
        }

        // 2. Añadir al grupo mundial en memoria del servidor
        List<String> members = groups.computeIfAbsent(GLOBAL_GROUP_ID, k -> new ArrayList<>());
        if (!members.contains(name)) {
            members.add(name);
            ICraftMod.LOGGER.info("[iCraft] {} se unió al grupo Mundial", name);
        }

        // 3. Asegurar que el jugador tiene la conversación del grupo en sus datos
        boolean hasGroup = data.conversations.stream()
                .anyMatch(c -> c.id.equals(GLOBAL_GROUP_ID));
        if (!hasGroup) {
            PhoneData.ChatConversation globalConv = new PhoneData.ChatConversation(
                    GLOBAL_GROUP_ID, GLOBAL_GROUP_NAME, true);
            globalConv.members.addAll(members);
            data.conversations.add(0, globalConv); // al principio de la lista
        } else {
            // Actualizar lista de miembros
            data.conversations.stream()
                    .filter(c -> c.id.equals(GLOBAL_GROUP_ID))
                    .findFirst()
                    .ifPresent(c -> {
                        c.members.clear();
                        c.members.addAll(members);
                    });
        }

        // 4. Notificar al grupo que el jugador entró (sólo una vez por sesión)
        if (announcedJoins.add(name)) {
            String joinMsg = "§e" + name + " se unió al mundo";
            broadcastToGlobalGroup(server, "§aSistema", joinMsg);
        }

        // 5. Enviarle sus datos actuales
        sendAllData(player);
    }

    /**
     * Llamado cuando un jugador sale del servidor.
     * Guarda sus datos y lo mantiene en el grupo (para que reciba mensajes offline al volver).
     */
    public static void onPlayerLeave(ServerPlayer player, MinecraftServer server) {
        String name = player.getGameProfile().getName();
        savePlayerData(name, server);
        announcedJoins.remove(name); // permitir anuncio al volver a entrar

        // Notificar al grupo
        String leaveMsg = "§e" + name + " salió del mundo";
        broadcastToGlobalGroup(server, "§aSistema", leaveMsg);

        // Si se desconectó con el celular abierto, que los demás clientes
        // dejen de mostrarle el modelo "abierto" en la mano.
        broadcastPhoneOpenState(player, false);
    }

    /**
     * El jugador {@code player} acaba de abrir o cerrar su celular
     * (ver PhoneOpenStatePacket). Le avisamos a todos los DEMÁS
     * jugadores online para que vean el modelo "abierto" en su mano
     * (ver PhoneOpenSyncPacket / PhoneOpenTracker en el cliente).
     */
    public static void broadcastPhoneOpenState(ServerPlayer player, boolean open) {
        PhoneOpenSyncPacket packet = new PhoneOpenSyncPacket(player.getUUID(), open);
        for (ServerPlayer target : player.server.getPlayerList().getPlayers()) {
            if (target == player) continue; // el propio jugador no lo necesita
            PacketDistributor.sendToPlayer(target, packet);
        }

        // Levanta o baja el brazo (animación tipo catalejo, ver SmartphoneItem)
        // usando el sistema estándar de "usar item" de LivingEntity. Al hacerlo
        // del lado servidor, el estado se sincroniza solo a TODOS los clientes
        // (incluido el dueño) vía el entity data normal del jugador — no hace
        // falta un paquete custom para que los demás vean el brazo levantado.
        InteractionHand hand = handHoldingSmartphone(player);
        if (open) {
            if (hand != null) {
                player.startUsingItem(hand);
                phoneArmRaised.add(player.getUUID());
            }
        } else {
            player.stopUsingItem();
            phoneArmRaised.remove(player.getUUID());
        }
    }

    /** Devuelve la mano (principal u offhand) con la que el jugador sostiene
     *  el celular, o null si no lo tiene en ninguna de las dos. */
    private static InteractionHand handHoldingSmartphone(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof com.icraft.item.SmartphoneItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof com.icraft.item.SmartphoneItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** UUIDs de jugadores que tienen el celular abierto ahora mismo y por lo
     *  tanto deberían tener el brazo levantado (animación tipo catalejo). */
    private static final Set<UUID> phoneArmRaised = ConcurrentHashMap.newKeySet();

    /**
     * Se llama cada tick del servidor (ver ICraftMod#onServerTick).
     *
     * Minecraft corta automáticamente el "usar item" de un jugador cuando
     * detecta que soltó el click derecho. Como nuestro celular se abre con
     * un solo click (no hace falta mantenerlo apretado), esa lógica vanilla
     * podría bajar el brazo apenas se suelta el botón, aunque la pantalla
     * siga abierta. Para evitarlo, mientras un jugador esté en
     * {@link #phoneArmRaised}, cada tick chequeamos que siga "usando" el
     * celular y, si no, lo reactivamos. La mayoría de los ticks esto no
     * hace nada (startUsingItem() es no-op si ya está en uso).
     */
    public static void tickPhoneArmAnimations(MinecraftServer server) {
        if (phoneArmRaised.isEmpty()) return;
        for (UUID id : phoneArmRaised) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            if (!player.isUsingItem()) {
                InteractionHand hand = handHoldingSmartphone(player);
                if (hand != null) {
                    player.startUsingItem(hand);
                }
            }
        }
    }

    /**
     * Envía un mensaje al grupo mundial a todos los jugadores ONLINE.
     * También lo guarda en los datos de cada jugador online para persistencia.
     */
    public static void broadcastToGlobalGroup(MinecraftServer server, String senderName, String content) {
        long ts = System.currentTimeMillis();
        String msgId = UUID.randomUUID().toString();
        List<String> members = groups.getOrDefault(GLOBAL_GROUP_ID, List.of());

        ChatMessagePacket msg = new ChatMessagePacket(
                GLOBAL_GROUP_ID, senderName, content, ts, true, msgId);

        for (String member : members) {
            ServerPlayer target = server.getPlayerList().getPlayerByName(member);
            if (target != null) {
                PacketDistributor.sendToPlayer(target, msg);
                // Guardar en sus datos
                PhoneData tData = playerData.get(member);
                if (tData != null) {
                    tData.conversations.stream()
                            .filter(c -> c.id.equals(GLOBAL_GROUP_ID))
                            .findFirst()
                            .ifPresent(c -> {
                                PhoneData.ChatMessage stored = new PhoneData.ChatMessage(senderName, content);
                                stored.id = msgId;
                                stored.timestamp = ts;
                                c.messages.add(stored);
                            });
                }
            }
        }
    }

    // ===== DATA ACCESS =====

    public static PhoneData getOrCreate(String playerName) {
        return playerData.computeIfAbsent(playerName, k -> new PhoneData());
    }

    /**
     * Genera un ID de conversación DM canónico a partir de dos nombres de jugador.
     * Siempre produce el mismo ID sin importar quién inicia la conversación,
     * evitando que se creen conversaciones duplicadas ("dm_Alex" vs "dm_Steve").
     * Formato: "dm_<menor_alfabético>_<mayor_alfabético>"
     */
    public static String canonicalDmId(String playerA, String playerB) {
        if (playerA.compareToIgnoreCase(playerB) <= 0) {
            return "dm_" + playerA + "_" + playerB;
        } else {
            return "dm_" + playerB + "_" + playerA;
        }
    }

    // ===== MESSAGE ROUTING =====

    public static void routeMessage(ServerPlayer sender, SendChatPacket packet) {
        String senderName = sender.getGameProfile().getName();
        long timestamp = System.currentTimeMillis();

        // ── Invitación de grupo privado ────────────────────────────────────
        // Formato conversationId: "GROUP_INVITE:<groupId>:<groupName>"
        // El servidor registra el grupo y reenvía la invitación al destinatario.
        String convId = packet.conversationId();
        if (convId != null && convId.startsWith("GROUP_INVITE:")) {
            handleGroupInvite(sender, packet);
            return;
        }

        // Traducir códigos de color: & → § (permite &a, &b, &c... en mensajes)
        // Solo se aplica a jugadores con permiso de operator (nivel 2+).
        // Usamos una variable final separada para que los lambdas más abajo
        // puedan capturarla sin el error "must be final or effectively final".
        final String content = (packet.content() != null && packet.content().contains("&") && sender.hasPermissions(2))
                ? packet.content().replace('&', '§')
                : packet.content();

        // Handle @mentions
        if (content.contains("@")) {
            handleMentions(content, senderName, sender.getServer());
        }

        if (packet.isGroup()) {
            String groupId = packet.conversationId();
            List<String> members;

            // Si es el grupo mundial, usamos la lista completa
            if (GLOBAL_GROUP_ID.equals(groupId)) {
                members = groups.getOrDefault(GLOBAL_GROUP_ID, List.of());
            } else {
                members = groups.getOrDefault(groupId, List.of());
            }

            // Usamos el mismo messageId que generó el cliente al crear el mensaje
            // localmente (en vez de uno nuevo), así el cliente puede deduplicar
            // por ID cuando le llegue el historial más adelante.
            String msgId = (packet.messageId() != null && !packet.messageId().isEmpty())
                    ? packet.messageId() : UUID.randomUUID().toString();

            ChatMessagePacket msg = new ChatMessagePacket(
                    groupId, senderName, content, timestamp, true, msgId);

            for (String member : members) {
                ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(member);
                if (target != null && !target.equals(sender)) {
                    PacketDistributor.sendToPlayer(target, msg);
                }
                // Guardar en datos persistentes de cada miembro (incluido el sender)
                PhoneData mData = playerData.get(member);
                if (mData != null) {
                    mData.conversations.stream()
                            .filter(c -> c.id.equals(groupId))
                            .findFirst()
                            .ifPresent(c -> {
                                PhoneData.ChatMessage stored = new PhoneData.ChatMessage(senderName, content);
                                stored.id = msgId;
                                stored.timestamp = timestamp;
                                c.messages.add(stored);
                            });
                }
            }
        } else {
            // DM: send to recipient only.
            // Bloquear mensajes a uno mismo (no tiene sentido y causa bugs de UI).
            String recipientName = packet.recipientName();
            if (senderName.equals(recipientName)) {
                ICraftMod.LOGGER.warn("[iCraft] {} intentó enviarse un DM a sí mismo — ignorado", senderName);
                return;
            }

            // Mismo messageId que generó el cliente, así no se duplica al
            // recibir el historial más adelante.
            String msgId = (packet.messageId() != null && !packet.messageId().isEmpty())
                    ? packet.messageId() : UUID.randomUUID().toString();

            // ConvId CANÓNICO: igual para ambas partes sin importar quién inicia.
            // Evita que se creen dos conversaciones distintas.
            String canonicalId = canonicalDmId(senderName, recipientName);

            ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(recipientName);
            if (target != null) {
                // El cliente receptor recibirá el convId canónico y el sender real.
                ChatMessagePacket msg = new ChatMessagePacket(
                        canonicalId, senderName, content, timestamp, false, msgId);
                PacketDistributor.sendToPlayer(target, msg);

                // Guardar en datos del sender (name = recipientName)
                PhoneData senderData = getOrCreate(senderName);
                PhoneData.ChatConversation conv = senderData.conversations.stream()
                        .filter(c -> c.id.equals(canonicalId))
                        .findFirst().orElseGet(() -> {
                            PhoneData.ChatConversation nc = new PhoneData.ChatConversation(
                                    canonicalId, recipientName, false);
                            senderData.conversations.add(nc);
                            return nc;
                        });
                PhoneData.ChatMessage senderMsg = new PhoneData.ChatMessage(senderName, content);
                senderMsg.id = msgId;
                senderMsg.timestamp = timestamp;
                conv.messages.add(senderMsg);

                // Guardar en datos del receptor (name = senderName)
                PhoneData targetData = getOrCreate(recipientName);
                PhoneData.ChatConversation targetConv = targetData.conversations.stream()
                        .filter(c -> c.id.equals(canonicalId))
                        .findFirst().orElseGet(() -> {
                            PhoneData.ChatConversation nc = new PhoneData.ChatConversation(
                                    canonicalId, senderName, false);
                            targetData.conversations.add(nc);
                            return nc;
                        });
                PhoneData.ChatMessage targetMsg = new PhoneData.ChatMessage(senderName, content);
                targetMsg.id = msgId;
                targetMsg.timestamp = timestamp;
                targetConv.messages.add(targetMsg);
            } else {
                // Receptor offline: guardar igual para cuando vuelva
                PhoneData targetData = getOrCreate(recipientName);
                PhoneData.ChatConversation targetConv = targetData.conversations.stream()
                        .filter(c -> c.id.equals(canonicalId))
                        .findFirst().orElseGet(() -> {
                            PhoneData.ChatConversation nc = new PhoneData.ChatConversation(
                                    canonicalId, senderName, false);
                            targetData.conversations.add(nc);
                            return nc;
                        });
                PhoneData.ChatMessage offlineMsg = new PhoneData.ChatMessage(senderName, content);
                offlineMsg.id = msgId;
                offlineMsg.timestamp = timestamp;
                targetConv.messages.add(offlineMsg);

                // Guardar también en datos del sender
                PhoneData senderData = getOrCreate(senderName);
                PhoneData.ChatConversation senderConv = senderData.conversations.stream()
                        .filter(c -> c.id.equals(canonicalId))
                        .findFirst().orElseGet(() -> {
                            PhoneData.ChatConversation nc = new PhoneData.ChatConversation(
                                    canonicalId, recipientName, false);
                            senderData.conversations.add(nc);
                            return nc;
                        });
                PhoneData.ChatMessage senderMsg2 = new PhoneData.ChatMessage(senderName, content);
                senderMsg2.id = msgId;
                senderMsg2.timestamp = timestamp;
                senderConv.messages.add(senderMsg2);

                ICraftMod.LOGGER.info("[iCraft] {} offline — mensaje guardado para cuando vuelva", recipientName);
            }
        }
    }

    /**
     * Procesa un packet de invitación a grupo privado.
     *
     * El packet tiene:
     *   conversationId = "GROUP_INVITE:<groupId>:<groupName>"
     *   recipientName  = jugador invitado
     *   content        = "__group_invite__"  (sentinel, no se muestra)
     *
     * El servidor:
     *   1. Extrae groupId y groupName del conversationId.
     *   2. Registra al invitante y al invitado en groups[groupId].
     *   3. Reenvía el invite al cliente del destinatario mediante un
     *      ChatMessagePacket con prefijo "§§GROUP_INVITE:<groupId>:<groupName>"
     *      que el cliente parsea en handleChatMessage() y convierte en
     *      una llamada a PhoneScreen.receiveGroupInvite().
     */
    private static void handleGroupInvite(ServerPlayer sender, SendChatPacket packet) {
        String senderName = sender.getGameProfile().getName();
        String recipientName = packet.recipientName();
        String rawConvId = packet.conversationId(); // "GROUP_INVITE:<groupId>:<groupName>"

        // Parsear groupId y groupName
        String[] parts = rawConvId.split(":", 3);
        if (parts.length < 3) {
            ICraftMod.LOGGER.warn("[iCraft] GROUP_INVITE malformado desde {}: {}", senderName, rawConvId);
            return;
        }
        String groupId   = parts[1];
        String groupName = parts[2];

        // Bloquear invitaciones al grupo global
        if (GLOBAL_GROUP_ID.equals(groupId)) {
            ICraftMod.LOGGER.warn("[iCraft] {} intentó invitar a {} al grupo global — ignorado", senderName, recipientName);
            return;
        }

        // Registrar grupo en memoria (sender + recipient)
        List<String> members = groups.computeIfAbsent(groupId, k -> new ArrayList<>());
        if (!members.contains(senderName)) members.add(senderName);
        if (!members.contains(recipientName)) members.add(recipientName);

        ICraftMod.LOGGER.info("[iCraft] Grupo privado '{}' ({}) registrado por {} — miembros: {}",
                groupName, groupId, senderName, members);

        // Reenviar invitación al destinatario si está online.
        // El contenido usa el prefijo §§GROUP_INVITE: que el cliente reconoce.
        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(recipientName);
        if (target != null) {
            String inviteContent = "§§GROUP_INVITE:" + groupId + ":" + groupName + ":" + senderName;
            ChatMessagePacket invitePacket = new ChatMessagePacket(
                    "group_invite_channel", senderName, inviteContent,
                    System.currentTimeMillis(), false,
                    java.util.UUID.randomUUID().toString());
            PacketDistributor.sendToPlayer(target, invitePacket);
        } else {
            // Offline: guardar en sus datos para que reciba la invitación al reconectarse
            PhoneData targetData = getOrCreate(recipientName);
            // Creamos una conversación del grupo si no existe aún
            boolean hasConv = targetData.conversations.stream().anyMatch(c -> c.id.equals(groupId));
            if (!hasConv) {
                PhoneData.ChatConversation pendingConv = new PhoneData.ChatConversation(groupId, groupName, true);
                pendingConv.members.addAll(members);
                // Agregar mensaje de invitación pendiente
                PhoneData.ChatMessage pendingMsg = new PhoneData.ChatMessage(senderName,
                        "§§GROUP_INVITE:" + groupId + ":" + groupName + ":" + senderName);
                pendingConv.messages.add(pendingMsg);
                targetData.conversations.add(pendingConv);
            }
            ICraftMod.LOGGER.info("[iCraft] {} offline — invitación al grupo '{}' guardada", recipientName, groupName);
        }
    }

    private static void handleMentions(String content, String senderName, MinecraftServer server) {
        String[] words = content.split(" ");
        for (String word : words) {
            if (word.startsWith("@")) {
                String mentioned = word.substring(1);
                ServerPlayer target = server.getPlayerList().getPlayerByName(mentioned);
                if (target != null) {
                    ChatMessagePacket ping = new ChatMessagePacket(
                            "mention", senderName,
                            "🔔 " + senderName + " te mencionó: " + content,
                            System.currentTimeMillis(), false, UUID.randomUUID().toString());
                    PacketDistributor.sendToPlayer(target, ping);
                }
            }
        }
    }

    public static void deleteMessageForAll(ServerPlayer sender, String convId, String msgId) {
        PhoneData data = getOrCreate(sender.getGameProfile().getName());
        PhoneData.ChatConversation conv = data.conversations.stream()
                .filter(c -> c.id.equals(convId)).findFirst().orElse(null);
        if (conv != null) {
            conv.messages.stream()
                    .filter(m -> m.id.equals(msgId))
                    .findFirst()
                    .ifPresent(m -> m.deletedForAll = true);
        }
    }

    // ===== WEATHER =====

    public static void sendWeatherData(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean rain = level.isRaining();
        boolean thunder = level.isThundering();
        long time = level.getDayTime();
        String biome = level.getBiome(player.blockPosition()).unwrapKey()
                .map(k -> k.location().getPath()).orElse("plains");
        String dim = level.dimension().location().toString();

        WeatherPacket packet = new WeatherPacket(rain, thunder, time, 20.0f, biome, dim);
        PacketDistributor.sendToPlayer(player, packet);
    }

    // ===== PLAYER LIST =====

    public static void sendPlayerList(ServerPlayer player) {
        List<String> names = player.getServer().getPlayerList().getPlayers()
                .stream().map(p -> p.getGameProfile().getName()).toList();
        PacketDistributor.sendToPlayer(player, new PlayerListPacket(names));
    }

    public static void sendAllData(ServerPlayer player) {
        sendWeatherData(player);
        sendPlayerList(player);
        sendWorldIcon(player);
        sendContacts(player);
        // Enviar historial del grupo mundial
        sendGlobalGroupHistory(player);
        // Enviar historial de conversaciones individuales (DMs)
        sendDMHistory(player);
    }

    // ===== CONTACTOS (jugadores que alguna vez se conectaron) =====

    private static Path knownPlayersFile = null;

    private static void loadKnownPlayersIfNeeded(MinecraftServer server) {
        if (knownPlayersLoaded) return;
        knownPlayersLoaded = true;
        knownPlayersFile = getDataDir(server).resolve("known_players.json");
        if (!Files.exists(knownPlayersFile)) return;
        try (Reader r = Files.newBufferedReader(knownPlayersFile)) {
            String[] names = GSON.fromJson(r, String[].class);
            if (names != null) knownPlayers.addAll(Arrays.asList(names));
        } catch (Exception e) {
            ICraftMod.LOGGER.error("[iCraft] Error al cargar known_players.json: {}", e.getMessage());
        }
    }

    private static void saveKnownPlayers(MinecraftServer server) {
        if (knownPlayersFile == null) knownPlayersFile = getDataDir(server).resolve("known_players.json");
        try (Writer w = Files.newBufferedWriter(knownPlayersFile)) {
            GSON.toJson(knownPlayers, w);
        } catch (IOException e) {
            ICraftMod.LOGGER.error("[iCraft] Error al guardar known_players.json: {}", e.getMessage());
        }
    }

    /** Manda la lista actualizada de contactos a todos los jugadores online
     *  (por ejemplo cuando se une alguien nuevo por primera vez). */
    public static void broadcastContactsUpdate(MinecraftServer server) {
        ContactsPacket packet = new ContactsPacket(new ArrayList<>(knownPlayers));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, packet);
        }
    }

    public static void sendContacts(ServerPlayer player) {
        loadKnownPlayersIfNeeded(player.getServer());
        PacketDistributor.sendToPlayer(player, new ContactsPacket(new ArrayList<>(knownPlayers)));
    }

    // ===== WORLD ICON =====

    /**
     * Lee el icon.png real del mundo (carpeta raíz del save) y lo manda al
     * cliente codificado en Base64, para usarlo como foto del chat global.
     * Si el mundo no tiene icon.png (es normal en muchos servidores
     * dedicados, ya que sólo se genera automáticamente en singleplayer/LAN),
     * se manda una cadena vacía y el cliente usa su ícono de globo de
     * respaldo.
     */
    public static void sendWorldIcon(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        String base64 = server != null ? loadWorldIconBase64(server) : "";
        PacketDistributor.sendToPlayer(player, new WorldIconPacket(base64));
    }

    /** Límite de seguridad para no mandar por la red un archivo absurdamente grande. */
    private static final long MAX_WORLD_ICON_BYTES = 262_144; // 256 KB

    private static String loadWorldIconBase64(MinecraftServer server) {
        try {
            Path iconPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("icon.png");
            if (!Files.exists(iconPath)) return "";
            byte[] bytes = Files.readAllBytes(iconPath);
            if (bytes.length > MAX_WORLD_ICON_BYTES) {
                ICraftMod.LOGGER.warn(
                        "[iCraft] icon.png del mundo es muy grande ({} bytes) — no se enviará",
                        bytes.length);
                return "";
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            ICraftMod.LOGGER.warn("[iCraft] No se pudo leer icon.png del mundo: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Envía el historial de conversaciones individuales (no-grupo) al cliente.
     * Últimos 50 mensajes por conversación.
     */
    private static void sendDMHistory(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        PhoneData data = playerData.get(name);
        if (data == null) return;

        for (PhoneData.ChatConversation conv : data.conversations) {
            if (conv.isGroup) continue; // grupos se manejan por broadcast
            int start = Math.max(0, conv.messages.size() - 50);
            for (int i = start; i < conv.messages.size(); i++) {
                PhoneData.ChatMessage m = conv.messages.get(i);
                ChatMessagePacket pkt = new ChatMessagePacket(
                        conv.id, m.sender, m.content, m.timestamp, false, m.id);
                PacketDistributor.sendToPlayer(player, pkt);
            }
        }
    }

    /**
     * Envía el historial del grupo mundial al cliente (últimos 50 mensajes).
     */
    private static void sendGlobalGroupHistory(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        PhoneData data = playerData.get(name);
        if (data == null) return;

        data.conversations.stream()
                .filter(c -> c.id.equals(GLOBAL_GROUP_ID))
                .findFirst()
                .ifPresent(conv -> {
                    // Reenviar los últimos 50 mensajes del historial
                    int start = Math.max(0, conv.messages.size() - 50);
                    for (int i = start; i < conv.messages.size(); i++) {
                        PhoneData.ChatMessage m = conv.messages.get(i);
                        ChatMessagePacket pkt = new ChatMessagePacket(
                                GLOBAL_GROUP_ID, m.sender, m.content, m.timestamp, true, m.id);
                        PacketDistributor.sendToPlayer(player, pkt);
                    }
                });
    }

    // ===== PHOTO UPLOAD (cliente → servidor → destinatarios) =====

    /**
     * Ruta de shared_photos/: fotos que los clientes suben al compartirlas en un DM
     * o grupo privado. A diferencia de admin_photos/, cualquier jugador puede subir
     * aquí (no requiere op). Las imágenes se reenvían a los destinatarios vía
     * AdminPhotoPacket exactamente igual que las fotos de admin.
     *
     * Ruta: <world_root>/iCraft/shared_photos/
     */
    public static Path getSharedPhotosDir(MinecraftServer server) {
        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("iCraft").resolve("shared_photos");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ICraftMod.LOGGER.warn("[iCraft] No se pudo crear shared_photos/: {}", e.getMessage());
        }
        return dir;
    }

    /**
     * Maneja un PhotoUploadPacket enviado por un cliente que quiere compartir una foto.
     *
     * Flujo:
     *   1. Valida el archivo (solo .png, tamaño ≤ 512 KB, sin path traversal).
     *   2. Guarda el PNG en shared_photos/ del servidor.
     *   3. Determina los destinatarios a partir del conversationId y el isGroup.
     *   4. Envía AdminPhotoPacket a cada destinatario online para que tengan el
     *      archivo localmente antes de que llegue la burbuja del chat.
     *
     * El mensaje §§PHOTO: de chat se envía por separado vía SendChatPacket
     * (que ya existía), igual que antes. TCP garantiza el orden: el PNG llega
     * antes que la burbuja, así que el receptor ya puede renderizarla.
     *
     * @param sender      jugador que comparte la foto
     * @param filename    nombre del archivo, ej. "screenshot_2024.png"
     * @param base64Png   PNG codificado en Base64
     * @param convId      ID de la conversación destino
     * @param isGroup     true si es grupo, false si es DM
     * @param recipientName nombre del destinatario (solo para DMs; ignorado en grupos)
     */
    public static void handlePhotoUpload(ServerPlayer sender, String filename,
                                         String base64Png, String convId,
                                         boolean isGroup, String recipientName) {
        // ── Validación ──────────────────────────────────────────────────────
        if (filename == null || base64Png == null || convId == null) return;
        if (!filename.endsWith(".png") || filename.contains("/")
                || filename.contains("\\") || filename.contains("..")) {
            ICraftMod.LOGGER.warn("[iCraft] handlePhotoUpload: nombre inválido \"{}\" de {}",
                    filename, sender.getGameProfile().getName());
            return;
        }
        // No permitir subir fotos al grupo global
        if (GLOBAL_GROUP_ID.equals(convId)) return;

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Png);
        } catch (IllegalArgumentException e) {
            ICraftMod.LOGGER.warn("[iCraft] handlePhotoUpload: Base64 inválido de {}: {}",
                    sender.getGameProfile().getName(), e.getMessage());
            return;
        }
        if (bytes.length > MAX_ADMIN_PHOTO_BYTES) {
            ICraftMod.LOGGER.warn("[iCraft] handlePhotoUpload: foto de {} supera {} KB — ignorada",
                    sender.getGameProfile().getName(), MAX_ADMIN_PHOTO_BYTES / 1024);
            return;
        }

        // ── Guardar en disco ────────────────────────────────────────────────
        try {
            Path dest = getSharedPhotosDir(sender.getServer()).resolve(filename);
            Files.write(dest, bytes);
        } catch (IOException e) {
            ICraftMod.LOGGER.warn("[iCraft] handlePhotoUpload: error guardando \"{}\": {}", filename, e.getMessage());
            return;
        }

        // ── Determinar destinatarios ────────────────────────────────────────
        List<ServerPlayer> targets = new ArrayList<>();
        if (isGroup) {
            List<String> members = groups.getOrDefault(convId, List.of());
            for (String member : members) {
                ServerPlayer t = sender.getServer().getPlayerList().getPlayerByName(member);
                if (t != null && !t.equals(sender)) targets.add(t);
            }
        } else {
            ServerPlayer t = sender.getServer().getPlayerList().getPlayerByName(recipientName);
            if (t != null) targets.add(t);
        }

        if (targets.isEmpty()) return;

        // ── Reenviar a destinatarios vía AdminPhotoPacket ───────────────────
        AdminPhotoPacket packet = new AdminPhotoPacket(filename, base64Png);
        for (ServerPlayer target : targets) {
            PacketDistributor.sendToPlayer(target, packet);
        }
        ICraftMod.LOGGER.info("[iCraft] Foto \"{}\" de {} enviada a {} jugador(es) ({} KB)",
                filename, sender.getGameProfile().getName(), targets.size(), bytes.length / 1024);
    }

    // ===== FOTOS IMPRESAS / CUADROS (impresora -> world_photos -> todos los clientes) =====

    /**
     * Repositorio CENTRAL y PERSISTENTE de todas las fotos que algún jugador
     * imprimió alguna vez en este mundo. A diferencia de iCraft/photos/ (que es
     * una carpeta LOCAL del cliente, dentro de su carpeta de instalación de
     * Minecraft), esta carpeta vive en el servidor/mundo, así que cualquier
     * jugador —esté online en el momento de imprimir o se conecte después—
     * puede recibir la imagen con solo pedirla (ver handleRequestPhoto).
     *
     * Ruta: <world_root>/iCraft/world_photos/
     */
    public static Path getWorldPhotosDir(MinecraftServer server) {
        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("iCraft").resolve("world_photos");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ICraftMod.LOGGER.warn("[iCraft] No se pudo crear world_photos/: {}", e.getMessage());
        }
        return dir;
    }

    /**
     * Maneja el PrintPhotoPacket enviado al apretar "Imprimir foto" en la Impresora.
     *
     * Flujo:
     *   1. Valida el archivo (solo .png, ≤512 KB, sin path traversal).
     *   2. Lo guarda en world_photos/ (repositorio central del mundo).
     *   3. Crea el ItemStack de la foto impresa y lo agrega al inventario REAL
     *      del jugador en el servidor (antes esto se hacía solo del lado del
     *      cliente y nunca llegaba al servidor — por eso "parecía" imprimir
     *      pero al usarla contra una pared no pasaba nada).
     *   4. Reenvía la imagen a todos los jugadores conectados vía AdminPhotoPacket,
     *      para que ya la tengan en caché si ven el cuadro colgado en el mundo.
     */
    public static void handlePrintPhoto(ServerPlayer player, String filename, String base64Png) {
        if (filename == null || base64Png == null) return;
        if (!filename.endsWith(".png") || filename.contains("/")
                || filename.contains("\\") || filename.contains("..")) {
            ICraftMod.LOGGER.warn("[iCraft] handlePrintPhoto: nombre inválido \"{}\" de {}",
                    filename, player.getGameProfile().getName());
            return;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Png);
        } catch (IllegalArgumentException e) {
            ICraftMod.LOGGER.warn("[iCraft] handlePrintPhoto: Base64 inválido de {}: {}",
                    player.getGameProfile().getName(), e.getMessage());
            return;
        }
        if (bytes.length > MAX_ADMIN_PHOTO_BYTES) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§cLa foto pesa más de " + (MAX_ADMIN_PHOTO_BYTES / 1024) + " KB, no se puede imprimir."),
                    true);
            return;
        }

        try {
            Path dest = getWorldPhotosDir(player.getServer()).resolve(filename);
            Files.write(dest, bytes);
        } catch (IOException e) {
            ICraftMod.LOGGER.warn("[iCraft] handlePrintPhoto: error guardando \"{}\": {}", filename, e.getMessage());
            return;
        }

        // Consumir papel fotográfico sin revelar del inventario del jugador
        boolean consumed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty()
                    && slot.getItem() == com.icraft.init.ModItems.PRINTED_PHOTO.get()
                    && com.icraft.item.PrintedPhotoItem.getFilename(slot).isEmpty()) {
                slot.shrink(1);
                consumed = true;
                break;
            }
        }
        if (!consumed) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§cNecesitás papel fotográfico sin revelar."), true);
            return;
        }

        net.minecraft.world.item.ItemStack printed =
                new net.minecraft.world.item.ItemStack(com.icraft.init.ModItems.PRINTED_PHOTO.get());
        net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
        nbt.putString("photoFilename", filename);
        printed.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(nbt));
        if (!player.getInventory().add(printed)) {
            player.drop(printed, false);
        }
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§a🖨 Foto impresa."), true);

        // Reenviar a todos los jugadores online para que ya la tengan cacheada.
        AdminPhotoPacket packet = new AdminPhotoPacket(filename, base64Png);
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(online, packet);
        }

        ICraftMod.LOGGER.info("[iCraft] {} imprimió \"{}\" ({} KB) — distribuida a {} jugador(es)",
                player.getGameProfile().getName(), filename, bytes.length / 1024,
                player.getServer().getPlayerList().getPlayerCount());
    }

    /**
     * Maneja el RequestPhotoPacket: un cliente no tiene localmente el archivo
     * que necesita para renderizar un cuadro (PhotoFrameEntity) o una foto
     * impresa, y lo pide. Buscamos primero en world_photos/ (fotos impresas) y,
     * como respaldo, en admin_photos/ y shared_photos/ (otros orígenes posibles
     * del mismo nombre de archivo).
     */
    public static void handleRequestPhoto(ServerPlayer player, String filename) {
        if (filename == null || filename.isEmpty()) return;
        if (!filename.endsWith(".png") || filename.contains("/")
                || filename.contains("\\") || filename.contains("..")) {
            return;
        }

        MinecraftServer server = player.getServer();
        Path[] candidates = {
                getWorldPhotosDir(server).resolve(filename),
                getAdminPhotosDir(server).resolve(filename),
                getSharedPhotosDir(server).resolve(filename)
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    byte[] bytes = Files.readAllBytes(candidate);
                    if (bytes.length > MAX_ADMIN_PHOTO_BYTES) continue;
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    PacketDistributor.sendToPlayer(player, new AdminPhotoPacket(filename, base64));
                } catch (IOException e) {
                    ICraftMod.LOGGER.warn("[iCraft] handleRequestPhoto: error leyendo \"{}\": {}",
                            filename, e.getMessage());
                }
                return;
            }
        }
        // No se encontró en ningún repositorio: no había nada para reenviar.
    }

    // ===== ADMIN PHOTOS =====

    /**
     * Tamaño máximo permitido para una imagen de admin_photos/.
     * Imágenes más grandes se rechazan antes de intentar enviarlas por red.
     * 512 KB es suficiente para PNGs razonables; reducir si la conexión es lenta.
     */
    public static final long MAX_ADMIN_PHOTO_BYTES = 512 * 1024L; // 512 KB

    /**
     * Devuelve la ruta de la carpeta admin_photos/ dentro del directorio del servidor.
     * La crea si no existe. Los ops suben imágenes PNG directamente a esta carpeta
     * y luego las publican con /icraft sendphoto <archivo.png>.
     *
     * Ruta: <server_root>/iCraft/admin_photos/
     */
    public static Path getAdminPhotosDir(MinecraftServer server) {
        // Usamos la ruta del nivel (igual que getDataDir) para evitar que
        // getServerDirectory() devuelva null o un path incorrecto en NeoForge 1.21.
        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("iCraft").resolve("admin_photos");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ICraftMod.LOGGER.warn("[iCraft] No se pudo crear admin_photos/: {}", e.getMessage());
        }
        return dir;
    }

    /**
     * Devuelve los nombres de todos los .png disponibles en admin_photos/,
     * ordenados alfabéticamente. Usado por el autocompletado del comando.
     */
    public static List<String> listAdminPhotos(MinecraftServer server) {
        Path dir = getAdminPhotosDir(server);
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Lee un PNG de admin_photos/, lo codifica en Base64 y lo envía a todos
     * los jugadores online vía AdminPhotoPacket. Cada cliente lo guarda en su
     * carpeta iCraft/photos/ y el servidor luego broadcastea el mensaje
     * §§PHOTO:<filename> al grupo Global.
     *
     * Si el archivo no existe, es demasiado grande, o no es un PNG válido,
     * lanza una IllegalArgumentException con el mensaje de error para mostrar
     * al op que ejecutó el comando.
     *
     * @param server   servidor de Minecraft
     * @param filename nombre del archivo, ej. "cartel.png"
     * @throws IllegalArgumentException si el archivo no se puede enviar
     */
    public static void broadcastAdminPhoto(MinecraftServer server, String filename) {
        Path photoPath = getAdminPhotosDir(server).resolve(filename);

        // Validación de seguridad: solo .png, sin path traversal
        if (!filename.endsWith(".png") || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException("Nombre de archivo inválido: " + filename);
        }
        if (!Files.exists(photoPath)) {
            throw new IllegalArgumentException("No existe \"" + filename + "\" en iCraft/admin_photos/");
        }

        byte[] bytes;
        try {
            long size = Files.size(photoPath);
            if (size > MAX_ADMIN_PHOTO_BYTES) {
                throw new IllegalArgumentException(
                        "La imagen pesa " + (size / 1024) + " KB — el límite es " +
                        (MAX_ADMIN_PHOTO_BYTES / 1024) + " KB. Reducí el tamaño del PNG.");
            }
            bytes = Files.readAllBytes(photoPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer \"" + filename + "\": " + e.getMessage());
        }

        String base64 = Base64.getEncoder().encodeToString(bytes);
        AdminPhotoPacket packet = new AdminPhotoPacket(filename, base64);

        // Enviar a todos los jugadores online
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, packet);
        }

        ICraftMod.LOGGER.info("[iCraft] Admin photo \"{}\" enviada a {} jugadores ({} KB)",
                filename, server.getPlayerList().getPlayerCount(), bytes.length / 1024);
    }

    // ===== MARKETPLACE =====

    public static void handleMarketplace(ServerPlayer player, MarketplacePacket packet) {
        switch (packet.action()) {
            case "create" -> {
                PhoneData.MarketListing listing = new PhoneData.MarketListing(
                        player.getGameProfile().getName(),
                        packet.itemName(), packet.itemId(),
                        packet.quantity(), packet.price(),
                        packet.description()
                );
                globalMarket.add(listing);
                ICraftMod.LOGGER.info("New marketplace listing by {}: {}", player.getGameProfile().getName(), packet.itemName());
            }
            case "buy" -> {
                globalMarket.stream()
                        .filter(l -> l.id.equals(packet.listingId()) && l.active)
                        .findFirst().ifPresent(l -> {
                            l.active = false;
                            ICraftMod.LOGGER.info("{} bought {} from {}", player.getGameProfile().getName(), l.itemName, l.seller);
                        });
            }
            case "remove" -> {
                globalMarket.removeIf(l -> l.id.equals(packet.listingId())
                        && l.seller.equals(player.getGameProfile().getName()));
            }
        }
    }
}
