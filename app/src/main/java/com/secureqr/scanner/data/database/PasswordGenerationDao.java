package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.secureqr.scanner.data.model.PasswordGenerationRecord;

import java.util.List;

@Dao
public interface PasswordGenerationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PasswordGenerationRecord record);

    @Query("SELECT * FROM password_generation_records WHERE source = :source ORDER BY createdAt DESC LIMIT 100")
    LiveData<List<PasswordGenerationRecord>> observeRecent(String source);

    @Query("SELECT * FROM password_generation_records WHERE source = :source AND (password LIKE '%' || :query || '%' OR remark LIKE '%' || :query || '%' OR website LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%') ORDER BY createdAt DESC LIMIT 100")
    LiveData<List<PasswordGenerationRecord>> search(String source, String query);

    @Query("DELETE FROM password_generation_records WHERE id NOT IN (SELECT id FROM password_generation_records ORDER BY createdAt DESC LIMIT 100)")
    void trimTo100();

    @Query("DELETE FROM password_generation_records WHERE source = :source AND id NOT IN (SELECT id FROM password_generation_records WHERE source = :source ORDER BY createdAt DESC LIMIT 100)")
    void trimSourceTo100(String source);

    @Update
    void update(PasswordGenerationRecord record);

    @Delete
    void delete(PasswordGenerationRecord record);

    @Query("SELECT * FROM password_generation_records ORDER BY createdAt DESC")
    List<PasswordGenerationRecord> getAllNow();

    @Query("SELECT * FROM password_generation_records WHERE password = :password AND createdAt = :createdAt LIMIT 1")
    PasswordGenerationRecord findMatching(String password, long createdAt);

    @Query("SELECT * FROM password_generation_records WHERE source = 'REGISTRATION_AUTOFILL' AND password = :password AND linkedPasswordEntryId IS NULL ORDER BY createdAt DESC LIMIT 1")
    PasswordGenerationRecord findLatestUnlinkedRegistration(String password);
    @Query("SELECT * FROM password_generation_records WHERE itemId = :itemId LIMIT 1") PasswordGenerationRecord findByItemId(String itemId);
}

