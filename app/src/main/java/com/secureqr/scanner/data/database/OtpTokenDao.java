package com.secureqr.scanner.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.secureqr.scanner.data.model.OtpToken;

import java.util.List;

@Dao
public interface OtpTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(OtpToken token);

    @Update
    void update(OtpToken token);

    @Delete
    void delete(OtpToken token);

    @Query("SELECT * FROM otp_tokens WHERE accountName LIKE '%' || :query || '%' OR issuer LIKE '%' || :query || '%' ORDER BY pinned DESC, sortOrder ASC, createdAt DESC")
    LiveData<List<OtpToken>> observe(String query);

    @Query("SELECT * FROM otp_tokens ORDER BY pinned DESC, sortOrder ASC, createdAt DESC")
    List<OtpToken> getAllNow();

    @Query("SELECT * FROM otp_tokens WHERE secret = :secret AND accountName = :accountName LIMIT 1")
    OtpToken findBySecretAndAccount(String secret, String accountName);
}

