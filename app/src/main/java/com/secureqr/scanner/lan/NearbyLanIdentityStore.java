package com.secureqr.scanner.lan;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.UUID;

public final class NearbyLanIdentityStore {
    private static final String PREFS = "keyscan_nearby_lan_identity";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final int DISPLAY_NAME_MAX = 24;

    private NearbyLanIdentityStore() {
    }

    public static Identity get(Context context) {
        SharedPreferences prefs = prefs(context);
        String deviceId = prefs.getString(KEY_DEVICE_ID, "");
        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
        String displayName = prefs.getString(KEY_DISPLAY_NAME, "");
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = defaultDisplayName(deviceId);
            prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply();
        }
        return new Identity(deviceId, displayName);
    }

    public static Identity rename(Context context, String displayName) {
        String sanitized = sanitizeDisplayName(displayName);
        SharedPreferences prefs = prefs(context);
        String deviceId = get(context).deviceId;
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).putString(KEY_DISPLAY_NAME, sanitized).apply();
        return new Identity(deviceId, sanitized);
    }

    public static String sanitizeDisplayName(String displayName) {
        String value = displayName == null ? "" : displayName.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            return defaultDisplayName(UUID.randomUUID().toString());
        }
        if (value.length() > DISPLAY_NAME_MAX) {
            value = value.substring(0, DISPLAY_NAME_MAX).trim();
        }
        return value;
    }

    private static String defaultDisplayName(String deviceId) {
        String suffix = deviceId == null ? UUID.randomUUID().toString().replace("-", "") : deviceId.replace("-", "");
        suffix = suffix.length() >= 6 ? suffix.substring(0, 6) : suffix;
        return String.format(Locale.US, "KeyScan-%s", suffix.toUpperCase(Locale.US));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Identity {
        public final String deviceId;
        public final String displayName;

        Identity(String deviceId, String displayName) {
            this.deviceId = deviceId;
            this.displayName = displayName;
        }
    }
}
