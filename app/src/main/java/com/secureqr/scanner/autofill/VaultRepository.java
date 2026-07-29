package com.secureqr.scanner.autofill;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.PasswordEntryDao;
import com.secureqr.scanner.data.database.PasswordHistoryDao;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordHistory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class VaultRepository {
    private final PasswordEntryDao dao;
    private final PasswordHistoryDao historyDao;
    private final Context appContext;

    public VaultRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(appContext);
        dao = database.passwordEntryDao();
        historyDao = database.passwordHistoryDao();
    }

    public List<PasswordEntry> findExactMatches(String packageName, String webDomain) {
        List<Match> scored = new ArrayList<>();
        String targetPackage = AutofillCredentialMatcher.normalizePackage(packageName);
        String targetDomain = AutofillCredentialMatcher.normalizeDomain(webDomain);
        String appName = appName(targetPackage);
        for (PasswordEntry entry : dao.getAllNow()) {
            int score = matchScore(entry, targetPackage, targetDomain, appName);
            if (score > 0) scored.add(new Match(entry, score));
        }
        scored.sort((left, right) -> {
            int score = Integer.compare(right.score, left.score);
            if (score != 0) return score;
            return Long.compare(right.entry.lastUsedAt, left.entry.lastUsedAt);
        });
        List<PasswordEntry> matches = new ArrayList<>();
        for (Match match : scored) matches.add(match.entry);
        return matches;
    }

    public List<PasswordEntry> getAll() {
        List<PasswordEntry> entries = new ArrayList<>(dao.getAllNow());
        entries.sort(Comparator.comparingLong((PasswordEntry entry) -> entry.lastUsedAt).reversed());
        return entries;
    }

    public void bindApp(long entryId, String packageName, String appName) {
        AutofillLinkStore.linkApp(appContext, entryId, packageName, appName);
    }

    private int matchScore(PasswordEntry entry, String targetPackage, String targetDomain, String appName) {
        if (entry == null || TextUtils.isEmpty(entry.password)) return 0;
        String entryPackage = AutofillCredentialMatcher.normalizePackage(entry.appPackageName);
        if (!targetPackage.isEmpty() && targetPackage.equals(entryPackage)) return 100;
        if (!targetPackage.isEmpty() && AutofillLinkStore.isAppLinked(appContext, entry.id, targetPackage)) return 90;
        if (!targetDomain.isEmpty()) {
            String entryDomain = AutofillCredentialMatcher.normalizeDomain(AutofillCredentialMatcher.nonEmpty(entry.websiteDomain, entry.remark, entry.title));
            if (AutofillCredentialMatcher.domainMatches(targetDomain, entryDomain)) return 80;
        }
        if (titleMatches(entry, appName, targetDomain)) return 60;
        return 0;
    }

    private boolean titleMatches(PasswordEntry entry, String appName, String webDomain) {
        String target = AutofillCredentialMatcher.nonEmpty(appName, webDomain).toLowerCase();
        if (target.isEmpty()) return false;
        String title = AutofillCredentialMatcher.nonEmpty(entry.title, entry.remark, entry.websiteDomain).toLowerCase();
        return !title.isEmpty() && (target.contains(title) || title.contains(target));
    }

    private String appName(String packageName) {
        if (TextUtils.isEmpty(packageName)) return "";
        try {
            PackageManager pm = appContext.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? "" : label.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public PasswordEntry findById(long id) {
        return dao.findById(id);
    }

    public void updateLastUsed(long id, long time) {
        dao.updateLastUsed(id, time);
    }

    public long createOrUpdatePasswordEntry(String webDomain, String packageName, String username, String password) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) return 0;
        String normalizedDomain = AutofillCredentialMatcher.normalizeDomain(webDomain);
        String normalizedPackage = AutofillCredentialMatcher.normalizePackage(packageName);
        String title = AutofillCredentialMatcher.bestTitleFromContext(normalizedDomain, normalizedPackage);
        long now = System.currentTimeMillis();
        PasswordEntry entry = dao.findMatchingCredential(normalizedDomain, normalizedPackage, username);
        if (entry == null) {
            entry = new PasswordEntry();
            entry.createdAt = now;
        } else if (!TextUtils.isEmpty(entry.password) && !entry.password.equals(password)) {
            if (TextUtils.isEmpty(entry.itemId)) entry.itemId = UUID.randomUUID().toString();
            PasswordHistory history = new PasswordHistory();
            history.historyId = UUID.randomUUID().toString();
            history.entryItemId = entry.itemId;
            history.oldPassword = entry.password;
            history.createdAt = now;
            history.source = "auto_update";
            historyDao.insert(history);
        }
        entry.title = title;
        entry.websiteDomain = normalizedDomain;
        entry.appPackageName = normalizedPackage;
        entry.username = username;
        entry.account = username;
        entry.password = password;
        entry.remark = title;
        entry.updatedAt = now;
        if (entry.createdAt <= 0) entry.createdAt = now;
        return entry.id == 0 ? dao.insert(entry) : updateAndReturnId(entry);
    }

    public boolean updatePasswordWithHistory(String webDomain, String packageName, String username, String oldPassword, String newPassword) {
        if (TextUtils.isEmpty(oldPassword) || TextUtils.isEmpty(newPassword) || oldPassword.equals(newPassword)) {
            return false;
        }
        String normalizedUser = username == null ? "" : username.trim();
        for (PasswordEntry entry : findExactMatches(packageName, webDomain)) {
            if (!normalizedUser.isEmpty()
                    && !normalizedUser.equals(AutofillCredentialMatcher.displayUsername(entry))) {
                continue;
            }
            if (!oldPassword.equals(entry.password)) continue;
            if (TextUtils.isEmpty(entry.itemId)) entry.itemId = UUID.randomUUID().toString();
            PasswordHistory history = new PasswordHistory();
            history.historyId = UUID.randomUUID().toString();
            history.entryItemId = entry.itemId;
            history.oldPassword = entry.password == null ? "" : entry.password;
            history.createdAt = System.currentTimeMillis();
            history.source = "auto_update";
            historyDao.insert(history);
            entry.password = newPassword;
            entry.updatedAt = System.currentTimeMillis();
            dao.update(entry);
            return true;
        }
        return false;
    }

    private long updateAndReturnId(PasswordEntry entry) {
        dao.update(entry);
        return entry.id;
    }

    private static final class Match {
        final PasswordEntry entry;
        final int score;
        Match(PasswordEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

}
