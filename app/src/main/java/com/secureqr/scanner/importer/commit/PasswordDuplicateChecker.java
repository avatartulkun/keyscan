package com.secureqr.scanner.importer.commit;

import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.importer.model.ImportedPassword;

import java.util.List;
import java.util.Locale;

/** Applies the import duplicate rules without exposing database access to the UI. */
public final class PasswordDuplicateChecker {
    public PasswordEntry findDuplicate(ImportedPassword incoming, List<PasswordEntry> existing) {
        if (incoming == null || existing == null) return null;
        String website = domain(incoming.websiteDomain);
        String appPackage = normalized(incoming.appPackageName);
        String username = normalized(incoming.username);
        String title = normalized(incoming.title);
        for (PasswordEntry entry : existing) {
            if (entry == null) continue;
            String existingUser = normalized(entry.username == null || entry.username.trim().isEmpty() ? entry.account : entry.username);
            if (!website.isEmpty() && !username.isEmpty()
                    && website.equals(domain(entry.websiteDomain)) && username.equals(existingUser)) return entry;
            if (!appPackage.isEmpty() && !username.isEmpty()
                    && appPackage.equals(normalized(entry.appPackageName)) && username.equals(existingUser)) return entry;
            if (!title.isEmpty() && !username.isEmpty()
                    && title.equals(normalized(entry.displayTitle())) && username.equals(existingUser)) return entry;
        }
        return null;
    }

    private String domain(String value) {
        String normalized = normalized(value);
        if (normalized.startsWith("https://")) normalized = normalized.substring(8);
        else if (normalized.startsWith("http://")) normalized = normalized.substring(7);
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : normalized;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
