package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.secureqr.scanner.data.model.VaultAttachment;
import java.util.List;

@Dao
public interface VaultAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insert(VaultAttachment attachment);
    @Delete void delete(VaultAttachment attachment);
    @Query("SELECT * FROM vault_attachments WHERE itemId = :itemId ORDER BY createdTime DESC") LiveData<List<VaultAttachment>> observeForItem(String itemId);
    @Query("SELECT * FROM vault_attachments WHERE itemId = :itemId ORDER BY createdTime DESC") List<VaultAttachment> getForItemNow(String itemId);
    @Query("SELECT * FROM vault_attachments ORDER BY createdTime DESC") List<VaultAttachment> getAllNow();
    @Query("SELECT * FROM vault_attachments WHERE id = :id LIMIT 1") VaultAttachment findById(String id);
    @Query("DELETE FROM vault_attachments WHERE itemId = :itemId") void deleteForItem(String itemId);
}
