package com.secureqr.scanner.autofill;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class GeneratedPasswordSessionStore {
    private static final long TTL_MS = 3L * 60L * 1000L;
    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private GeneratedPasswordSessionStore() {
    }

    public static synchronized String getOrCreate(String key, PasswordFactory factory) {
        long now = System.currentTimeMillis();
        prune(now);
        Entry entry = ENTRIES.get(key);
        if (entry != null && entry.expiresAt > now) {
            return entry.password;
        }
        String password = factory.create();
        ENTRIES.put(key, new Entry(password, now + TTL_MS));
        return password;
    }

    public static synchronized void clear(String key) {
        if (key != null) ENTRIES.remove(key);
    }

    public static synchronized void clearAll() {
        ENTRIES.clear();
    }

    private static void prune(long now) {
        Iterator<Map.Entry<String, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt <= now) iterator.remove();
        }
    }

    public interface PasswordFactory {
        String create();
    }

    private static final class Entry {
        final String password;
        final long expiresAt;

        Entry(String password, long expiresAt) {
            this.password = password;
            this.expiresAt = expiresAt;
        }
    }
}
