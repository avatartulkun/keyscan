package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "password_entries")
public class PasswordEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String password;
    public String account;
    public String remark;
    public long createdAt;

    public PasswordEntry() {
    }
}

