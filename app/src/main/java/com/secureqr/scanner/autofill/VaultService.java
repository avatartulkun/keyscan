package com.secureqr.scanner.autofill;

import android.content.Context;

import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.WebDavAutoSyncManager;

import java.util.ArrayList;
import java.util.List;

public class VaultService {
    private final Context appContext;
    private final VaultRepository repository;

    public VaultService(Context context) {
        appContext = context.getApplicationContext();
        repository = new VaultRepository(appContext);
    }

    public List<PasswordEntry> findExactMatches(String packageName, String webDomain) {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) return new ArrayList<>();
        return repository.findExactMatches(packageName, webDomain);
    }

    public List<PasswordEntry> getAll() {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) return new ArrayList<>();
        return repository.getAll();
    }

    public PasswordEntry findById(long id) {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) return null;
        return repository.findById(id);
    }

    public void updateLastUsed(long id, long time) {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) return;
        repository.updateLastUsed(id, time);
    }

    public long createPasswordEntry(String webDomain, String packageName, String username, String password) {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) return 0;
        return repository.createOrUpdatePasswordEntry(webDomain, packageName, username, password);
    }

    public void bindApp(long entryId, String packageName, String appName) {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) return;
        repository.bindApp(entryId, packageName, appName);
    }

    public boolean updatePasswordWithHistory(String webDomain, String packageName, String username, String oldPassword, String newPassword) {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) return false;
        boolean updated = repository.updatePasswordWithHistory(webDomain, packageName, username, oldPassword, newPassword);
        if (updated) {
            WebDavAutoSyncManager.requestHighPrioritySync(appContext);
        }
        return updated;
    }
}
