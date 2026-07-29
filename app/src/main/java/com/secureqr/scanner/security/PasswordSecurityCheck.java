package com.secureqr.scanner.security;

import android.content.Context;
import android.content.SharedPreferences;

import com.secureqr.scanner.data.model.PasswordEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Small, local-only password hygiene check. Password values never leave the device. */
public final class PasswordSecurityCheck {
    public enum Risk { NORMAL, WEAK, STALE, DUPLICATE, CONFIRMED }
    private static final String PREFS = "password_security_check";
    private static final String KEY_CONFIRMED = "confirmed_risks";
    private static final long STALE_AFTER_MS = 90L * 24L * 60L * 60L * 1000L;
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
            "password", "password1", "123456", "12345678", "123456789", "1234567890",
            "qwerty", "qwerty123", "abc123", "letmein", "welcome", "admin", "iloveyou",
            "111111", "000000", "monkey", "dragon", "football", "sunshine"));

    private PasswordSecurityCheck() { }

    public static Result analyze(Context context, List<PasswordEntry> entries) {
        List<PasswordEntry> safe = entries == null ? Collections.emptyList() : entries;
        Map<String, Integer> uses = new HashMap<>();
        for (PasswordEntry entry : safe) {
            String password = clean(entry == null ? null : entry.password);
            if (!password.isEmpty()) uses.put(password, uses.containsKey(password) ? uses.get(password) + 1 : 1);
        }
        Set<String> confirmed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_CONFIRMED, Collections.emptySet());
        Map<Long, Risk> risks = new HashMap<>();
        Map<Long, String> keys = new HashMap<>();
        int weak = 0, stale = 0, duplicate = 0, normal = 0, confirmedCount = 0;
        for (PasswordEntry entry : safe) {
            if (entry == null) continue;
            Risk risk = rawRisk(entry, uses);
            String key = key(entry, risk);
            keys.put(entry.id, key);
            if (risk != Risk.NORMAL && confirmed.contains(key)) risk = Risk.CONFIRMED;
            risks.put(entry.id, risk);
            if (risk == Risk.WEAK) weak++;
            else if (risk == Risk.STALE) stale++;
            else if (risk == Risk.DUPLICATE) duplicate++;
            else if (risk == Risk.CONFIRMED) confirmedCount++;
            else normal++;
        }
        return new Result(risks, keys, weak, stale, duplicate, normal, confirmedCount);
    }

    public static void confirm(Context context, Result result, PasswordEntry entry) {
        if (result == null || entry == null) return;
        String key = result.keyFor(entry.id);
        if (key == null || key.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> values = new HashSet<>(prefs.getStringSet(KEY_CONFIRMED, Collections.emptySet()));
        values.add(key);
        prefs.edit().putStringSet(KEY_CONFIRMED, values).apply();
    }

    private static Risk rawRisk(PasswordEntry entry, Map<String, Integer> uses) {
        String password = clean(entry.password);
        if (isWeak(password, entry)) return Risk.WEAK;
        long changed = entry.updatedAt > 0 ? entry.updatedAt : entry.createdAt;
        if (changed > 0 && System.currentTimeMillis() - changed >= STALE_AFTER_MS) return Risk.STALE;
        if (!password.isEmpty() && uses.getOrDefault(password, 0) > 1) return Risk.DUPLICATE;
        return Risk.NORMAL;
    }

    private static boolean isWeak(String password, PasswordEntry entry) {
        if (password.length() < 8 || COMMON.contains(password.toLowerCase(Locale.ROOT))) return true;
        int types = 0;
        if (password.matches(".*[a-z].*")) types++;
        if (password.matches(".*[A-Z].*")) types++;
        if (password.matches(".*[0-9].*")) types++;
        if (password.matches(".*[^A-Za-z0-9].*")) types++;
        if (types < 3) return true;
        String lower = password.toLowerCase(Locale.ROOT);
        for (String pattern : Arrays.asList("qwerty", "asdf", "zxcv", "123456", "abcdef", "987654")) {
            if (lower.contains(pattern)) return true;
        }
        if (lower.matches(".*(.)\\1\\1\\1+.*")) return true;
        for (String related : Arrays.asList(clean(entry.displayTitle()), clean(entry.websiteDomain), clean(entry.displayUsername()))) {
            related = related.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (related.length() >= 4 && lower.contains(related)) return true;
        }
        return false;
    }

    private static String key(PasswordEntry entry, Risk risk) {
        if (risk == Risk.NORMAL) return "";
        return entry.id + ":" + risk.name() + ":" + clean(entry.password).hashCode() + ":" + entry.updatedAt + ":" + entry.createdAt;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public static final class Result {
        private final Map<Long, Risk> risks;
        private final Map<Long, String> keys;
        public final int weakCount;
        public final int staleCount;
        public final int duplicateCount;
        public final int normalCount;
        public final int confirmedCount;
        Result(Map<Long, Risk> risks, Map<Long, String> keys, int weak, int stale,
               int duplicate, int normal, int confirmed) {
            this.risks = risks; this.keys = keys; weakCount = weak; staleCount = stale;
            duplicateCount = duplicate; normalCount = normal; confirmedCount = confirmed;
        }
        public Risk riskFor(long id) { return risks.getOrDefault(id, Risk.NORMAL); }
        String keyFor(long id) { return keys.get(id); }
        public int riskCount() { return weakCount + staleCount + duplicateCount; }
    }
}
