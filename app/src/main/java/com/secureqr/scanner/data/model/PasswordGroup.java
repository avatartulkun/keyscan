package com.secureqr.scanner.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "password_groups")
public class PasswordGroup {
    public static final String DEFAULT_ID = "00000000-0000-0000-0000-000000000000";
    public static final String SECURE_SHARE_ID = "00000000-0000-0000-0000-000000000001";
    /** System default groups are identified by DEFAULT_ID; their name is never localized in storage. */
    public static final String DEFAULT_NAME = "";

    @PrimaryKey
    @NonNull
    public String id;
    public String name;
    public int sortOrder;
    public boolean isDefault;
    public long createdAt;
    public long updatedAt;

    public PasswordGroup() {
    }

    @NonNull
    public String displayName() {
        if (DEFAULT_ID.equals(id)) return DEFAULT_NAME;
        if (name == null || name.trim().isEmpty()) return DEFAULT_NAME;
        return name.trim();
    }
}
