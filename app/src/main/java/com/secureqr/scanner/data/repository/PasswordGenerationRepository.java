package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.PasswordGenerationDao;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordGenerationRepository {
    private static volatile PasswordGenerationRepository INSTANCE;
    private final PasswordGenerationDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PasswordGenerationRepository(Context context) {
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
        return dao.observeRecent();
    }

    public LiveData<List<PasswordGenerationRecord>> observeRecent(String query) {
        String normalized = query == null ? "" : query.trim();
        return normalized.isEmpty() ? dao.observeRecent() : dao.search(normalized);
    }

    public void insert(String password, int length, String configSummary) {
        executor.execute(() -> {
            PasswordGenerationRecord record = new PasswordGenerationRecord();
            record.password = password;
            record.length = length;
            record.configSummary = configSummary;
            record.createdAt = System.currentTimeMillis();
            dao.insert(record);
            dao.trimTo100();
        });
    }

    public void update(PasswordGenerationRecord record) {
        executor.execute(() -> dao.update(record));
    }

    public void delete(PasswordGenerationRecord record) {
        executor.execute(() -> dao.delete(record));
    }
}

