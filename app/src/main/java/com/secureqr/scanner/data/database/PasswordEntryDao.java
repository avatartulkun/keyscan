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

    @Update
    int updateAndCount(PasswordEntry entry);

    @Delete
    void delete(PasswordEntry entry);

    @Query("SELECT * FROM password_entries ORDER BY createdAt DESC")
    LiveData<List<PasswordEntry>> observeAll();

    @Query("SELECT * FROM password_entries WHERE remark LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR websiteDomain LIKE '%' || :query || '%' OR appPackageName LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    LiveData<List<PasswordEntry>> search(String query);

    @Query("SELECT * FROM password_entries ORDER BY createdAt DESC")
    List<PasswordEntry> getAllNow();

    @Query("SELECT * FROM password_entries WHERE id = :id LIMIT 1")
    PasswordEntry findById(long id);
    @Query("SELECT * FROM password_entries WHERE itemId = :itemId LIMIT 1") PasswordEntry findByItemId(String itemId);

    @Query("SELECT * FROM password_entries WHERE remark = :remark AND account = :account LIMIT 1")
    PasswordEntry findByRemarkAndAccount(String remark, String account);

    @Query("SELECT * FROM password_entries WHERE ((websiteDomain = :websiteDomain AND websiteDomain != '') OR (appPackageName = :appPackageName AND appPackageName != '') OR (remark = :websiteDomain AND remark != '')) AND (username = :username OR account = :username) LIMIT 1")
    PasswordEntry findMatchingCredential(String websiteDomain, String appPackageName, String username);

    @Query("UPDATE password_entries SET lastUsedAt = :time WHERE id = :id")
    void updateLastUsed(long id, long time);

    @Query("SELECT id FROM password_entries WHERE itemId IS NULL OR TRIM(itemId) = ''")
    List<Long> findIdsMissingItemId();

    @Query("UPDATE password_entries SET itemId = :itemId WHERE id = :id AND (itemId IS NULL OR TRIM(itemId) = '')")
    int updateItemIdIfMissing(long id, String itemId);

    @Query("UPDATE password_entries SET groupId = :groupId WHERE id = :id")
    int updateGroupId(long id, String groupId);

    @Query("UPDATE password_entries SET groupId = :groupId WHERE groupId = :oldGroupId")
    int moveEntriesToGroup(String oldGroupId, String groupId);
}

