package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.PasswordEntryDao;
import com.secureqr.scanner.data.database.PasswordHistoryDao;
import com.secureqr.scanner.data.database.PasswordGroupDao;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordHistory;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.DataSyncState;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordRepository {
    public interface Callback<T> {
        void onResult(T result);
    }

    public interface ImportCallback {
        void onComplete(int successCount, int failCount);
    }

    private static volatile PasswordRepository INSTANCE;
    private final AppDatabase database;
    private final Context appContext;
    private final PasswordEntryDao dao;
    private final PasswordHistoryDao historyDao;
    private final PasswordGroupDao groupDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PasswordRepository(Context context) {
        appContext = context.getApplicationContext();
        database = AppDatabase.getInstance(context);
        dao = database.passwordEntryDao();
        historyDao = database.passwordHistoryDao();
        groupDao = database.passwordGroupDao();
    }

    public static PasswordRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (PasswordRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PasswordRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<PasswordEntry>> observe(String query) {
        if (!canAccessSensitiveData()) return emptyLiveData();
        String normalized = query == null ? "" : query.trim();
        return normalized.isEmpty() ? dao.observeAll() : dao.search(normalized);
    }

    public void insert(PasswordEntry entry) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> database.runInTransaction(() -> {
            ensureNewEntryItemId(entry);
            ensureEntryGroupId(entry);
            syncOtpReference(entry);
            entry.id = dao.insert(entry);
            DataSyncState.markDirty(appContext);
            PasswordGenerationRepository.getInstance(appContext).linkSavedEntry(entry.password, entry.id, entry.websiteDomain, entry.displayUsername());
        }));
    }

    /** Saves a directly scanned sharing record into the stable system sharing group. */
    public void insertSecureShare(PasswordEntry entry, Runnable done) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> database.runInTransaction(() -> {
            PasswordGroup group = groupDao.findById(PasswordGroup.SECURE_SHARE_ID);
            if (group == null) {
                group = new PasswordGroup();
                group.id = PasswordGroup.SECURE_SHARE_ID;
                group.name = "";
                Integer max = groupDao.findMaxSortOrder();
                group.sortOrder = max == null ? 1 : max + 1;
                group.isDefault = false;
                group.createdAt = System.currentTimeMillis();
                group.updatedAt = group.createdAt;
                groupDao.insert(group);
            }
            ensureNewEntryItemId(entry);
            entry.groupId = PasswordGroup.SECURE_SHARE_ID;
            entry.id = dao.insert(entry);
            DataSyncState.markDirty(appContext);
        }));
        if (done != null) executor.execute(done);
    }

    public void update(PasswordEntry entry) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> { syncOtpReference(entry); updateWithPasswordHistory(entry); DataSyncState.markDirty(appContext); PasswordGenerationRepository.getInstance(appContext).linkSavedEntry(entry.password, entry.id, entry.websiteDomain, entry.displayUsername()); });
    }

    /** Executes import writes on the repository executor and reports completed writes. */
    public void importEntries(List<PasswordEntry> inserts, List<PasswordEntry> updates, ImportCallback callback) {
        if (!canAccessSensitiveData()) {
            if (callback != null) callback.onComplete(0, (inserts == null ? 0 : inserts.size()) + (updates == null ? 0 : updates.size()));
            return;
        }
        executor.execute(() -> {
            int success = 0;
            int failed = 0;
            if (inserts != null) {
                for (PasswordEntry entry : inserts) {
                    try {
                        database.runInTransaction(() -> {
                            ensureNewEntryItemId(entry);
                            ensureEntryGroupId(entry);
                            dao.insert(entry);
                        });
                        success++;
                    } catch (Exception ignored) { failed++; }
                }
            }
            if (updates != null) {
                for (PasswordEntry entry : updates) {
                    try {
                        updateWithPasswordHistory(entry);
                        success++;
                    } catch (Exception ignored) { failed++; }
                }
            }
            if (success > 0) DataSyncState.markDirty(appContext);
            if (callback != null) callback.onComplete(success, failed);
        });
    }

    public void restorePasswordFromHistory(long entryId, String historyId) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> database.runInTransaction(() -> {
            PasswordEntry entry = dao.findById(entryId);
            PasswordHistory history = historyDao.getByHistoryId(historyId);
            if (entry == null || history == null || !Objects.equals(entry.itemId, history.entryItemId)) return;
            PasswordHistory current = new PasswordHistory();
            current.historyId = UUID.randomUUID().toString();
            current.entryItemId = entry.itemId;
            current.oldPassword = entry.password == null ? "" : entry.password;
            current.createdAt = System.currentTimeMillis();
            current.source = "restore";
            historyDao.insert(current);
            entry.password = history.oldPassword;
            entry.updatedAt = System.currentTimeMillis();
            dao.update(entry);
            DataSyncState.markDirty(appContext);
        }));
    }

    public void getPasswordHistory(String entryItemId, Callback<List<PasswordHistory>> callback) {
        if (!canAccessSensitiveData()) {
            if (callback != null) callback.onResult(Collections.emptyList());
            return;
        }
        executor.execute(() -> callback.onResult(historyDao.getHistoryForEntry(entryItemId)));
    }

    public void deletePasswordHistory(PasswordHistory history) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> {
            historyDao.delete(history);
            DataSyncState.markDirty(appContext);
        });
    }

    public void getEntry(long id, Callback<PasswordEntry> callback) {
        if (!canAccessSensitiveData()) {
            if (callback != null) callback.onResult(null);
            return;
        }
        executor.execute(() -> callback.onResult(dao.findById(id)));
    }

    public void delete(PasswordEntry entry) {
        if (!canAccessSensitiveData()) return;
        new TrashRepository(appContext).move(entry);
    }

    public void getAll(Callback<List<PasswordEntry>> callback) {
        if (!canAccessSensitiveData()) {
            if (callback != null) callback.onResult(Collections.emptyList());
            return;
        }
        executor.execute(() -> callback.onResult(dao.getAllNow()));
    }

    public LiveData<List<PasswordGroup>> observeGroups() {
        return groupDao.observeAll();
    }

    public void getGroups(Callback<List<PasswordGroup>> callback) {
        executor.execute(() -> callback.onResult(groupDao.getAllNow()));
    }

    public List<PasswordGroup> getGroupsNow() {
        return groupDao.getAllNow();
    }

    public void createGroup(String name) {
        createGroup(name, null);
    }

    public void createGroup(String name, Callback<PasswordGroup> callback) {
        executor.execute(() -> database.runInTransaction(() -> {
            PasswordGroup group = new PasswordGroup();
            group.id = UUID.randomUUID().toString();
            group.name = isBlank(name) ? PasswordGroup.DEFAULT_NAME : name.trim();
            Integer max = groupDao.findMaxSortOrder();
            group.sortOrder = max == null ? 1 : max + 1;
            group.isDefault = false;
            long now = System.currentTimeMillis();
            group.createdAt = now;
            group.updatedAt = now;
            groupDao.insert(group);
            if (callback != null) callback.onResult(group);
        }));
    }

    public void mergeGroups(List<PasswordGroup> remoteGroups, Runnable done) {
        executor.execute(() -> {
            database.runInTransaction(() -> {
                if (remoteGroups == null) return;
                for (PasswordGroup incoming : remoteGroups) {
                    if (incoming == null) continue;
                    if (isBlank(incoming.id)) incoming.id = PasswordGroup.DEFAULT_ID;
                    if (isBlank(incoming.name)) incoming.name = PasswordGroup.DEFAULT_NAME;
                    PasswordGroup local = groupDao.findById(incoming.id);
                    if (local == null) {
                        if (isBlank(incoming.id)) continue;
                        if (incoming.createdAt <= 0) incoming.createdAt = System.currentTimeMillis();
                        if (incoming.updatedAt <= 0) incoming.updatedAt = incoming.createdAt;
                        if (PasswordGroup.DEFAULT_ID.equals(incoming.id)) {
                            incoming.isDefault = true;
                        }
                        groupDao.insert(incoming);
                    } else if (incoming.updatedAt >= local.updatedAt || incoming.sortOrder != local.sortOrder || !Objects.equals(incoming.name, local.name)) {
                        local.name = incoming.name;
                        local.sortOrder = incoming.sortOrder;
                        local.isDefault = local.isDefault || incoming.isDefault || PasswordGroup.DEFAULT_ID.equals(local.id);
                        local.updatedAt = Math.max(incoming.updatedAt, local.updatedAt);
                        groupDao.update(local);
                    }
                }
            });
            if (done != null) done.run();
        });
    }

    public void renameGroup(PasswordGroup group, String name) {
        executor.execute(() -> database.runInTransaction(() -> {
            if (group == null) return;
            PasswordGroup stored = groupDao.findById(group.id);
            if (stored == null) return;
            stored.name = isBlank(name) ? stored.name : name.trim();
            stored.isDefault = stored.isDefault || PasswordGroup.DEFAULT_ID.equals(stored.id);
            stored.updatedAt = System.currentTimeMillis();
            groupDao.update(stored);
        }));
    }

    public void deleteGroup(String groupId) {
        executor.execute(() -> database.runInTransaction(() -> {
            if (isBlank(groupId) || PasswordGroup.DEFAULT_ID.equals(groupId)) return;
            PasswordGroup group = groupDao.findById(groupId);
            if (group == null || group.isDefault) return;
            groupDao.moveEntries(groupId, PasswordGroup.DEFAULT_ID);
            groupDao.delete(group);
        }));
    }

    public void mergeEntries(List<PasswordEntry> remoteEntries, Runnable done) {
        if (!canAccessSensitiveData()) {
            if (done != null) done.run();
            return;
        }
        executor.execute(() -> {
            for (PasswordEntry incoming : remoteEntries) {
                normalizeEntry(incoming);
                syncOtpReference(incoming);
                PasswordEntry local = isBlank(incoming.itemId) ? null : dao.findByItemId(incoming.itemId);
                if (local == null) local = dao.findMatchingCredential(
                        incoming.websiteDomain == null ? "" : incoming.websiteDomain,
                        incoming.appPackageName == null ? "" : incoming.appPackageName,
                        incoming.username == null ? incoming.account : incoming.username
                );
                if (local == null) {
                    local = dao.findByRemarkAndAccount(incoming.remark, incoming.account);
                }
                if (local == null) {
                    incoming.id = 0;
                    dao.insert(incoming);
                } else if (incoming.createdAt >= local.createdAt) {
                    incoming.id = local.id;
                    dao.update(incoming);
                }
            }
            if (done != null) done.run();
        });
    }

    private void normalizeEntry(PasswordEntry entry) {
        if (entry == null) return;
        if ((entry.title == null || entry.title.trim().isEmpty()) && entry.remark != null) {
            entry.title = entry.remark;
        }
        if ((entry.username == null || entry.username.trim().isEmpty()) && entry.account != null) {
            entry.username = entry.account;
        }
        if (entry.updatedAt <= 0) entry.updatedAt = entry.createdAt;
        ensureEntryGroupId(entry);
    }

    private void updateWithPasswordHistory(PasswordEntry entry) {
        if (entry == null || entry.id <= 0) return;
        database.runInTransaction(() -> {
            PasswordEntry oldEntry = dao.findById(entry.id);
            if (oldEntry == null) {
                throw new IllegalStateException("PasswordEntry not found: " + entry.id);
            }

            if (isBlank(oldEntry.itemId)) {
                entry.itemId = UUID.randomUUID().toString();
            } else {
                entry.itemId = oldEntry.itemId;
            }

            boolean passwordChanged = !Objects.equals(oldEntry.password, entry.password);
            if (passwordChanged) {
                PasswordHistory history = new PasswordHistory();
                history.historyId = UUID.randomUUID().toString();
                history.entryItemId = entry.itemId;
                history.oldPassword = oldEntry.password == null ? "" : oldEntry.password;
                history.createdAt = System.currentTimeMillis();
                history.source = "manual_edit";
                history.deviceId = null;
                history.note = null;
                historyDao.insert(history);
            }

            int updated = dao.updateAndCount(entry);
            if (updated != 1) {
                throw new IllegalStateException("PasswordEntry update failed: " + entry.id);
            }
        });
    }

    private void ensureNewEntryItemId(PasswordEntry entry) {
        if (entry != null && isBlank(entry.itemId)) {
            entry.itemId = UUID.randomUUID().toString();
        }
    }

    private void ensureEntryGroupId(PasswordEntry entry) {
        if (entry != null && isBlank(entry.groupId)) {
            entry.groupId = PasswordGroup.DEFAULT_ID;
        }
    }

    private void syncOtpReference(PasswordEntry entry) {
        if (entry == null) return;
        if (!isBlank(entry.otpItemId)) { com.secureqr.scanner.data.model.OtpToken otp=database.otpTokenDao().findByItemId(entry.otpItemId); entry.otpId=otp==null?null:otp.id; }
        else if (entry.otpId != null) { com.secureqr.scanner.data.model.OtpToken otp=database.otpTokenDao().findById(entry.otpId); entry.otpItemId=otp==null?null:otp.itemId; }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean canAccessSensitiveData() {
        return VaultAccessManager.canAccessSensitiveData(appContext);
    }

    private LiveData<List<PasswordEntry>> emptyLiveData() {
        MutableLiveData<List<PasswordEntry>> data = new MutableLiveData<>();
        data.setValue(Collections.emptyList());
        return data;
    }
}

