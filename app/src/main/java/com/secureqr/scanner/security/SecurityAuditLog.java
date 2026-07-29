package com.secureqr.scanner.security;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SecurityAuditLog {
    private static final String PREFS = "security_audit_log";
    private static final String KEY_EVENTS = "events";
    private static final int MAX_EVENTS = 50;

    private SecurityAuditLog() {
    }

    public static void record(Context context, String event, boolean success) {
        if (context == null) return;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
        String line = time + " · " + safe(event) + " · " + (success ? "成功" : "失败");
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LinkedHashSet<String> next = new LinkedHashSet<>();
        next.add(line);
        Set<String> saved = prefs.getStringSet(KEY_EVENTS, new LinkedHashSet<>());
        if (saved != null) next.addAll(saved);
        while (next.size() > MAX_EVENTS) {
            String last = null;
            for (String value : next) last = value;
            if (last == null) break;
            next.remove(last);
        }
        prefs.edit().putStringSet(KEY_EVENTS, next).apply();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "安全操作";
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
