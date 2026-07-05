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

    @Query("SELECT * FROM password_generation_records ORDER BY createdAt DESC LIMIT 100")
    LiveData<List<PasswordGenerationRecord>> observeRecent();

    @Query("SELECT * FROM password_generation_records WHERE password LIKE '%' || :query || '%' OR remark LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT 100")
    LiveData<List<PasswordGenerationRecord>> search(String query);

    @Query("DELETE FROM password_generation_records WHERE id NOT IN (SELECT id FROM password_generation_records ORDER BY createdAt DESC LIMIT 100)")
    void trimTo100();

    @Update
    void update(PasswordGenerationRecord record);

    @Delete
    void delete(PasswordGenerationRecord record);
}

