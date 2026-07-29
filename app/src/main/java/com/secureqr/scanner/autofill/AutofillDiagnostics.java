package com.secureqr.scanner.autofill;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import com.secureqr.scanner.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AutofillDiagnostics {
    private static final String PREFS = "keyscan_autofill_diagnostics";
    private static final String EVENTS = "events";
    private static final String ENABLED = "enabled";
    private static final int LIMIT = 30;

    private AutofillDiagnostics() {}

    public static void record(Context context, String packageName, String webDomain, String scenario,
                              int textFields, int passwordFields, int matches, int datasets,
                              String status, String reason) {
        if (context == null || !isEnabled(context)) return;
        try {
            JSONArray current = events(context);
            JSONArray next = new JSONArray();
            JSONObject event = new JSONObject();
            event.put("time", System.currentTimeMillis());
            event.put("packageName", safe(packageName));
            event.put("webDomain", safe(webDomain));
            event.put("scenario", safe(scenario));
            event.put("textFields", textFields);
            event.put("passwordFields", passwordFields);
            event.put("matches", matches);
            event.put("datasets", datasets);
            event.put("status", safe(status));
            event.put("reason", safe(reason));
            next.put(event);
            for (int i = 0; i < current.length() && next.length() < LIMIT; i++) next.put(current.get(i));
            prefs(context).edit().putString(EVENTS, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static JSONArray events(Context context) {
        String raw = prefs(context).getString(EVENTS, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static boolean isEnabled(Context context) {
        if (context == null) return false;
        return isDebuggable(context) || prefs(context).getBoolean(ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(context).edit().putBoolean(ENABLED, enabled).apply();
    }

    public static String format(Context context) {
        if (!isEnabled(context)) return context.getString(R.string.autofill_diagnostics_disabled);
        JSONArray events = events(context);
        if (events.length() == 0) return context.getString(R.string.autofill_diagnostics_empty);
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            out.append(format.format(new Date(e.optLong("time")))).append('\n');
            out.append("App: ").append(e.optString("packageName", "-")).append('\n');
            if (!e.optString("webDomain", "").isEmpty()) out.append("Domain: ").append(e.optString("webDomain")).append('\n');
            out.append(context.getString(R.string.autofill_diagnostics_scenario)).append(": ").append(e.optString("scenario", "-")).append('\n');
            out.append(context.getString(R.string.autofill_diagnostics_fields)).append(": text=").append(e.optInt("textFields")).append(", password=").append(e.optInt("passwordFields")).append('\n');
            out.append(context.getString(R.string.autofill_diagnostics_matches)).append(": ").append(e.optInt("matches")).append('/').append(e.optInt("datasets")).append('\n');
            out.append(context.getString(R.string.autofill_diagnostics_status)).append(": ").append(e.optString("status")).append('\n');
            out.append(context.getString(R.string.autofill_diagnostics_reason)).append(": ").append(e.optString("reason")).append("\n\n");
        }
        return out.toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
