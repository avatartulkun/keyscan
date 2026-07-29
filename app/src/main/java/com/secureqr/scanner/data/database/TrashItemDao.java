package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.secureqr.scanner.data.model.TrashItem;
import java.util.List;

@Dao
public interface TrashItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insert(TrashItem item);
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC") LiveData<List<TrashItem>> observeAll();
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC") List<TrashItem> getAllNow();
    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1") TrashItem findById(String id);
    @Query("DELETE FROM trash_items WHERE id = :id") void deleteById(String id);
    @Query("DELETE FROM trash_items WHERE deletedAt < :cutoff") void deleteOlderThan(long cutoff);
}
