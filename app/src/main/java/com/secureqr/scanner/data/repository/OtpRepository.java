package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.OtpTokenDao;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.DataSyncState;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

public class OtpRepository {
    public interface Callback<T> {
        void onResult(T result);
    }

    public interface ImportCallback {
        void onComplete(int successCount, int failCount);
    }

    private static volatile OtpRepository INSTANCE;
    private final OtpTokenDao dao;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private OtpRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = AppDatabase.getInstance(context).otpTokenDao();
    }

    public static OtpRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (OtpRepository.class) {
                if (INSTANCE == null) INSTANCE = new OtpRepository(context.getApplicationContext());
            }
        }
        return INSTANCE;
    }

    public LiveData<List<OtpToken>> observe(String query) {
        requireSensitiveDataAccess();
        return dao.observe(query == null ? "" : query.trim());
    }

    public void insert(OtpToken token) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> { ensureItemId(token); dao.insert(token); DataSyncState.markDirty(appContext); });
    }

    public void insert(OtpToken token, Callback<Long> callback) {
        if (!canAccessSensitiveData()) { if (callback != null) callback.onResult(-1L); return; }
        executor.execute(() -> { ensureItemId(token); long id = dao.insert(token); token.id = id; DataSyncState.markDirty(appContext); if (callback != null) callback.onResult(id); });
    }

    public void getById(long id, Callback<OtpToken> callback) {
        if (!canAccessSensitiveData()) { if (callback != null) callback.onResult(null); return; }
        executor.execute(() -> { if (callback != null) callback.onResult(dao.findById(id)); });
    }

    public void getAvailableForLogin(long loginId, Callback<List<OtpToken>> callback) {
        if (!canAccessSensitiveData()) { if (callback != null) callback.onResult(Collections.emptyList()); return; }
        executor.execute(() -> { if (callback != null) callback.onResult(dao.findAvailableForLogin(loginId)); });
    }

    public void update(OtpToken token) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> { dao.update(token); DataSyncState.markDirty(appContext); });
    }

    /** Persists a completed manual reorder in one queued write, rather than interrupting the drag for each row crossed. */
    public void updateSortOrder(List<OtpToken> tokens) {
        if (!canAccessSensitiveData() || tokens == null || tokens.isEmpty()) return;
        executor.execute(() -> {
            for (OtpToken token : tokens) dao.update(token);
            DataSyncState.markDirty(appContext);
        });
    }

    /** Executes import writes on the repository executor and reports completed writes. */
    public void importTokens(List<OtpToken> inserts, List<OtpToken> updates, ImportCallback callback) {
        if (!canAccessSensitiveData()) {
            if (callback != null) callback.onComplete(0, (inserts == null ? 0 : inserts.size()) + (updates == null ? 0 : updates.size()));
            return;
        }
        executor.execute(() -> {
            int success = 0;
            int failed = 0;
            if (inserts != null) {
                for (OtpToken token : inserts) {
                    try { ensureItemId(token); dao.insert(token); success++; } catch (Exception ignored) { failed++; }
                }
            }
            if (updates != null) {
                for (OtpToken token : updates) {
                    try { dao.update(token); success++; } catch (Exception ignored) { failed++; }
                }
            }
            if (success > 0) DataSyncState.markDirty(appContext);
            if (callback != null) callback.onComplete(success, failed);
        });
    }

    public void delete(OtpToken token) {
        if (!canAccessSensitiveData()) return;
        new TrashRepository(appContext).move(token);
    }

    public void getAll(Callback<List<OtpToken>> callback) {
        if (!canAccessSensitiveData()) {
            if (callback != null) callback.onResult(Collections.emptyList());
            return;
        }
        executor.execute(() -> callback.onResult(dao.getAllNow()));
    }

    public void mergeTokens(List<OtpToken> remoteTokens, Runnable done) {
        if (!canAccessSensitiveData()) {
            if (done != null) done.run();
            return;
        }
        executor.execute(() -> {
            for (OtpToken incoming : remoteTokens) {
                ensureItemId(incoming);
                OtpToken local = dao.findByItemId(incoming.itemId);
                if (local == null) local = dao.findBySecretAndAccount(incoming.secret, incoming.accountName);
                if (local == null) {
                    incoming.id = 0;
                    dao.insert(incoming);
                } else if (incoming.updatedAt >= local.updatedAt) {
                    incoming.id = local.id;
                    dao.update(incoming);
                }
            }
            if (done != null) done.run();
        });
    }

    private boolean canAccessSensitiveData() {
        return VaultAccessManager.canAccessSensitiveData(appContext);
    }

    private static void ensureItemId(OtpToken token) { if (token != null && (token.itemId == null || token.itemId.trim().isEmpty())) token.itemId=UUID.randomUUID().toString(); }

    private void requireSensitiveDataAccess() {
        if (!canAccessSensitiveData()) {
            throw new SecurityException("OTP vault is locked");
        }
    }
}

