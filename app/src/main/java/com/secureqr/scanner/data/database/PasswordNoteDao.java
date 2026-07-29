package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.secureqr.scanner.data.model.PasswordNote;

import java.util.List;

@Dao
public interface PasswordNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PasswordNote note);

    @Update
    void update(PasswordNote note);

    @Delete
    void delete(PasswordNote note);

    @Query("SELECT * FROM password_notes ORDER BY updatedAt DESC, createdAt DESC")
    LiveData<List<PasswordNote>> observeAll();

    @Query("SELECT * FROM password_notes WHERE type = :type ORDER BY updatedAt DESC, createdAt DESC")
    LiveData<List<PasswordNote>> observeByType(String type);

    @Query("SELECT * FROM password_notes WHERE title LIKE '%' || :query || '%' OR primaryText LIKE '%' || :query || '%' OR secondaryText LIKE '%' || :query || '%' OR contentJson LIKE '%' || :query || '%' ORDER BY updatedAt DESC, createdAt DESC")
    LiveData<List<PasswordNote>> search(String query);

    @Query("SELECT * FROM password_notes WHERE type = :type AND (title LIKE '%' || :query || '%' OR primaryText LIKE '%' || :query || '%' OR secondaryText LIKE '%' || :query || '%' OR contentJson LIKE '%' || :query || '%') ORDER BY updatedAt DESC, createdAt DESC")
    LiveData<List<PasswordNote>> searchByType(String type, String query);

    @Query("SELECT * FROM password_notes WHERE sourcePasswordEntryId = :entryId LIMIT 1")
    PasswordNote findByPasswordEntryId(long entryId);

    @Query("SELECT * FROM password_notes WHERE type = :type")
    List<PasswordNote> getByTypeNow(String type);

    @Query("SELECT * FROM password_notes ORDER BY updatedAt DESC, createdAt DESC")
    List<PasswordNote> getAllNow();

    @Query("SELECT * FROM password_notes WHERE type = :type AND title = :title AND contentJson = :contentJson LIMIT 1")
    PasswordNote findMatching(String type, String title, String contentJson);
    @Query("SELECT * FROM password_notes WHERE id = :id LIMIT 1") PasswordNote findById(long id);
}
