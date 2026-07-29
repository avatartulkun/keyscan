package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.secureqr.scanner.data.model.PasswordGroup;

import java.util.List;

@Dao
public interface PasswordGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PasswordGroup group);

    @Update
    void update(PasswordGroup group);

    @Delete
    void delete(PasswordGroup group);

    @Query("SELECT * FROM password_groups ORDER BY isDefault DESC, sortOrder ASC, createdAt ASC")
    LiveData<List<PasswordGroup>> observeAll();

    @Query("SELECT * FROM password_groups ORDER BY isDefault DESC, sortOrder ASC, createdAt ASC")
    List<PasswordGroup> getAllNow();

    @Query("SELECT * FROM password_groups WHERE id = :id LIMIT 1")
    PasswordGroup findById(String id);

    @Query("SELECT COUNT(*) FROM password_entries WHERE groupId = :groupId")
    int countEntriesInGroup(String groupId);

    @Query("UPDATE password_entries SET groupId = :groupId WHERE groupId = :oldGroupId")
    int moveEntries(String oldGroupId, String groupId);

    @Query("SELECT MAX(sortOrder) FROM password_groups")
    Integer findMaxSortOrder();

    @Query("SELECT COUNT(*) FROM password_groups WHERE id = :id")
    int countById(String id);
}
