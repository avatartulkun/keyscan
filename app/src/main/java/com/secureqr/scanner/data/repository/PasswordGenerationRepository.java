package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.PasswordGenerationDao;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;
import com.secureqr.scanner.utils.DataSyncState;
import com.secureqr.scanner.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

public class PasswordGenerationRepository {
    public interface Callback<T> { void onResult(T result); }
    private static volatile PasswordGenerationRepository INSTANCE;
    private final PasswordGenerationDao dao;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PasswordGenerationRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = AppDatabase.getInstance(context).passwordGenerationDao();
    }

    public static PasswordGenerationRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (PasswordGenerationRepository.class) {
                if (INSTANCE == null) INSTANCE = new PasswordGenerationRepository(context.getApplicationContext());
            }
        }
        return INSTANCE;
    }

    public LiveData<List<PasswordGenerationRecord>> observeRecent() {
        return dao.observeRecent(PasswordGenerationRecord.SOURCE_GENERATOR);
    }

    public LiveData<List<PasswordGenerationRecord>> observeRecent(String query) {
        String normalized = query == null ? "" : query.trim();
        return normalized.isEmpty() ? dao.observeRecent(PasswordGenerationRecord.SOURCE_GENERATOR) : dao.search(PasswordGenerationRecord.SOURCE_GENERATOR, normalized);
    }

    public LiveData<List<PasswordGenerationRecord>> observeRegistrationRecords(String query) {
        String normalized = query == null ? "" : query.trim();
        return normalized.isEmpty() ? dao.observeRecent(PasswordGenerationRecord.SOURCE_REGISTRATION_AUTOFILL)
                : dao.search(PasswordGenerationRecord.SOURCE_REGISTRATION_AUTOFILL, normalized);
    }

    public void insert(String password, int length, String configSummary) {
        executor.execute(() -> {
            PasswordGenerationRecord record = new PasswordGenerationRecord();
            record.itemId = UUID.randomUUID().toString();
            record.password = password;
            record.length = length;
            record.configSummary = configSummary;
            record.createdAt = System.currentTimeMillis();
            record.source = PasswordGenerationRecord.SOURCE_GENERATOR;
            dao.insert(record);
            dao.trimSourceTo100(PasswordGenerationRecord.SOURCE_GENERATOR);
            DataSyncState.markDirty(appContext);
        });
    }

    public void recordRegistrationFill(String password, String website, String account) {
        if (password == null || password.isEmpty()) return;
        executor.execute(() -> {
            PasswordGenerationRecord record = new PasswordGenerationRecord();
            record.itemId = UUID.randomUUID().toString();
            record.password = password; record.length = password.length(); record.configSummary = appContext.getString(R.string.password_registration_autofill_summary);
            record.website = website; record.account = account; record.source = PasswordGenerationRecord.SOURCE_REGISTRATION_AUTOFILL;
            record.createdAt = System.currentTimeMillis(); dao.insert(record); dao.trimSourceTo100(PasswordGenerationRecord.SOURCE_REGISTRATION_AUTOFILL);
        });
    }

    public void linkSavedEntry(String password, long entryId, String website, String account) {
        if (password == null || password.isEmpty() || entryId <= 0) return;
        executor.execute(() -> { PasswordGenerationRecord record = dao.findLatestUnlinkedRegistration(password); if (record != null) { record.linkedPasswordEntryId = entryId; com.secureqr.scanner.data.model.PasswordEntry entry=AppDatabase.getInstance(appContext).passwordEntryDao().findById(entryId); record.linkedPasswordEntryItemId=entry==null?null:entry.itemId; if (website != null && !website.isEmpty()) record.website = website; if (account != null && !account.isEmpty()) record.account = account; dao.update(record); } });
    }

    public void getAll(Callback<List<PasswordGenerationRecord>> callback) { executor.execute(() -> callback.onResult(dao.getAllNow())); }

    public void mergeRecords(List<PasswordGenerationRecord> records, Runnable done) {
        executor.execute(() -> { if (records != null) for (PasswordGenerationRecord incoming : records) {
            if (incoming.itemId == null || incoming.itemId.trim().isEmpty()) incoming.itemId=UUID.randomUUID().toString();
            PasswordGenerationRecord local=dao.findByItemId(incoming.itemId);
            com.secureqr.scanner.data.model.PasswordEntry linked=incoming.linkedPasswordEntryItemId==null?null:AppDatabase.getInstance(appContext).passwordEntryDao().findByItemId(incoming.linkedPasswordEntryItemId);
            incoming.linkedPasswordEntryId=linked==null?null:linked.id;
            if(local==null){incoming.id=0;dao.insert(incoming);} else if(incoming.createdAt>=local.createdAt){incoming.id=local.id;dao.update(incoming);}
        } if(done!=null)done.run(); });
    }

    public void update(PasswordGenerationRecord record) {
        executor.execute(() -> { dao.update(record); DataSyncState.markDirty(appContext); });
    }

    public void delete(PasswordGenerationRecord record) {
        executor.execute(() -> { dao.delete(record); DataSyncState.markDirty(appContext); });
    }
}

