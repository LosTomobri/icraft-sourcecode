package com.icraft.voicechat;

import com.icraft.ICraftConstants;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ICraftVoicechatPlugin implements VoicechatPlugin {

    private static volatile VoicechatServerApi SERVER_API;

    private static final Map<UUID, UUID> activeCallPeer = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return ICraftConstants.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {

    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        SERVER_API = event.getVoicechat();
        ICraftConstants.LOGGER.info("[iCraft] Simple Voice Chat detectado — llamadas privadas habilitadas.");
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        SERVER_API = null;
        activeCallPeer.clear();
    }

    public static boolean isAvailable() {
        return SERVER_API != null;
    }

    public enum CallResult {
        OK,
        VOICECHAT_NOT_INSTALLED,
        CALLER_NOT_CONNECTED,
        TARGET_NOT_CONNECTED
    }

    public static CallResult checkCallable(UUID callerId, UUID targetId) {
        VoicechatServerApi api = SERVER_API;
        if (api == null) {
            return CallResult.VOICECHAT_NOT_INSTALLED;
        }
        if (api.getConnectionOf(callerId) == null) {
            return CallResult.CALLER_NOT_CONNECTED;
        }
        if (api.getConnectionOf(targetId) == null) {
            return CallResult.TARGET_NOT_CONNECTED;
        }
        return CallResult.OK;
    }

    public static CallResult startCall(UUID callerId, UUID targetId) {
        CallResult check = checkCallable(callerId, targetId);
        if (check != CallResult.OK) {
            return check;
        }
        VoicechatServerApi api = SERVER_API;

        VoicechatConnection callerConn = api.getConnectionOf(callerId);
        VoicechatConnection targetConn = api.getConnectionOf(targetId);

        endCall(callerId);
        endCall(targetId);

        Group group = api.groupBuilder()
                .setName("iCraft Call")
                .setPersistent(false)
                .setHidden(true)
                .setType(Group.Type.ISOLATED)
                .build();

        callerConn.setGroup(group);
        targetConn.setGroup(group);

        activeCallPeer.put(callerId, targetId);
        activeCallPeer.put(targetId, callerId);

        return CallResult.OK;
    }

    public static void endCall(UUID playerId) {
        UUID peer = activeCallPeer.remove(playerId);
        if (peer != null) {
            activeCallPeer.remove(peer);
        }

        VoicechatServerApi api = SERVER_API;
        if (api == null) return;

        VoicechatConnection conn = api.getConnectionOf(playerId);
        if (conn != null && conn.getGroup() != null) {
            conn.setGroup(null);
        }
        if (peer != null) {
            VoicechatConnection peerConn = api.getConnectionOf(peer);
            if (peerConn != null && peerConn.getGroup() != null) {
                peerConn.setGroup(null);
            }
        }
    }

    public static UUID getCallPeer(UUID playerId) {
        return activeCallPeer.get(playerId);
    }
}
