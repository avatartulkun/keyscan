package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(tableName = "password_entries", indices = {@Index(value = "itemId", unique = true)})
public class PasswordEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @ColumnInfo(name = "itemId")
    public String itemId;
    @ColumnInfo(name = "groupId")
    public String groupId;
    public Long otpId;
    public String otpItemId;
    public String title;
    public String websiteDomain;
    public String appPackageName;
    public String username;
    public String password;
    public String account;
    public String remark;
    public String notes;
    public long lastUsedAt;
    public long createdAt;
    public long updatedAt;

    public PasswordEntry() {
    }

    public String displayTitle() {
        if (title != null && !title.trim().isEmpty()) return title;
        return remark == null ? "" : remark;
    }

    public String displayUsername() {
        if (username != null && !username.trim().isEmpty()) return username;
        return account == null ? "" : account;
    }
}

