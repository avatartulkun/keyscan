package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "password_history",
        indices = {
                @Index(value = {"historyId"}, name = "index_password_history_historyId", unique = true),
                @Index(value = {"entryItemId"}, name = "index_password_history_entryItemId")
        }
)
public class PasswordHistory {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String historyId;
    @NonNull
    public String entryItemId;
    @NonNull
    public String oldPassword;
    public long createdAt;
    @NonNull
    public String source;
    public String deviceId;
    public String note;

    public PasswordHistory() {
    }
}
