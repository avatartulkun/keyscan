package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(tableName = "otp_tokens", indices = {@Index(value = "itemId", unique = true)})
public class OtpToken {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String itemId;
    public String accountName;
    public String issuer;
    public String secret;
    public int digits = 6;
    public int period = 30;
    public String algorithm = "SHA1";
    public boolean pinned;
    public int sortOrder;
    public long createdAt;
    public long updatedAt;
}

