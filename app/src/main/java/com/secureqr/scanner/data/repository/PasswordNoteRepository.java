package com.secureqr.scanner.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.database.PasswordEntryDao;
import com.secureqr.scanner.data.database.PasswordNoteDao;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordNote;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.DataSyncState;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class PasswordNoteRepository {
    private static volatile PasswordNoteRepository INSTANCE;
    private final PasswordNoteDao noteDao;
    private final Context appContext;
    private final PasswordEntryDao entryDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PasswordNoteRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(context);
        noteDao = database.passwordNoteDao();
        entryDao = database.passwordEntryDao();
    }

    public static PasswordNoteRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (PasswordNoteRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PasswordNoteRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<PasswordNote>> observe(String query, String typeFilter) {
        if (!canAccessSensitiveData()) return emptyLiveData();
        String normalized = query == null ? "" : query.trim();
        boolean loginOnly = PasswordNote.TYPE_LOGIN.equals(typeFilter);
        if (normalized.isEmpty()) {
            return loginOnly ? noteDao.observeByType(typeFilter) : noteDao.observeAll();
        }
        return loginOnly ? noteDao.searchByType(typeFilter, normalized) : noteDao.search(normalized);
    }

    public void save(PasswordNote note) {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            if (note.createdAt <= 0) note.createdAt = now;
            note.updatedAt = now;
            if (note.id == 0) {
                note.id = noteDao.insert(note);
            } else {
                noteDao.update(note);
            }
            if (PasswordNote.TYPE_LOGIN.equals(note.type)) {
                upsertPasswordEntry(note);
            }
            DataSyncState.markDirty(appContext);
        });
    }

    public void delete(PasswordNote note) {
        if (!canAccessSensitiveData()) return;
        new TrashRepository(appContext).move(note);
    }

    public void getAllNow(Consumer<List<PasswordNote>> callback) {
        if (!canAccessSensitiveData()) {
            if (callback != null) callback.accept(Collections.emptyList());
            return;
        }
        executor.execute(() -> {
            List<PasswordNote> notes = noteDao.getAllNow();
            if (callback != null) callback.accept(notes);
        });
    }

    public void syncLegacyPasswordEntries() {
        if (!canAccessSensitiveData()) return;
        executor.execute(() -> {
            for (PasswordEntry entry : entryDao.getAllNow()) {
                if (entry.id > 0 && noteDao.findByPasswordEntryId(entry.id) == null) {
                    PasswordNote note = fromPasswordEntry(entry);
                    noteDao.insert(note);
                }
            }
        });
    }

    private PasswordNote fromPasswordEntry(PasswordEntry entry) {
        long now = System.currentTimeMillis();
        PasswordNote note = new PasswordNote();
        note.type = PasswordNote.TYPE_LOGIN;
        note.title = firstNonEmpty(entry.title, entry.remark, entry.websiteDomain, entry.appPackageName, "Login");
        note.primaryText = firstNonEmpty(entry.username, entry.account, "");
        note.secondaryText = firstNonEmpty(entry.websiteDomain, entry.appPackageName, entry.remark, "");
        note.sourcePasswordEntryId = entry.id;
        note.createdAt = entry.createdAt > 0 ? entry.createdAt : now;
        note.updatedAt = entry.updatedAt > 0 ? entry.updatedAt : note.createdAt;
        try {
            JSONObject object = new JSONObject();
            object.put("website", firstNonEmpty(entry.websiteDomain, entry.remark, ""));
            object.put("app", firstNonEmpty(entry.appPackageName, ""));
            object.put("account", firstNonEmpty(entry.username, entry.account, ""));
            object.put("password", firstNonEmpty(entry.password, ""));
            object.put("notes", firstNonEmpty(entry.notes, ""));
            note.contentJson = object.toString();
        } catch (Exception e) {
            note.contentJson = "{}";
        }
        return note;
    }

    private void upsertPasswordEntry(PasswordNote note) {
        try {
            JSONObject object = new JSONObject(note.contentJson == null ? "{}" : note.contentJson);
            PasswordEntry entry = note.sourcePasswordEntryId > 0 ? entryDao.findById(note.sourcePasswordEntryId) : null;
            if (entry == null) {
                entry = entryDao.findMatchingCredential(object.optString("website"), object.optString("app"), object.optString("account"));
            }
            if (entry == null) {
                entry = new PasswordEntry();
                entry.createdAt = note.createdAt;
            }
            entry.title = note.title;
            entry.remark = firstNonEmpty(object.optString("website"), note.secondaryText, note.title);
            entry.websiteDomain = object.optString("website");
            entry.appPackageName = object.optString("app");
            entry.username = object.optString("account");
            entry.account = object.optString("account");
            entry.password = object.optString("password");
            entry.notes = object.optString("notes");
            entry.updatedAt = note.updatedAt;
            if (entry.id == 0) {
                note.sourcePasswordEntryId = entryDao.insert(entry);
                noteDao.update(note);
            } else {
                entryDao.update(entry);
            }
        } catch (Exception ignored) {
        }
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private boolean canAccessSensitiveData() {
        return VaultAccessManager.canAccessSensitiveData(appContext);
    }

    private LiveData<List<PasswordNote>> emptyLiveData() {
        MutableLiveData<List<PasswordNote>> data = new MutableLiveData<>();
        data.setValue(Collections.emptyList());
        return data;
    }
}
