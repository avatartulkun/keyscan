package com.secureqr.scanner.autofill;

import android.content.Context;
import com.secureqr.scanner.data.model.PasswordEntry;

import java.util.List;

public class PasswordAutofillRepository {
    private final VaultService vaultService;

    public PasswordAutofillRepository(Context context) {
        vaultService = new VaultService(context.getApplicationContext());
    }

    public List<PasswordEntry> findMatches(String packageName, String webDomain) {
        return vaultService.findExactMatches(packageName, webDomain);
    }

    public List<PasswordEntry> getAll() {
        return vaultService.getAll();
    }

    public PasswordEntry findById(long id) {
        return vaultService.findById(id);
    }

    public void updateLastUsed(long id, long time) {
        vaultService.updateLastUsed(id, time);
    }

    public long saveFromAutofill(String webDomain, String packageName, String username, String password) {
        return vaultService.createPasswordEntry(webDomain, packageName, username, password);
    }

    public void bindApp(long entryId, String packageName, String appName) {
        vaultService.bindApp(entryId, packageName, appName);
    }

    public boolean updatePasswordWithHistory(String webDomain, String packageName, String username, String oldPassword, String newPassword) {
        return vaultService.updatePasswordWithHistory(webDomain, packageName, username, oldPassword, newPassword);
    }
}
