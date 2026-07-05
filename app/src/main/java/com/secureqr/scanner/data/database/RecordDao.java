package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.secureqr.scanner.data.model.ScanRecord;

import java.util.List;

@Dao
public interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ScanRecord record);

    @Update
    void update(ScanRecord record);

    @Delete
    void delete(ScanRecord record);

    @Query("DELETE FROM records")
    void clearAll();

    @Query("SELECT * FROM records WHERE (isStarred = 1 OR id IN (SELECT id FROM records WHERE isStarred = 0 ORDER BY timestamp DESC LIMIT 500)) AND (:filter = 'ALL' OR type = :filter) AND content LIKE '%' || :query || '%' ORDER BY isStarred DESC, timestamp DESC")
    LiveData<List<ScanRecord>> observeRecords(String query, String filter);

    @Query("SELECT * FROM records ORDER BY isStarred DESC, timestamp DESC")
    List<ScanRecord> getAllNow();

    @Query("SELECT * FROM records WHERE isStarred = 1 OR id IN (SELECT id FROM records WHERE isStarred = 0 ORDER BY timestamp DESC LIMIT 500) ORDER BY isStarred DESC, timestamp DESC")
    List<ScanRecord> getSyncRecords();

    @Query("SELECT * FROM records WHERE content = :content AND type = :type LIMIT 1")
    ScanRecord findByContentAndType(String content, String type);

    @Query("DELETE FROM records WHERE isStarred = 0 AND id NOT IN (SELECT id FROM records WHERE isStarred = 0 ORDER BY timestamp DESC LIMIT 500)")
    void trimNonStarredTo500();
}

