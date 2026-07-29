package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "password_notes")
public class PasswordNote {
    public static final String TYPE_LOGIN = "login";
    public static final String TYPE_SECURE_NOTE = "secure_note";
    public static final String TYPE_BANK_CARD = "bank_card";
    public static final String TYPE_SOFTWARE_LICENSE = "software_license";
    public static final String TYPE_SERVER = "server";
    public static final String TYPE_IDENTITY = "identity";
    public static final String TYPE_CUSTOM = "custom";

    @PrimaryKey(autoGenerate = true)
    public long id;
    public String type;
    public String title;
    public String primaryText;
    public String secondaryText;
    public String contentJson;
    public long sourcePasswordEntryId;
    public long createdAt;
    public long updatedAt;

    public PasswordNote() {
    }
}
