package com.secureqr.scanner.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "vault_attachments", indices = {@Index("itemId")})
public class VaultAttachment {
    @PrimaryKey @NonNull public String id = UUID.randomUUID().toString();
    @NonNull public String itemId = "";
    @NonNull public String filename = "";
    @NonNull public String mimeType = "application/octet-stream";
    @NonNull public String encryptedPath = "";
    @NonNull public String hash = "";
    public long size;
    public long createdTime;
}
