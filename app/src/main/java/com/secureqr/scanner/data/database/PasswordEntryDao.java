package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.secureqr.scanner.data.model.PasswordEntry;

import java.util.List;

@Dao
public interface PasswordEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PasswordEntry entry);

    @Update
    void update(PasswordEntry entry);

    @Delete
    void delete(PasswordEntry entry);

    @Query("SELECT * FROM password_entries ORDER BY createdAt DESC")
    LiveData<List<PasswordEntry>> observeAll();

    @Query("SELECT * FROM password_entries WHERE remark LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    LiveData<List<PasswordEntry>> search(String query);

    @Query("SELECT * FROM password_entries ORDER BY createdAt DESC")
    List<PasswordEntry> getAllNow();

    @Query("SELECT * FROM password_entries WHERE remark = :remark AND account = :account LIMIT 1")
    PasswordEntry findByRemarkAndAccount(String remark, String account);
}

