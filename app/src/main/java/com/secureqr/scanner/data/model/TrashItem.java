package com.secureqr.scanner.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.UUID;

@Entity(tableName = "trash_items")
public class TrashItem {
    public static final String PASSWORD = "PASSWORD";
    public static final String OTP = "OTP";
    public static final String VAULT = "VAULT";
    public static final String NOTE = "NOTE";

    @PrimaryKey @NonNull public String id = UUID.randomUUID().toString();
    @NonNull public String type = "";
    @NonNull public String originalId = "";
    @NonNull public String title = "";
    @NonNull public String payload = "{}";
    public long deletedAt;
}
