package com.secureqr.scanner.lan;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.LanFileTransferServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class NearbyLanShareManager {
    public interface Listener {
        void onStateChanged();

        void onError(String message);
    }

    public static final class Peer {
        public final String peerId;
        public final String deviceId;
        public final String displayName;
        public final String host;
        public final int port;
        public final String inviteUrl;
        public final String token;
        public final long lastSeenAt;
        public final boolean self;
        public final String state;
        public final String stateMessage;
        public final long stateUpdatedAt;

        Peer(String peerId, String deviceId, String displayName, String host, int port, String inviteUrl, String token, long lastSeenAt, boolean self, String state, String stateMessage, long stateUpdatedAt) {
            this.peerId = peerId;
            this.deviceId = deviceId;
            this.displayName = displayName;
            this.host = host;
            this.port = port;
            this.inviteUrl = inviteUrl;
            this.token = token;
            this.lastSeenAt = lastSeenAt;
            this.self = self;
            this.state = state;
            this.stateMessage = stateMessage;
            this.stateUpdatedAt = stateUpdatedAt;
        }
    }

    public static final class IncomingInvite {
        public final String inviteId;
        public final String senderDeviceId;
        public final String senderDisplayName;
        public final String senderHost;
        public final int senderPort;
        public final String senderTransferUrl;
        public final String callbackUrl;
        public final String callbackToken;
        public final int fileCount;
        public final long totalSize;
        public final long sentAt;

        IncomingInvite(NearbyLanRelayServer.Invite invite) {
            this.inviteId = invite.inviteId;
            this.senderDeviceId = invite.senderDeviceId;
            this.senderDisplayName = invite.senderDisplayName;
            this.senderHost = invite.senderHost;
            this.senderPort = invite.senderPort;
            this.senderTransferUrl = invite.senderTransferUrl;
            this.callbackUrl = invite.callbackUrl;
            this.callbackToken = invite.callbackToken;
            this.fileCount = invite.fileCount;
            this.totalSize = invite.totalSize;
            this.sentAt = invite.sentAt;
        }
    }

    private static final String NSD_TYPE = "_keyscan._tcp.";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final NearbyLanShareManager INSTANCE = new NearbyLanShareManager();
    private static final long PEER_TTL_MS = 15000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final OkHttpClient httpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
    private final Map<String, PeerRecord> peers = new LinkedHashMap<>();
    private final Map<String, IncomingInvite> incomingInvites = new LinkedHashMap<>();
    private final Map<String, String> inviteToPeerId = new LinkedHashMap<>();

    private Context appContext;
    private Listener listener;
    private NearbyLanIdentityStore.Identity identity;
    private NearbyLanRelayServer relayServer;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private WifiManager.MulticastLock multicastLock;
    private boolean started;
    private boolean discoveryRunning;

    private NearbyLanShareManager() {
    }

    public static NearbyLanShareManager getInstance(Context context) {
        INSTANCE.attachContext(context);
        return INSTANCE;
    }

    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized NearbyLanIdentityStore.Identity getIdentity() {
        if (appContext == null) return null;
        if (identity == null) identity = NearbyLanIdentityStore.get(appContext);
        return identity;
    }

    public synchronized boolean isRunning() {
        return started;
    }

    public synchronized boolean start() {
        if (started) {
            notifyChanged();
            return true;
        }
        if (appContext == null) return false;
        try {
            identity = NearbyLanIdentityStore.get(appContext);
            relayServer = new NearbyLanRelayServer(appContext, identity.deviceId, identity.displayName, new NearbyLanRelayServer.Listener() {
                @Override
                public void onInviteReceived(NearbyLanRelayServer.Invite invite) {
                    mainHandler.post(() -> {
                        synchronized (NearbyLanShareManager.this) {
                            incomingInvites.put(invite.inviteId, new IncomingInvite(invite));
                        }
                        notifyChanged();
                    });
                }

                @Override
                public void onStatusUpdate(NearbyLanRelayServer.StatusUpdate statusUpdate) {
                    mainHandler.post(() -> {
                        synchronized (NearbyLanShareManager.this) {
                            String peerId = inviteToPeerId.get(statusUpdate.inviteId);
                            if (peerId != null) {
                                PeerRecord record = peers.get(peerId);
                                if (record != null) {
                                    peers.put(peerId, record.withState(statusUpdate.state, statusUpdate.message, statusUpdate.updatedAt));
                                }
                            }
                        }
                        notifyChanged();
                    });
                }

                @Override
                public void onError(Exception error) {
                    mainHandler.post(() -> notifyError(error == null ? appContext.getString(R.string.nearby_invite_service_failed) : error.getMessage()));
                }
            });
            relayServer.start();
            nsdManager = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
            acquireMulticastLock();
            registerService();
            startDiscovery();
            started = true;
            notifyChanged();
            return true;
        } catch (Exception error) {
            stop();
            notifyError(error == null ? appContext.getString(R.string.nearby_service_start_failed) : error.getMessage());
            return false;
        }
    }

    public synchronized void stop() {
        stopDiscovery();
        unregisterService();
        releaseMulticastLock();
        if (relayServer != null) {
            relayServer.stop();
            relayServer = null;
        }
        started = false;
    }

    public synchronized void renameDisplayName(String displayName) {
        if (appContext == null) return;
        identity = NearbyLanIdentityStore.rename(appContext, displayName);
        if (started) {
            unregisterService();
            registerService();
        }
        notifyChanged();
    }

    public synchronized List<Peer> getPeers() {
        pruneStalePeers();
        List<Peer> result = new ArrayList<>();
        for (PeerRecord record : peers.values()) {
            result.add(record.toPeer());
        }
        Collections.sort(result, Comparator.comparing((Peer peer) -> !peer.self).thenComparing(peer -> peer.displayName == null ? "" : peer.displayName));
        return result;
    }

    public synchronized List<IncomingInvite> getIncomingInvites() {
        return new ArrayList<>(incomingInvites.values());
    }

    public synchronized String getRelayBaseUrl() {
        return relayServer == null ? "" : relayServer.getBaseUrl();
    }

    public synchronized String getRelayToken() {
        return relayServer == null ? "" : relayServer.getToken();
    }

    public synchronized void sendInvite(Peer peer, LanFileTransferServer server) {
        if (peer == null || server == null) return;
        if (peer.self) {
            notifyError(appContext.getString(R.string.nearby_cannot_send_to_self));
            return;
        }
        if (peer.inviteUrl == null || peer.inviteUrl.isEmpty()) {
            notifyError(appContext.getString(R.string.nearby_invalid_address));
            return;
        }
        NearbyLanIdentityStore.Identity currentIdentity = getIdentity();
        if (currentIdentity == null) {
            notifyError(appContext.getString(R.string.nearby_identity_not_initialized));
            return;
        }
        if (relayServer == null) {
            notifyError(appContext.getString(R.string.nearby_invite_service_not_started));
            return;
        }
        final String inviteId = UUID.randomUUID().toString();
        final PeerRecord targetRecord = peerToRecord(peer);
        synchronized (this) {
            peers.put(peer.peerId, targetRecord.withState("sending", appContext.getString(R.string.nearby_sending_invite), System.currentTimeMillis()));
            inviteToPeerId.put(inviteId, peer.peerId);
            notifyChanged();
        }
        ioExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("inviteId", inviteId);
                payload.put("senderDeviceId", currentIdentity.deviceId);
                payload.put("senderDisplayName", currentIdentity.displayName);
                payload.put("senderHost", extractHost(server.getBrowserEntryUrl()));
                payload.put("senderPort", extractPort(server.getBrowserEntryUrl()));
                payload.put("senderTransferUrl", server.getBrowserEntryUrl());
                payload.put("callbackUrl", getRelayBaseUrl() + "/keyscan/nearby/status");
                payload.put("callbackToken", getRelayToken());
                payload.put("fileCount", server.getFileCount());
                payload.put("totalSize", server.getTotalSize());
                payload.put("sentAt", System.currentTimeMillis());
                JSONArray files = new JSONArray();
                for (LanFileTransferServer.FileItem item : server.getFiles()) {
                    JSONObject file = new JSONObject();
                    file.put("name", item.name);
                    file.put("mimeType", item.mimeType);
                    file.put("size", item.size);
                    file.put("risky", item.risky);
                    files.put(file);
                }
                payload.put("files", files);

                Request request = new Request.Builder()
                        .url(peer.inviteUrl)
                        .header("X-KeyScan-Token", peer.token == null ? "" : peer.token)
                        .post(RequestBody.create(payload.toString(), JSON))
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code());
                    }
                }
                synchronized (NearbyLanShareManager.this) {
                    PeerRecord record = peers.get(peer.peerId);
                    if (record != null) {
                        peers.put(peer.peerId, record.withState("sent", appContext.getString(R.string.nearby_sent_waiting_confirmation), System.currentTimeMillis()));
                    }
                }
                notifyChanged();
            } catch (Exception error) {
                synchronized (NearbyLanShareManager.this) {
                    PeerRecord record = peers.get(peer.peerId);
                    if (record != null) {
                        peers.put(peer.peerId, record.withState("failed", error.getMessage() == null ? appContext.getString(R.string.nearby_send_failed) : error.getMessage(), System.currentTimeMillis()));
                    }
                }
                notifyChanged();
                notifyError(error == null ? appContext.getString(R.string.nearby_invite_send_failed) : error.getMessage());
            }
        });
    }

    private PeerRecord peerToRecord(Peer peer) {
        return new PeerRecord(peer.peerId, peer.deviceId, peer.displayName, peer.host, peer.port, peer.inviteUrl, peer.token, peer.lastSeenAt, peer.self, peer.state, peer.stateMessage, peer.stateUpdatedAt);
    }

    public synchronized void acceptInvite(IncomingInvite invite) {
        if (invite == null) return;
        incomingInvites.remove(invite.inviteId);
        notifyChanged();
        sendStatus(invite.callbackUrl, invite.callbackToken, invite.inviteId, "accepted", appContext.getString(R.string.nearby_accepted));
    }

    public synchronized void declineInvite(IncomingInvite invite) {
        if (invite == null) return;
        incomingInvites.remove(invite.inviteId);
        notifyChanged();
        sendStatus(invite.callbackUrl, invite.callbackToken, invite.inviteId, "declined", appContext.getString(R.string.nearby_declined));
    }

    public synchronized void clearIncomingInvite(String inviteId) {
        if (inviteId == null || inviteId.isEmpty()) return;
        incomingInvites.remove(inviteId);
        notifyChanged();
    }

    private void attachContext(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    private void registerService() {
        if (nsdManager == null || relayServer == null || identity == null) return;
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceType(NSD_TYPE);
        serviceInfo.setServiceName(identity.displayName);
        serviceInfo.setPort(relayServer.getPort());
        serviceInfo.setAttribute("deviceId", identity.deviceId);
        serviceInfo.setAttribute("displayName", identity.displayName);
        serviceInfo.setAttribute("token", relayServer.getToken());
        serviceInfo.setAttribute("invitePort", String.valueOf(relayServer.getPort()));
        serviceInfo.setAttribute("baseUrl", relayServer.getBaseUrl());
        serviceInfo.setAttribute("relayUrl", relayServer.getInviteUrl());
        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                notifyError(appContext.getString(R.string.nearby_broadcast_failed_code, errorCode));
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                notifyChanged();
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }
        };
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Exception error) {
            notifyError(error == null ? appContext.getString(R.string.nearby_broadcast_failed) : error.getMessage());
        }
    }

    private void unregisterService() {
        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception ignored) {
            }
        }
        registrationListener = null;
    }

    private void startDiscovery() {
        if (nsdManager == null || discoveryRunning) return;
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                notifyError(appContext.getString(R.string.nearby_discovery_failed_code, errorCode));
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                notifyChanged();
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (serviceInfo == null || !NSD_TYPE.equals(serviceInfo.getServiceType())) return;
                resolveService(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                if (serviceInfo == null) return;
                String peerId = servicePeerId(serviceInfo);
                synchronized (NearbyLanShareManager.this) {
                    peers.remove(peerId);
                }
                notifyChanged();
            }
        };
        try {
            nsdManager.discoverServices(NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
            discoveryRunning = true;
        } catch (Exception error) {
            notifyError(error == null ? appContext.getString(R.string.nearby_discovery_failed) : error.getMessage());
        }
    }

    private void stopDiscovery() {
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception ignored) {
            }
        }
        discoveryListener = null;
        discoveryRunning = false;
        synchronized (this) {
            peers.clear();
            inviteToPeerId.clear();
            incomingInvites.clear();
        }
    }

    private void pruneStalePeers() {
        long now = System.currentTimeMillis();
        peers.entrySet().removeIf(entry -> !entry.getValue().self && now - entry.getValue().lastSeenAt > PEER_TTL_MS);
    }

    private void resolveService(NsdServiceInfo serviceInfo) {
        if (nsdManager == null) return;
        NsdManager.ResolveListener resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onServiceResolved(NsdServiceInfo resolved) {
                String deviceId = attr(resolved, "deviceId");
                if (deviceId.isEmpty()) {
                    deviceId = resolved.getServiceName() == null ? "" : resolved.getServiceName();
                }
                NearbyLanIdentityStore.Identity selfIdentity = getIdentity();
                if (selfIdentity != null && selfIdentity.deviceId.equals(deviceId)) {
                    return;
                }
                String displayName = attr(resolved, "displayName");
                if (displayName.isEmpty()) {
                    displayName = resolved.getServiceName() == null ? "KeyScan" : resolved.getServiceName();
                }
                String host = resolved.getHost() == null ? "" : resolved.getHost().getHostAddress();
                int port = resolved.getPort();
                String token = attr(resolved, "token");
                String url = attr(resolved, "relayUrl");
                if (url.isEmpty()) {
                    url = "http://" + host + ":" + port + "/keyscan/nearby/invite";
                }
                String peerId = deviceId.isEmpty() ? host + ":" + port : deviceId;
                PeerRecord record = new PeerRecord(peerId, deviceId, displayName, host, port, url, token, System.currentTimeMillis(), false, "", "", 0L);
                synchronized (NearbyLanShareManager.this) {
                    peers.put(peerId, record);
                }
                notifyChanged();
            }
        };
        try {
            nsdManager.resolveService(serviceInfo, resolveListener);
        } catch (Exception ignored) {
        }
    }

    private void sendStatus(String callbackUrl, String callbackToken, String inviteId, String state, String message) {
        if (callbackUrl == null || callbackUrl.isEmpty()) return;
        ioExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("inviteId", inviteId);
                payload.put("state", state);
                payload.put("message", message == null ? "" : message);
                payload.put("updatedAt", System.currentTimeMillis());
                payload.put("peerDeviceId", getIdentity() == null ? "" : getIdentity().deviceId);
                payload.put("peerDisplayName", getIdentity() == null ? "" : getIdentity().displayName);
                Request request = new Request.Builder()
                        .url(callbackUrl)
                        .header("X-KeyScan-Token", callbackToken == null ? "" : callbackToken)
                        .post(RequestBody.create(payload.toString(), JSON))
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code());
                    }
                }
            } catch (Exception error) {
                notifyError(error == null ? appContext.getString(R.string.nearby_status_callback_failed) : error.getMessage());
            }
        });
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifiManager = (WifiManager) appContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return;
            multicastLock = wifiManager.createMulticastLock("KeyScanNearbyLan");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignored) {
        }
        multicastLock = null;
    }

    private void notifyChanged() {
        Listener current = listener;
        if (current != null) {
            mainHandler.post(current::onStateChanged);
        }
    }

    private void notifyError(String message) {
        Listener current = listener;
        if (current != null) {
            mainHandler.post(() -> current.onError(message == null || message.trim().isEmpty() ? appContext.getString(R.string.nearby_device_error) : message));
        }
    }

    private String attr(NsdServiceInfo info, String key) {
        try {
            Map<String, byte[]> attributes = info.getAttributes();
            if (attributes == null) return "";
            byte[] value = attributes.get(key);
            return value == null ? "" : new String(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "";
        }
    }

    private String servicePeerId(NsdServiceInfo info) {
        String deviceId = attr(info, "deviceId");
        if (!deviceId.isEmpty()) return deviceId;
        String host = info.getHost() == null ? "" : info.getHost().getHostAddress();
        return host + ":" + info.getPort();
    }

    private String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception error) {
            return "";
        }
    }

    private int extractPort(String url) {
        try {
            URI uri = URI.create(url);
            return Math.max(0, uri.getPort());
        } catch (Exception error) {
            return 0;
        }
    }

    private static final class PeerRecord {
        final String peerId;
        final String deviceId;
        final String displayName;
        final String host;
        final int port;
        final String inviteUrl;
        final String token;
        final long lastSeenAt;
        final boolean self;
        final String state;
        final String stateMessage;
        final long stateUpdatedAt;

        PeerRecord(String peerId, String deviceId, String displayName, String host, int port, String inviteUrl, String token, long lastSeenAt, boolean self, String state, String stateMessage, long stateUpdatedAt) {
            this.peerId = peerId;
            this.deviceId = deviceId;
            this.displayName = displayName;
            this.host = host;
            this.port = port;
            this.inviteUrl = inviteUrl;
            this.token = token;
            this.lastSeenAt = lastSeenAt;
            this.self = self;
            this.state = state;
            this.stateMessage = stateMessage;
            this.stateUpdatedAt = stateUpdatedAt;
        }

        Peer toPeer() {
            return new Peer(peerId, deviceId, displayName, host, port, inviteUrl, token, lastSeenAt, self, state, stateMessage, stateUpdatedAt);
        }

        PeerRecord withState(String state, String message, long updatedAt) {
            return new PeerRecord(peerId, deviceId, displayName, host, port, inviteUrl, token, lastSeenAt, self, state, message, updatedAt);
        }
    }
}
