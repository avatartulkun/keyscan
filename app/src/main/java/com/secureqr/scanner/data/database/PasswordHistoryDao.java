package com.secureqr.scanner.data.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.secureqr.scanner.data.model.PasswordHistory;

import java.util.List;

@Dao
public interface PasswordHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(PasswordHistory history);

    @Delete
    void delete(PasswordHistory history);

    @Query("SELECT * FROM password_history WHERE entryItemId = :entryItemId ORDER BY createdAt DESC")
    List<PasswordHistory> getHistoryForEntry(String entryItemId);

    @Query("SELECT * FROM password_history WHERE historyId = :historyId LIMIT 1")
    PasswordHistory getByHistoryId(String historyId);

    @Query("DELETE FROM password_history WHERE historyId = :historyId")
    void deleteByHistoryId(String historyId);

    @Query("DELETE FROM password_history WHERE entryItemId = :entryItemId")
    void deleteAllForEntry(String entryItemId);
}
