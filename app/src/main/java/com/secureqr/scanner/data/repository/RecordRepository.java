package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.RecordDao;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.utils.DataSyncState;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordRepository {
    public interface Callback<T> {
        void onResult(T result);
    }

    private static volatile RecordRepository INSTANCE;
    private final RecordDao dao;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private RecordRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = AppDatabase.getInstance(context).recordDao();
    }

    public static RecordRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (RecordRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RecordRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public static void resetInstance() {
        synchronized (RecordRepository.class) {
            if (INSTANCE != null) {
                INSTANCE.executor.shutdown();
                INSTANCE = null;
            }
        }
    }

    public LiveData<List<ScanRecord>> observeRecords(String query, String filter) {
        return dao.observeRecords(query == null ? "" : query, filter == null ? "ALL" : filter);
    }

    public void insert(ScanRecord record) {
        executor.execute(() -> {
            dao.insert(record);
            dao.trimNonStarredTo500();
            DataSyncState.markDirty(appContext);
        });
    }

    public void insert(ScanRecord record, Callback<ScanRecord> callback) {
        executor.execute(() -> {
            record.id = dao.insert(record);
            dao.trimNonStarredTo500();
            DataSyncState.markDirty(appContext);
            if (callback != null) callback.onResult(record);
        });
    }

    public void update(ScanRecord record) {
        executor.execute(() -> { dao.update(record); DataSyncState.markDirty(appContext); });
    }

    public void delete(ScanRecord record) {
        executor.execute(() -> { dao.delete(record); DataSyncState.markDirty(appContext); });
    }

    public void clearAll() {
        executor.execute(() -> { dao.clearAll(); DataSyncState.markDirty(appContext); });
    }

    public void getAll(Callback<List<ScanRecord>> callback) {
        executor.execute(() -> callback.onResult(dao.getAllNow()));
    }

    public void getSyncRecords(Callback<List<ScanRecord>> callback) {
        executor.execute(() -> callback.onResult(dao.getSyncRecords()));
    }

    public void mergeRecords(List<ScanRecord> remoteRecords, Runnable done) {
        executor.execute(() -> {
            for (ScanRecord incoming : remoteRecords) {
                ScanRecord local = dao.findByContentAndType(incoming.content, incoming.type);
                if (local == null) {
                    incoming.id = 0;
                    dao.insert(incoming);
                } else if (incoming.timestamp >= local.timestamp) {
                    incoming.id = local.id;
                    dao.update(incoming);
                }
            }
            dao.trimNonStarredTo500();
            if (done != null) done.run();
        });
    }
}

