package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.OtpTokenDao;
import com.secureqr.scanner.data.model.OtpToken;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OtpRepository {
    public interface Callback<T> {
        void onResult(T result);
    }

    private static volatile OtpRepository INSTANCE;
    private final OtpTokenDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private OtpRepository(Context context) {
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
        return dao.observe(query == null ? "" : query.trim());
    }

    public void insert(OtpToken token) {
        executor.execute(() -> dao.insert(token));
    }

    public void update(OtpToken token) {
        executor.execute(() -> dao.update(token));
    }

    public void delete(OtpToken token) {
        executor.execute(() -> dao.delete(token));
    }

    public void getAll(Callback<List<OtpToken>> callback) {
        executor.execute(() -> callback.onResult(dao.getAllNow()));
    }

    public void mergeTokens(List<OtpToken> remoteTokens, Runnable done) {
        executor.execute(() -> {
            for (OtpToken incoming : remoteTokens) {
                OtpToken local = dao.findBySecretAndAccount(incoming.secret, incoming.accountName);
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
}

