package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.PasswordEntryDao;
import com.secureqr.scanner.data.model.PasswordEntry;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordRepository {
    public interface Callback<T> {
        void onResult(T result);
    }

    private static volatile PasswordRepository INSTANCE;
    private final PasswordEntryDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PasswordRepository(Context context) {
        dao = AppDatabase.getInstance(context).passwordEntryDao();
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
        String normalized = query == null ? "" : query.trim();
        return normalized.isEmpty() ? dao.observeAll() : dao.search(normalized);
    }

    public void insert(PasswordEntry entry) {
        executor.execute(() -> dao.insert(entry));
    }

    public void update(PasswordEntry entry) {
        executor.execute(() -> dao.update(entry));
    }

    public void delete(PasswordEntry entry) {
        executor.execute(() -> dao.delete(entry));
    }

    public void getAll(Callback<List<PasswordEntry>> callback) {
        executor.execute(() -> callback.onResult(dao.getAllNow()));
    }

    public void mergeEntries(List<PasswordEntry> remoteEntries, Runnable done) {
        executor.execute(() -> {
            for (PasswordEntry incoming : remoteEntries) {
                PasswordEntry local = dao.findByRemarkAndAccount(incoming.remark, incoming.account);
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
}

