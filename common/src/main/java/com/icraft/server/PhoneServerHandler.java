package com.icraft.server;

import com.google.gson.*;
import com.icraft.ICraftConstants;
import com.icraft.data.PhoneData;
import com.icraft.network.*;
import com.icraft.network.AdminPhotoPacket;
import com.icraft.voicechat.ICraftVoicechatPlugin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import dev.architectury.networking.NetworkManager;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhoneServerHandler {

    public static final String GLOBAL_GROUP_ID   = "mundial";
    public static final String GLOBAL_GROUP_NAME = "Global";

    public static final String SYSTEM_SENDER = "§a\u0001SYS\u0001";

    private static final Map<String, PhoneData> playerData = new ConcurrentHashMap<>();

    private static final java.util.Set<String> announcedJoins = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static final List<PhoneData.MarketListing> globalMarket = new ArrayList<>();

    private static final Map<String, List<String>> groups = new ConcurrentHashMap<>();

    private static final Set<String> knownPlayers = ConcurrentHashMap.newKeySet();
    private static boolean knownPlayersLoaded = false;

    private static final Map<UUID, UUID> pendingCalls = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> ringTicks = new ConcurrentHashMap<>();

    private static final int RING_INTERVAL_TICKS = 40;

    private static UUID findCallerFor(UUID targetId) {
        for (Map.Entry<UUID, UUID> e : pendingCalls.entrySet()) {
            if (e.getValue().equals(targetId)) return e.getKey();
        }
        return null;
    }

    private static boolean isCallBusy(UUID playerId) {
        return ICraftVoicechatPlugin.getCallPeer(playerId) != null
                || pendingCalls.containsKey(playerId)
                || pendingCalls.containsValue(playerId);
    }

    private static volatile boolean vanillaChatEnabled = false;
    private static boolean vanillaChatSettingLoaded = false;
    private static Path vanillaChatFile = null;

    public static boolean isVanillaChatEnabled(MinecraftServer server) {
        loadVanillaChatSettingIfNeeded(server);
        return vanillaChatEnabled;
    }

    public static void setVanillaChatEnabled(MinecraftServer server, boolean enabled) {
        vanillaChatEnabled = enabled;
        vanillaChatSettingLoaded = true;
        if (vanillaChatFile == null) vanillaChatFile = getDataDir(server).resolve("vanilla_chat.properties");
        try {
            Files.createDirectories(vanillaChatFile.getParent());
            Properties props = new Properties();
            props.setProperty("enabled", String.valueOf(enabled));
            try (OutputStream os = Files.newOutputStream(vanillaChatFile)) {
                props.store(os, "iCraft - estado del chat vanilla de Minecraft");
            }
        } catch (IOException e) {
            ICraftConstants.LOGGER.error("[iCraft] Error al guardar vanilla_chat.properties: {}", e.getMessage());
        }
    }

    private static void loadVanillaChatSettingIfNeeded(MinecraftServer server) {
        if (vanillaChatSettingLoaded) return;
        vanillaChatSettingLoaded = true;
        vanillaChatFile = getDataDir(server).resolve("vanilla_chat.properties");
        if (!Files.exists(vanillaChatFile)) return;
        try (InputStream is = Files.newInputStream(vanillaChatFile)) {
            Properties props = new Properties();
            props.load(is);
            vanillaChatEnabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
        } catch (Exception e) {
            ICraftConstants.LOGGER.error("[iCraft] Error al cargar vanilla_chat.properties: {}", e.getMessage());
        }
    }

    private static volatile boolean globalImagesEnabled = false;
    private static boolean globalImagesSettingLoaded = false;
    private static Path globalImagesFile = null;

    public static boolean isGlobalImagesEnabled(MinecraftServer server) {
        loadGlobalImagesSettingIfNeeded(server);
        return globalImagesEnabled;
    }

    public static void setGlobalImagesEnabled(MinecraftServer server, boolean enabled) {
        globalImagesEnabled = enabled;
        globalImagesSettingLoaded = true;
        if (globalImagesFile == null) globalImagesFile = getDataDir(server).resolve("global_images.properties");
        try {
            Files.createDirectories(globalImagesFile.getParent());
            Properties props = new Properties();
            props.setProperty("enabled", String.valueOf(enabled));
            try (OutputStream os = Files.newOutputStream(globalImagesFile)) {
                props.store(os, "iCraft - estado de imagenes en el grupo Global");
            }
        } catch (IOException e) {
            ICraftConstants.LOGGER.error("[iCraft] Error al guardar global_images.properties: {}", e.getMessage());
        }
        broadcastGlobalImagesSetting(server);
    }

    private static void loadGlobalImagesSettingIfNeeded(MinecraftServer server) {
        if (globalImagesSettingLoaded) return;
        globalImagesSettingLoaded = true;
        globalImagesFile = getDataDir(server).resolve("global_images.properties");
        if (!Files.exists(globalImagesFile)) return;
        try (InputStream is = Files.newInputStream(globalImagesFile)) {
            Properties props = new Properties();
            props.load(is);
            globalImagesEnabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
        } catch (Exception e) {
            ICraftConstants.LOGGER.error("[iCraft] Error al cargar global_images.properties: {}", e.getMessage());
        }
    }

    public static void sendGlobalImagesSetting(ServerPlayer player) {
        NetworkManager.sendToPlayer(player,
                new GlobalImagesSettingPacket(isGlobalImagesEnabled(player.getServer())));
    }

    public static void broadcastGlobalImagesSetting(MinecraftServer server) {
        GlobalImagesSettingPacket packet = new GlobalImagesSettingPacket(globalImagesEnabled);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(p, packet);
        }
    }

    private static Path dataDir = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path getDataDir(MinecraftServer server) {
        if (dataDir == null) {
            dataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("iCraft").resolve("data");
            try {
                Files.createDirectories(dataDir);
            } catch (IOException e) {
                ICraftConstants.LOGGER.error("No se pudo crear el directorio iCraft/data: {}", e.getMessage());
            }
        }
        return dataDir;
    }

    public static PhoneData loadPlayerData(String playerName, MinecraftServer server) {
        Path file = getDataDir(server).resolve(playerName + ".json");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                PhoneData data = GSON.fromJson(r, PhoneData.class);
                if (data != null) {
                    playerData.put(playerName, data);
                    ICraftConstants.LOGGER.info("[iCraft] Datos cargados para {}", playerName);
                    return data;
                }
            } catch (Exception e) {
                ICraftConstants.LOGGER.error("[iCraft] Error al cargar datos de {}: {}", playerName, e.getMessage());
            }
        }

        PhoneData fresh = new PhoneData();
        playerData.put(playerName, fresh);
        return fresh;
    }

    public static void savePlayerData(String playerName, MinecraftServer server) {
        PhoneData data = playerData.get(playerName);
        if (data == null) return;
        Path file = getDataDir(server).resolve(playerName + ".json");
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(data, w);
            ICraftConstants.LOGGER.debug("[iCraft] Datos guardados para {}", playerName);
        } catch (IOException e) {
            ICraftConstants.LOGGER.error("[iCraft] Error al guardar datos de {}: {}", playerName, e.getMessage());
        }
    }

    public static void saveAllPlayerData(MinecraftServer server) {
        for (String name : playerData.keySet()) {
            savePlayerData(name, server);
        }
    }

    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        String name = player.getGameProfile().getName();

        PhoneData data = loadPlayerData(name, server);

        loadKnownPlayersIfNeeded(server);
        if (knownPlayers.add(name)) {
            saveKnownPlayers(server);
            broadcastContactsUpdate(server);
        }

        List<String> members = groups.computeIfAbsent(GLOBAL_GROUP_ID, k -> new ArrayList<>());
        if (!members.contains(name)) {
            members.add(name);
            ICraftConstants.LOGGER.info("[iCraft] {} se unió al grupo Mundial", name);
        }

        boolean hasGroup = data.conversations.stream()
                .anyMatch(c -> c.id.equals(GLOBAL_GROUP_ID));
        if (!hasGroup) {
            PhoneData.ChatConversation globalConv = new PhoneData.ChatConversation(
                    GLOBAL_GROUP_ID, GLOBAL_GROUP_NAME, true);
            globalConv.members.addAll(members);
            data.conversations.add(0, globalConv);
        } else {

            data.conversations.stream()
                    .filter(c -> c.id.equals(GLOBAL_GROUP_ID))
                    .findFirst()
                    .ifPresent(c -> {
                        c.members.clear();
                        c.members.addAll(members);
                    });
        }

        if (announcedJoins.add(name)) {
            String joinMsg = "§§JOIN:" + name;
            broadcastToGlobalGroup(server, SYSTEM_SENDER, joinMsg);
        }

        sendAllData(player);
    }

    public static void onPlayerLeave(ServerPlayer player, MinecraftServer server) {
        String name = player.getGameProfile().getName();
        savePlayerData(name, server);
        announcedJoins.remove(name);

        UUID myId = player.getUUID();

        UUID callPeerId = ICraftVoicechatPlugin.getCallPeer(myId);
        ICraftVoicechatPlugin.endCall(myId);
        if (callPeerId != null) {
            ServerPlayer peerPlayer = server.getPlayerList().getPlayer(callPeerId);
            if (peerPlayer != null) {
                NetworkManager.sendToPlayer(peerPlayer, new CallStatusPacket(name, "ENDED"));
            }
        }

        UUID pendingTargetId = pendingCalls.remove(myId);
        if (pendingTargetId != null) {
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(pendingTargetId);
            if (targetPlayer != null) {
                NetworkManager.sendToPlayer(targetPlayer, new CallStatusPacket(name, "ENDED"));
            }
        }
        UUID pendingCallerId = findCallerFor(myId);
        if (pendingCallerId != null) {
            pendingCalls.remove(pendingCallerId);
            ServerPlayer callerPlayer = server.getPlayerList().getPlayer(pendingCallerId);
            if (callerPlayer != null) {
                NetworkManager.sendToPlayer(callerPlayer, new CallStatusPacket(name, "ENDED"));
            }
        }

        String leaveMsg = "§§LEAVE:" + name;
        broadcastToGlobalGroup(server, SYSTEM_SENDER, leaveMsg);

        broadcastPhoneOpenState(player, false);
    }

    public static void handleCallRequest(ServerPlayer sender, CallRequestPacket packet) {
        String myName = sender.getGameProfile().getName();
        UUID myId = sender.getUUID();

        if (packet.hangUp()) {

            UUID pendingTargetId = pendingCalls.remove(myId);
            if (pendingTargetId != null) {
                NetworkManager.sendToPlayer(sender, new CallStatusPacket("", "ENDED"));
                ServerPlayer targetPlayer = sender.getServer().getPlayerList().getPlayer(pendingTargetId);
                if (targetPlayer != null) {
                    NetworkManager.sendToPlayer(targetPlayer, new CallStatusPacket(myName, "ENDED"));
                }
                return;
            }

            UUID pendingCallerId = findCallerFor(myId);
            if (pendingCallerId != null) {
                pendingCalls.remove(pendingCallerId);
                NetworkManager.sendToPlayer(sender, new CallStatusPacket("", "ENDED"));
                ServerPlayer callerPlayer = sender.getServer().getPlayerList().getPlayer(pendingCallerId);
                if (callerPlayer != null) {
                    NetworkManager.sendToPlayer(callerPlayer, new CallStatusPacket(myName, "DECLINED"));
                }
                return;
            }

            UUID peerId = ICraftVoicechatPlugin.getCallPeer(myId);
            ICraftVoicechatPlugin.endCall(myId);
            NetworkManager.sendToPlayer(sender, new CallStatusPacket("", "ENDED"));
            if (peerId != null) {
                ServerPlayer peerPlayer = sender.getServer().getPlayerList().getPlayer(peerId);
                if (peerPlayer != null) {
                    NetworkManager.sendToPlayer(peerPlayer, new CallStatusPacket(myName, "ENDED"));
                }
            }
            return;
        }

        String targetName = packet.targetName();
        if (targetName == null || targetName.isEmpty() || targetName.equals(myName)) {
            return;
        }

        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            NetworkManager.sendToPlayer(sender, new CallStatusPacket(targetName, "TARGET_OFFLINE"));
            return;
        }
        UUID targetId = target.getUUID();

        if (isCallBusy(myId) || isCallBusy(targetId)) {
            NetworkManager.sendToPlayer(sender, new CallStatusPacket(targetName, "TARGET_BUSY"));
            return;
        }

        ICraftVoicechatPlugin.CallResult check = ICraftVoicechatPlugin.checkCallable(myId, targetId);
        switch (check) {
            case OK -> {
                pendingCalls.put(myId, targetId);
                NetworkManager.sendToPlayer(sender, new CallStatusPacket(targetName, "RINGING"));
                NetworkManager.sendToPlayer(target, new CallStatusPacket(myName, "INCOMING"));
            }
            case VOICECHAT_NOT_INSTALLED ->
                    NetworkManager.sendToPlayer(sender, new CallStatusPacket(targetName, "NOT_INSTALLED"));
            case CALLER_NOT_CONNECTED ->
                    NetworkManager.sendToPlayer(sender, new CallStatusPacket(targetName, "CALLER_NOT_CONNECTED"));
            case TARGET_NOT_CONNECTED ->
                    NetworkManager.sendToPlayer(sender, new CallStatusPacket(targetName, "TARGET_NOT_CONNECTED"));
        }
    }

    public static void handleCallAnswer(ServerPlayer answerer, boolean accepted) {
        UUID answererId = answerer.getUUID();
        UUID callerId = findCallerFor(answererId);
        if (callerId == null) return;
        pendingCalls.remove(callerId);

        ServerPlayer caller = answerer.getServer().getPlayerList().getPlayer(callerId);
        String answererName = answerer.getGameProfile().getName();

        if (caller == null) return;

        String callerName = caller.getGameProfile().getName();

        if (!accepted) {
            NetworkManager.sendToPlayer(caller, new CallStatusPacket(answererName, "DECLINED"));
            return;
        }

        ICraftVoicechatPlugin.CallResult result = ICraftVoicechatPlugin.startCall(callerId, answererId);
        if (result == ICraftVoicechatPlugin.CallResult.OK) {
            NetworkManager.sendToPlayer(caller, new CallStatusPacket(answererName, "CONNECTED_CALLER"));
            NetworkManager.sendToPlayer(answerer, new CallStatusPacket(callerName, "CONNECTED_CALLEE"));
        } else {

            NetworkManager.sendToPlayer(caller, new CallStatusPacket(answererName, "ENDED"));
            NetworkManager.sendToPlayer(answerer, new CallStatusPacket(callerName, "ENDED"));
        }
    }

    public static void handleCallMute(ServerPlayer player, boolean muted) {
        UUID peerId = ICraftVoicechatPlugin.getCallPeer(player.getUUID());
        if (peerId == null) return;

        ServerPlayer peerPlayer = player.getServer().getPlayerList().getPlayer(peerId);
        if (peerPlayer == null) return;

        String myName = player.getGameProfile().getName();
        NetworkManager.sendToPlayer(peerPlayer, new CallStatusPacket(myName, muted ? "PEER_MUTED" : "PEER_UNMUTED"));
    }

    public static void broadcastPhoneOpenState(ServerPlayer player, boolean open) {
        PhoneOpenSyncPacket packet = new PhoneOpenSyncPacket(player.getUUID(), open);
        for (ServerPlayer target : player.server.getPlayerList().getPlayers()) {
            if (target == player) continue;
            NetworkManager.sendToPlayer(target, packet);
        }

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

    private static InteractionHand handHoldingSmartphone(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof com.icraft.item.SmartphoneItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof com.icraft.item.SmartphoneItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public static void tickCallRinging(MinecraftServer server) {
        if (pendingCalls.isEmpty()) {
            if (!ringTicks.isEmpty()) ringTicks.clear();
            return;
        }
        ringTicks.keySet().retainAll(pendingCalls.keySet());

        for (Map.Entry<UUID, UUID> e : pendingCalls.entrySet()) {
            UUID callerId = e.getKey();
            UUID targetId = e.getValue();

            int t = ringTicks.merge(callerId, 1, Integer::sum);
            if (t == 1 || t % RING_INTERVAL_TICKS == 0) {
                ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                if (target != null) {
                    broadcastRingSound(target);
                }
            }
        }
    }

    private static void broadcastRingSound(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        String chosen = getOrCreate(target.getGameProfile().getName()).callSound;

        net.minecraft.sounds.SoundEvent event;
        if (chosen != null && !chosen.isEmpty()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ICraftConstants.MODID, "call." + chosen);
            event = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id);
        } else {
            event = SoundEvents.NOTE_BLOCK_BELL.value();
        }

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                event, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static final Set<UUID> phoneArmRaised = ConcurrentHashMap.newKeySet();

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

    public static void broadcastToGlobalGroup(MinecraftServer server, String senderName, String content) {
        long ts = System.currentTimeMillis();
        String msgId = UUID.randomUUID().toString();
        List<String> members = groups.getOrDefault(GLOBAL_GROUP_ID, List.of());

        ChatMessagePacket msg = new ChatMessagePacket(
                GLOBAL_GROUP_ID, senderName, content, ts, true, msgId);

        for (String member : members) {
            ServerPlayer target = server.getPlayerList().getPlayerByName(member);
            if (target != null) {
                NetworkManager.sendToPlayer(target, msg);

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

    public static PhoneData getOrCreate(String playerName) {
        return playerData.computeIfAbsent(playerName, k -> new PhoneData());
    }

    public static void setCallRingtone(ServerPlayer player, String soundId) {
        String name = player.getGameProfile().getName();
        getOrCreate(name).callSound = soundId == null ? "" : soundId;
    }

    public static boolean swapPhoneCase(ServerPlayer player, net.minecraft.world.item.ItemStack caseStackToConsume, String newCaseId) {
        String name = player.getGameProfile().getName();
        PhoneData data = getOrCreate(name);

        if (data.currentCase.equals(newCaseId)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("icraft.phone.case.already_equipped"), true);
            return false;
        }

        caseStackToConsume.shrink(1);

        String oldCaseId = data.currentCase;
        if (!oldCaseId.equals("default")) {
            net.minecraft.world.item.Item oldItem = caseItemFor(oldCaseId);
            if (oldItem != null) {
                net.minecraft.world.item.ItemStack give = new net.minecraft.world.item.ItemStack(oldItem);
                if (!player.getInventory().add(give)) {
                    player.drop(give, false);
                }
            }
        }

        data.currentCase = newCaseId;
        if (player.getServer() != null) {
            savePlayerData(name, player.getServer());
        }

        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("icraft.phone.case.equipped",
                        net.minecraft.network.chat.Component.translatable("item.icraft.phone_case_" + newCaseId)),
                true);
        return true;
    }

    private static net.minecraft.world.item.Item caseItemFor(String caseId) {
        return switch (caseId) {
            case "default" -> com.icraft.init.ModItems.PHONE_CASE_DEFAULT.get();
            case "black"   -> com.icraft.init.ModItems.PHONE_CASE_BLACK.get();
            case "white"   -> com.icraft.init.ModItems.PHONE_CASE_WHITE.get();
            case "neon"    -> com.icraft.init.ModItems.PHONE_CASE_NEON.get();
            case "diamond" -> com.icraft.init.ModItems.PHONE_CASE_DIAMOND.get();
            default -> null;
        };
    }

    public static String canonicalDmId(String playerA, String playerB) {
        if (playerA.compareToIgnoreCase(playerB) <= 0) {
            return "dm_" + playerA + "_" + playerB;
        } else {
            return "dm_" + playerB + "_" + playerA;
        }
    }

    public static void routeMessage(ServerPlayer sender, SendChatPacket packet) {
        String senderName = sender.getGameProfile().getName();
        long timestamp = System.currentTimeMillis();

        String convId = packet.conversationId();
        if (convId != null && convId.startsWith("GROUP_INVITE:")) {
            handleGroupInvite(sender, packet);
            return;
        }

        final String content = (packet.content() != null && packet.content().contains("&") && sender.hasPermissions(2))
                ? packet.content().replace('&', '§')
                : packet.content();

        if (content.contains("@")) {
            handleMentions(content, senderName, sender.getServer());
        }

        if (packet.isGroup()) {
            String groupId = packet.conversationId();
            List<String> members;

            if (GLOBAL_GROUP_ID.equals(groupId)) {
                members = groups.getOrDefault(GLOBAL_GROUP_ID, List.of());
            } else {
                members = groups.getOrDefault(groupId, List.of());
            }

            String msgId = (packet.messageId() != null && !packet.messageId().isEmpty())
                    ? packet.messageId() : UUID.randomUUID().toString();

            ChatMessagePacket msg = new ChatMessagePacket(
                    groupId, senderName, content, timestamp, true, msgId);

            for (String member : members) {
                ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(member);
                if (target != null && !target.equals(sender)) {
                    NetworkManager.sendToPlayer(target, msg);
                }

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

            String recipientName = packet.recipientName();
            if (senderName.equals(recipientName)) {
                ICraftConstants.LOGGER.warn("[iCraft] {} intentó enviarse un DM a sí mismo — ignorado", senderName);
                return;
            }

            String msgId = (packet.messageId() != null && !packet.messageId().isEmpty())
                    ? packet.messageId() : UUID.randomUUID().toString();

            String canonicalId = canonicalDmId(senderName, recipientName);

            ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(recipientName);
            if (target != null) {

                ChatMessagePacket msg = new ChatMessagePacket(
                        canonicalId, senderName, content, timestamp, false, msgId);
                NetworkManager.sendToPlayer(target, msg);

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

                ICraftConstants.LOGGER.info("[iCraft] {} offline — mensaje guardado para cuando vuelva", recipientName);
            }
        }
    }

    private static void handleGroupInvite(ServerPlayer sender, SendChatPacket packet) {
        String senderName = sender.getGameProfile().getName();
        String recipientName = packet.recipientName();
        String rawConvId = packet.conversationId();

        String[] parts = rawConvId.split(":", 3);
        if (parts.length < 3) {
            ICraftConstants.LOGGER.warn("[iCraft] GROUP_INVITE malformado desde {}: {}", senderName, rawConvId);
            return;
        }
        String groupId   = parts[1];
        String groupName = parts[2];

        if (GLOBAL_GROUP_ID.equals(groupId)) {
            ICraftConstants.LOGGER.warn("[iCraft] {} intentó invitar a {} al grupo global — ignorado", senderName, recipientName);
            return;
        }

        List<String> members = groups.computeIfAbsent(groupId, k -> new ArrayList<>());
        if (!members.contains(senderName)) members.add(senderName);
        if (!members.contains(recipientName)) members.add(recipientName);

        ICraftConstants.LOGGER.info("[iCraft] Grupo privado '{}' ({}) registrado por {} — miembros: {}",
                groupName, groupId, senderName, members);

        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(recipientName);
        if (target != null) {
            String inviteContent = "§§GROUP_INVITE:" + groupId + ":" + groupName + ":" + senderName;
            ChatMessagePacket invitePacket = new ChatMessagePacket(
                    "group_invite_channel", senderName, inviteContent,
                    System.currentTimeMillis(), false,
                    java.util.UUID.randomUUID().toString());
            NetworkManager.sendToPlayer(target, invitePacket);
        } else {

            PhoneData targetData = getOrCreate(recipientName);

            boolean hasConv = targetData.conversations.stream().anyMatch(c -> c.id.equals(groupId));
            if (!hasConv) {
                PhoneData.ChatConversation pendingConv = new PhoneData.ChatConversation(groupId, groupName, true);
                pendingConv.members.addAll(members);

                PhoneData.ChatMessage pendingMsg = new PhoneData.ChatMessage(senderName,
                        "§§GROUP_INVITE:" + groupId + ":" + groupName + ":" + senderName);
                pendingConv.messages.add(pendingMsg);
                targetData.conversations.add(pendingConv);
            }
            ICraftConstants.LOGGER.info("[iCraft] {} offline — invitación al grupo '{}' guardada", recipientName, groupName);
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
                    NetworkManager.sendToPlayer(target, ping);
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

    public static void sendWeatherData(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean rain = level.isRaining();
        boolean thunder = level.isThundering();
        long time = level.getDayTime();
        String biome = level.getBiome(player.blockPosition()).unwrapKey()
                .map(k -> k.location().getPath()).orElse("plains");
        String dim = level.dimension().location().toString();

        WeatherPacket packet = new WeatherPacket(rain, thunder, time, 20.0f, biome, dim);
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendPlayerList(ServerPlayer player) {
        List<String> names = player.getServer().getPlayerList().getPlayers()
                .stream().map(p -> p.getGameProfile().getName()).toList();
        NetworkManager.sendToPlayer(player, new PlayerListPacket(names));
    }

    public static void sendAllData(ServerPlayer player) {
        sendWeatherData(player);
        sendPlayerList(player);
        sendWorldIcon(player);
        sendContacts(player);

        sendGlobalGroupHistory(player);

        sendDMHistory(player);

        sendGlobalImagesSetting(player);
    }

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
            ICraftConstants.LOGGER.error("[iCraft] Error al cargar known_players.json: {}", e.getMessage());
        }
    }

    private static void saveKnownPlayers(MinecraftServer server) {
        if (knownPlayersFile == null) knownPlayersFile = getDataDir(server).resolve("known_players.json");
        try (Writer w = Files.newBufferedWriter(knownPlayersFile)) {
            GSON.toJson(knownPlayers, w);
        } catch (IOException e) {
            ICraftConstants.LOGGER.error("[iCraft] Error al guardar known_players.json: {}", e.getMessage());
        }
    }

    public static void broadcastContactsUpdate(MinecraftServer server) {
        ContactsPacket packet = new ContactsPacket(new ArrayList<>(knownPlayers));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(p, packet);
        }
    }

    public static void sendContacts(ServerPlayer player) {
        loadKnownPlayersIfNeeded(player.getServer());
        NetworkManager.sendToPlayer(player, new ContactsPacket(new ArrayList<>(knownPlayers)));
    }

    public static void sendWorldIcon(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        String base64 = server != null ? loadWorldIconBase64(server) : "";
        NetworkManager.sendToPlayer(player, new WorldIconPacket(base64));
    }

    private static final long MAX_WORLD_ICON_BYTES = 262_144;

    private static String loadWorldIconBase64(MinecraftServer server) {
        try {
            Path iconPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("icon.png");
            if (!Files.exists(iconPath)) return "";
            byte[] bytes = Files.readAllBytes(iconPath);
            if (bytes.length > MAX_WORLD_ICON_BYTES) {
                ICraftConstants.LOGGER.warn(
                        "[iCraft] icon.png del mundo es muy grande ({} bytes) — no se enviará",
                        bytes.length);
                return "";
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudo leer icon.png del mundo: {}", e.getMessage());
            return "";
        }
    }

    private static void sendDMHistory(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        PhoneData data = playerData.get(name);
        if (data == null) return;

        for (PhoneData.ChatConversation conv : data.conversations) {
            if (conv.isGroup) continue;
            int start = Math.max(0, conv.messages.size() - 50);
            for (int i = start; i < conv.messages.size(); i++) {
                PhoneData.ChatMessage m = conv.messages.get(i);
                ChatMessagePacket pkt = new ChatMessagePacket(
                        conv.id, m.sender, m.content, m.timestamp, false, m.id);
                NetworkManager.sendToPlayer(player, pkt);
            }
        }
    }

    private static void sendGlobalGroupHistory(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        PhoneData data = playerData.get(name);
        if (data == null) return;

        data.conversations.stream()
                .filter(c -> c.id.equals(GLOBAL_GROUP_ID))
                .findFirst()
                .ifPresent(conv -> {

                    int start = Math.max(0, conv.messages.size() - 50);
                    for (int i = start; i < conv.messages.size(); i++) {
                        PhoneData.ChatMessage m = conv.messages.get(i);
                        ChatMessagePacket pkt = new ChatMessagePacket(
                                GLOBAL_GROUP_ID, m.sender, m.content, m.timestamp, true, m.id);
                        NetworkManager.sendToPlayer(player, pkt);
                    }
                });
    }

    public static Path getSharedPhotosDir(MinecraftServer server) {
        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("iCraft").resolve("shared_photos");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudo crear shared_photos/: {}", e.getMessage());
        }
        return dir;
    }

    public static void handlePhotoUpload(ServerPlayer sender, String filename,
                                         String base64Png, String convId,
                                         boolean isGroup, String recipientName) {

        if (filename == null || base64Png == null || convId == null) return;
        if (!filename.endsWith(".png") || filename.contains("/")
                || filename.contains("\\") || filename.contains("..")) {
            ICraftConstants.LOGGER.warn("[iCraft] handlePhotoUpload: nombre inválido \"{}\" de {}",
                    filename, sender.getGameProfile().getName());
            return;
        }

        if (GLOBAL_GROUP_ID.equals(convId) && !isGlobalImagesEnabled(sender.getServer())) return;

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Png);
        } catch (IllegalArgumentException e) {
            ICraftConstants.LOGGER.warn("[iCraft] handlePhotoUpload: Base64 inválido de {}: {}",
                    sender.getGameProfile().getName(), e.getMessage());
            return;
        }
        if (bytes.length > MAX_ADMIN_PHOTO_BYTES) {
            ICraftConstants.LOGGER.warn("[iCraft] handlePhotoUpload: foto de {} supera {} KB — ignorada",
                    sender.getGameProfile().getName(), MAX_ADMIN_PHOTO_BYTES / 1024);
            return;
        }

        try {
            Path dest = getSharedPhotosDir(sender.getServer()).resolve(filename);
            Files.write(dest, bytes);
        } catch (IOException e) {
            ICraftConstants.LOGGER.warn("[iCraft] handlePhotoUpload: error guardando \"{}\": {}", filename, e.getMessage());
            return;
        }

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

        AdminPhotoPacket packet = new AdminPhotoPacket(filename, base64Png);
        for (ServerPlayer target : targets) {
            NetworkManager.sendToPlayer(target, packet);
        }
        ICraftConstants.LOGGER.info("[iCraft] Foto \"{}\" de {} enviada a {} jugador(es) ({} KB)",
                filename, sender.getGameProfile().getName(), targets.size(), bytes.length / 1024);
    }

    public static Path getWorldPhotosDir(MinecraftServer server) {
        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("iCraft").resolve("world_photos");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudo crear world_photos/: {}", e.getMessage());
        }
        return dir;
    }

    public static void handlePrintPhoto(ServerPlayer player, String filename, String base64Png) {
        if (filename == null || base64Png == null) return;
        if (!filename.endsWith(".png") || filename.contains("/")
                || filename.contains("\\") || filename.contains("..")) {
            ICraftConstants.LOGGER.warn("[iCraft] handlePrintPhoto: nombre inválido \"{}\" de {}",
                    filename, player.getGameProfile().getName());
            return;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Png);
        } catch (IllegalArgumentException e) {
            ICraftConstants.LOGGER.warn("[iCraft] handlePrintPhoto: Base64 inválido de {}: {}",
                    player.getGameProfile().getName(), e.getMessage());
            return;
        }
        if (bytes.length > MAX_ADMIN_PHOTO_BYTES) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "icraft.photo.too_big_msg", (MAX_ADMIN_PHOTO_BYTES / 1024)),
                    true);
            return;
        }

        try {
            Path dest = getWorldPhotosDir(player.getServer()).resolve(filename);
            Files.write(dest, bytes);
        } catch (IOException e) {
            ICraftConstants.LOGGER.warn("[iCraft] handlePrintPhoto: error guardando \"{}\": {}", filename, e.getMessage());
            return;
        }

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
                net.minecraft.network.chat.Component.translatable("icraft.photo.need_paper_msg"), true);
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
                net.minecraft.network.chat.Component.translatable("icraft.photo.printed_msg"), true);

        AdminPhotoPacket packet = new AdminPhotoPacket(filename, base64Png);
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(online, packet);
        }

        ICraftConstants.LOGGER.info("[iCraft] {} imprimió \"{}\" ({} KB) — distribuida a {} jugador(es)",
                player.getGameProfile().getName(), filename, bytes.length / 1024,
                player.getServer().getPlayerList().getPlayerCount());
    }

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
                    NetworkManager.sendToPlayer(player, new AdminPhotoPacket(filename, base64));
                } catch (IOException e) {
                    ICraftConstants.LOGGER.warn("[iCraft] handleRequestPhoto: error leyendo \"{}\": {}",
                            filename, e.getMessage());
                }
                return;
            }
        }

    }

    public static final long MAX_ADMIN_PHOTO_BYTES = 512 * 1024L;

    public static Path getAdminPhotosDir(MinecraftServer server) {

        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("iCraft").resolve("admin_photos");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ICraftConstants.LOGGER.warn("[iCraft] No se pudo crear admin_photos/: {}", e.getMessage());
        }
        return dir;
    }

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

    public static void broadcastAdminPhoto(MinecraftServer server, String filename) {
        Path photoPath = getAdminPhotosDir(server).resolve(filename);

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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, packet);
        }

        ICraftConstants.LOGGER.info("[iCraft] Admin photo \"{}\" enviada a {} jugadores ({} KB)",
                filename, server.getPlayerList().getPlayerCount(), bytes.length / 1024);
    }

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
                ICraftConstants.LOGGER.info("New marketplace listing by {}: {}", player.getGameProfile().getName(), packet.itemName());
            }
            case "buy" -> {
                globalMarket.stream()
                        .filter(l -> l.id.equals(packet.listingId()) && l.active)
                        .findFirst().ifPresent(l -> {
                            l.active = false;
                            ICraftConstants.LOGGER.info("{} bought {} from {}", player.getGameProfile().getName(), l.itemName, l.seller);
                        });
            }
            case "remove" -> {
                globalMarket.removeIf(l -> l.id.equals(packet.listingId())
                        && l.seller.equals(player.getGameProfile().getName()));
            }
        }
    }
}
