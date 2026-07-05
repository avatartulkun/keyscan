package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "password_generation_records")
public class PasswordGenerationRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String password;
    public String remark;
    public int length;
    public String configSummary;
    public long createdAt;
}

