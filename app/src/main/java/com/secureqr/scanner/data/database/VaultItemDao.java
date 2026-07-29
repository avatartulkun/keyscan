package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.secureqr.scanner.data.model.VaultItem;
import java.util.List;

@Dao
public interface VaultItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insert(VaultItem item);
    @Update void update(VaultItem item);
    @Delete void delete(VaultItem item);
    @Query("SELECT * FROM vault_items ORDER BY updatedTime DESC") LiveData<List<VaultItem>> observeAll();
    @Query("SELECT * FROM vault_items WHERE title LIKE '%' || :query || '%' OR fieldsJson LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY updatedTime DESC") LiveData<List<VaultItem>> search(String query);
    @Query("SELECT * FROM vault_items ORDER BY updatedTime DESC") List<VaultItem> getAllNow();
    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1") VaultItem findById(String id);
}
