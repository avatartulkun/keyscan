package com.secureqr.scanner.autofill;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AutofillLinkStore {
    private static final String PREFS = "keyscan_autofill_links";
    private static final String ENTRY_PREFIX = "entry_";

    private AutofillLinkStore() {}

    public static boolean isAppLinked(Context context, long entryId, String packageName) {
        String target = AutofillCredentialMatcher.normalizePackage(packageName);
        if (target.isEmpty()) return false;
        JSONArray apps = links(context, entryId).optJSONArray("linkedApps");
        if (apps == null) return false;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app != null && target.equals(AutofillCredentialMatcher.normalizePackage(app.optString("packageName")))) {
                return true;
            }
        }
        return false;
    }

    public static void linkApp(Context context, long entryId, String packageName, String appName) {
        String target = AutofillCredentialMatcher.normalizePackage(packageName);
        if (context == null || entryId <= 0 || target.isEmpty()) return;
        JSONObject root = links(context, entryId);
        JSONArray apps = root.optJSONArray("linkedApps");
        if (apps == null) apps = new JSONArray();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app != null && target.equals(AutofillCredentialMatcher.normalizePackage(app.optString("packageName")))) {
                save(context, entryId, root);
                return;
            }
        }
        JSONObject app = new JSONObject();
        try {
            app.put("packageName", target);
            app.put("appName", appName == null ? "" : appName);
            apps.put(app);
            root.put("linkedApps", apps);
            save(context, entryId, root);
        } catch (Exception ignored) {}
    }

    public static JSONObject links(Context context, long entryId) {
        String raw = prefs(context).getString(ENTRY_PREFIX + entryId, "{}");
        try {
            return new JSONObject(raw == null ? "{}" : raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void save(Context context, long entryId, JSONObject root) {
        prefs(context).edit().putString(ENTRY_PREFIX + entryId, root.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
