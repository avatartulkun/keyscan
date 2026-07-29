package com.secureqr.scanner.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import com.secureqr.scanner.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NetworkAccessController {
    public static final String PREFS = "secureqr_settings";
    public static final String KEY_MANUAL = "network_access_manual_allowlist";
    public static final String KEY_LAN_ENABLED = "network_access_lan_enabled";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_WEBDAV = "AUTO_WEBDAV";
    private static final List<Endpoint> runtimeWebDavEndpoints = new ArrayList<>();
    private static LanRule lanRule;
    private static LanRule backupLanRule;

    private NetworkAccessController() {
    }

    public static void requireAllowed(Context context, String targetUrl, String purpose) throws NetworkBlockedException {
        Decision decision = evaluate(context, targetUrl);
        if (!decision.allowed) throw new NetworkBlockedException(decision.message);
    }

    public static Decision evaluate(Context context, String targetUrl) {
        Endpoint target = normalize(targetUrl);
        if (target == null || target.host.isEmpty() || target.port <= 0) {
            return Decision.blocked("BLOCKED_INVALID_TARGET");
        }
        for (Endpoint webdav : autoWebDavEndpoints(context)) {
            if (webdav.matches(target)) return Decision.allowed("ALLOWED_WEBDAV");
        }
        for (Endpoint endpoint : manualEndpoints(context)) {
            if (endpoint.enabled && endpoint.matches(target)) return Decision.allowed("ALLOWED_MANUAL");
        }
        if (isLanAddress(target.host)) {
            if (!isLanEnabled(context)) return Decision.blocked("BLOCKED_LAN_DISABLED");
            if (isCurrentWifiSubnetTarget(context, target.host)) return Decision.allowed("ALLOWED_LAN_SUBNET");
            return Decision.blocked("BLOCKED_NOT_SAME_SUBNET");
        }
        return Decision.blocked("BLOCKED_NOT_IN_ALLOWLIST");
    }

    public static Endpoint normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim();
        if (value.contains("@")) return null;
        try {
            URI uri = URI.create(value.contains("://") ? value : "https://" + value);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.US);
            String host = uri.getHost();
            if ((host == null || host.isEmpty()) && value.startsWith("[")) {
                int end = value.indexOf(']');
                if (end > 0) host = value.substring(1, end);
            }
            if (host == null || host.trim().isEmpty() || host.contains("*")) return null;
            int port = uri.getPort();
            if (port <= 0) port = "http".equals(scheme) ? 80 : "https".equals(scheme) ? 443 : -1;
            if (port <= 0 || port > 65535) return null;
            return new Endpoint(UUID.randomUUID().toString(), scheme, host.toLowerCase(Locale.US), port, "", SOURCE_MANUAL, true, System.currentTimeMillis(), System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    public static Endpoint autoWebDavEndpoint(Context context) {
        List<Endpoint> endpoints = autoWebDavEndpoints(context);
        return endpoints.isEmpty() ? null : endpoints.get(0);
    }

    public static List<Endpoint> autoWebDavEndpoints(Context context) {
        List<Endpoint> endpoints = new ArrayList<>();
        SharedPreferences prefs = prefs(context);
        addWebDavEndpoint(endpoints, prefs.getString("url", ""));
        addWebDavEndpoint(endpoints, prefs.getString("backup_url", ""));
        synchronized (runtimeWebDavEndpoints) {
            for (Endpoint endpoint : runtimeWebDavEndpoints) addUniqueEndpoint(endpoints, endpoint.copy());
        }
        return endpoints;
    }

    public static void rememberRuntimeWebDavEndpoint(String raw) {
        Endpoint endpoint = normalize(raw);
        if (endpoint == null) return;
        endpoint.source = SOURCE_WEBDAV;
        endpoint.displayName = endpoint.display();
        synchronized (runtimeWebDavEndpoints) {
            addUniqueEndpoint(runtimeWebDavEndpoints, endpoint);
        }
    }

    private static void addWebDavEndpoint(List<Endpoint> endpoints, String raw) {
        Endpoint endpoint = normalize(raw);
        if (endpoint == null) return;
        endpoint.source = SOURCE_WEBDAV;
        endpoint.displayName = endpoint.display();
        addUniqueEndpoint(endpoints, endpoint);
    }

    private static void addUniqueEndpoint(List<Endpoint> endpoints, Endpoint endpoint) {
        for (Endpoint existing : endpoints) {
            if (existing.matches(endpoint)) return;
        }
        endpoints.add(endpoint);
    }

    public static List<Endpoint> manualEndpoints(Context context) {
        List<Endpoint> endpoints = new ArrayList<>();
        String raw = prefs(context).getString(KEY_MANUAL, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                Endpoint endpoint = new Endpoint(
                        item.optString("endpointId", UUID.randomUUID().toString()),
                        item.optString("scheme", "https"),
                        item.optString("host", "").toLowerCase(Locale.US),
                        item.optInt("port", 443),
                        item.optString("displayName", ""),
                        SOURCE_MANUAL,
                        item.optBoolean("enabled", true),
                        item.optLong("createdAt", System.currentTimeMillis()),
                        item.optLong("updatedAt", System.currentTimeMillis())
                );
                if (!endpoint.host.isEmpty() && endpoint.port > 0) endpoints.add(endpoint);
            }
        } catch (Exception ignored) {
        }
        return endpoints;
    }

    public static void saveManualEndpoints(Context context, List<Endpoint> endpoints) {
        JSONArray array = new JSONArray();
        for (Endpoint endpoint : endpoints) {
            try {
                JSONObject item = new JSONObject();
                item.put("endpointId", endpoint.endpointId);
                item.put("scheme", endpoint.scheme);
                item.put("host", endpoint.host);
                item.put("port", endpoint.port);
                item.put("displayName", endpoint.displayName);
                item.put("source", SOURCE_MANUAL);
                item.put("enabled", endpoint.enabled);
                item.put("createdAt", endpoint.createdAt);
                item.put("updatedAt", System.currentTimeMillis());
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        prefs(context).edit().putString(KEY_MANUAL, array.toString()).apply();
    }

    public static boolean isLanEnabled(Context context) {
        synchronized (NetworkAccessController.class) {
            return (lanRule != null && isCurrentWifiSession(context, lanRule))
                    || (backupLanRule != null && isCurrentWifiSession(context, backupLanRule));
        }
    }

    public static void setLanEnabled(Context context, boolean enabled) {
        if (enabled) {
            LanInfo info = currentLanInfo(context);
            if (info != null) enableLanSession(info);
        } else {
            clearLanSession();
        }
    }

    public static String wifiSubnetSummary(Context context) {
        LinkAddress address = currentIpv4LinkAddress(context);
        if (address == null) return context.getString(R.string.network_wifi_not_connected);
        int ip = ipv4ToInt(address.getAddress().getHostAddress());
        int mask = address.getPrefixLength() == 0 ? 0 : (int) (0xffffffffL << (32 - address.getPrefixLength()));
        int network = ip & mask;
        return intToIpv4(network) + "/" + address.getPrefixLength();
    }

    private static boolean isCurrentWifiSubnetTarget(Context context, String host) {
        LanInfo info = activeLanInfo(context);
        if (info == null) info = activeBackupLanInfo(context);
        if (info == null) return false;
        try {
            int local = ipv4ToInt(info.ipv4);
            int target = ipv4ToInt(host);
            int mask = info.prefixLength == 0 ? 0 : (int) (0xffffffffL << (32 - info.prefixLength));
            return (local & mask) == (target & mask);
        } catch (Exception e) {
            return false;
        }
    }

    public static LanInfo currentLanInfo(Context context) {
        LinkAddress address = currentIpv4LinkAddress(context);
        if (address == null) return null;
        Network matchedNetwork = null;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            for (Network network : manager.getAllNetworks()) {
                LinkProperties properties = manager.getLinkProperties(network);
                if (properties != null && properties.getLinkAddresses().contains(address)) {
                    matchedNetwork = network;
                    break;
                }
            }
        }
        int ip = ipv4ToInt(address.getAddress().getHostAddress());
        int prefix = address.getPrefixLength();
        int mask = prefix == 0 ? 0 : (int) (0xffffffffL << (32 - prefix));
        return new LanInfo(matchedNetwork, address.getAddress().getHostAddress(), prefix, intToIpv4(ip & mask) + "/" + prefix);
    }

    public static void enableLanSession(LanInfo info) {
        if (info == null) return;
        synchronized (NetworkAccessController.class) {
            lanRule = new LanRule(info.ipv4, info.prefixLength, info.subnet);
        }
    }

    public static void clearLanSession() {
        synchronized (NetworkAccessController.class) {
            lanRule = null;
        }
    }

    public static void enableBackupLanSession(LanInfo info) {
        if (info == null) return;
        synchronized (NetworkAccessController.class) {
            backupLanRule = new LanRule(info.ipv4, info.prefixLength, info.subnet);
        }
    }

    public static void clearBackupLanSession() {
        synchronized (NetworkAccessController.class) {
            backupLanRule = null;
        }
    }

    public static LanInfo activeBackupLanInfo(Context context) {
        synchronized (NetworkAccessController.class) {
            if (backupLanRule == null || !isCurrentWifiSession(context, backupLanRule)) return null;
            return new LanInfo(null, backupLanRule.ipv4, backupLanRule.prefixLength, backupLanRule.subnet);
        }
    }

    public static LanInfo activeLanInfo(Context context) {
        synchronized (NetworkAccessController.class) {
            if (lanRule == null || !isCurrentWifiSession(context, lanRule)) return null;
            return new LanInfo(null, lanRule.ipv4, lanRule.prefixLength, lanRule.subnet);
        }
    }

    private static boolean isCurrentWifiSession(Context context, LanRule rule) {
        LanInfo current = currentLanInfo(context);
        return current != null
                && current.ipv4.equals(rule.ipv4)
                && current.prefixLength == rule.prefixLength
                && current.subnet.equals(rule.subnet);
    }

    private static LinkAddress currentIpv4LinkAddress(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return null;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
            LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) continue;
            for (LinkAddress address : properties.getLinkAddresses()) {
                InetAddress inet = address.getAddress();
                if (inet instanceof Inet4Address
                        && !inet.isLoopbackAddress()
                        && !inet.isLinkLocalAddress()
                        && !inet.isAnyLocalAddress()
                        && !inet.isMulticastAddress()) {
                    return address;
                }
            }
        }
        return null;
    }

    private static boolean isLanAddress(String host) {
        try {
            int ip = ipv4ToInt(host);
            return (ip & 0xff000000) == 0x0a000000
                    || (ip & 0xfff00000) == 0xac100000
                    || (ip & 0xffff0000) == 0xc0a80000
                    || (ip & 0xff000000) == 0x7f000000;
        } catch (Exception e) {
            return false;
        }
    }

    private static int ipv4ToInt(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4");
        int result = 0;
        for (String part : parts) {
            int number = Integer.parseInt(part);
            if (number < 0 || number > 255) throw new IllegalArgumentException("Invalid IPv4");
            result = (result << 8) | number;
        }
        return result;
    }

    private static String intToIpv4(int value) {
        return ((value >>> 24) & 0xff) + "." + ((value >>> 16) & 0xff) + "." + ((value >>> 8) & 0xff) + "." + (value & 0xff);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Decision {
        public final boolean allowed;
        public final String message;

        private Decision(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        static Decision allowed(String message) {
            return new Decision(true, message);
        }

        static Decision blocked(String message) {
            return new Decision(false, message);
        }
    }

    public static class Endpoint {
        public String endpointId;
        public String scheme;
        public String host;
        public int port;
        public String displayName;
        public String source;
        public boolean enabled;
        public long createdAt;
        public long updatedAt;

        public Endpoint(String endpointId, String scheme, String host, int port, String displayName, String source, boolean enabled, long createdAt, long updatedAt) {
            this.endpointId = endpointId;
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.displayName = displayName == null || displayName.isEmpty() ? scheme + "://" + host + ":" + port : displayName;
            this.source = source;
            this.enabled = enabled;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        boolean matches(Endpoint other) {
            return other != null
                    && scheme.equalsIgnoreCase(other.scheme)
                    && host.equalsIgnoreCase(other.host)
                    && port == other.port;
        }

        public String display() {
            return scheme + "://" + host + ":" + port;
        }

        Endpoint copy() {
            return new Endpoint(endpointId, scheme, host, port, displayName, source, enabled, createdAt, updatedAt);
        }
    }

    public static final class LanInfo {
        public final Network network;
        public final String ipv4;
        public final int prefixLength;
        public final String subnet;

        LanInfo(Network network, String ipv4, int prefixLength, String subnet) {
            this.network = network;
            this.ipv4 = ipv4;
            this.prefixLength = prefixLength;
            this.subnet = subnet;
        }
    }

    private static final class LanRule {
        final String ipv4;
        final int prefixLength;
        final String subnet;

        LanRule(String ipv4, int prefixLength, String subnet) {
            this.ipv4 = ipv4;
            this.prefixLength = prefixLength;
            this.subnet = subnet;
        }
    }
}
