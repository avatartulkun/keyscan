package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(tableName = "password_generation_records", indices = {@Index(value = "itemId", unique = true)})
public class PasswordGenerationRecord {
    public static final String SOURCE_GENERATOR = "GENERATOR";
    public static final String SOURCE_REGISTRATION_AUTOFILL = "REGISTRATION_AUTOFILL";
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String itemId;
    public String password;
    public String remark;
    public int length;
    public String configSummary;
    public long createdAt;
    public String source = SOURCE_GENERATOR;
    public String website;
    public String account;
    public Long linkedPasswordEntryId;
    public String linkedPasswordEntryItemId;
}

