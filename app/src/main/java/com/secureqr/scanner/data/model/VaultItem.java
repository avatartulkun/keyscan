package com.secureqr.scanner.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "vault_items")
public class VaultItem {
    @PrimaryKey @NonNull public String id = UUID.randomUUID().toString();
    @NonNull public String type = "CUSTOM";
    @NonNull public String category = "CUSTOM";
    @NonNull public String title = "";
    @NonNull public String fieldsJson = "{}";
    @NonNull public String notes = "";
    public long createdTime;
    public long updatedTime;
}
