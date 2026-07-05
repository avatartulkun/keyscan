package com.secureqr.scanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Locale;

@Entity(tableName = "records")
public class ScanRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String content;
    public String type;
    public String title;
    public String source;
    public String thumbnailBase64;
    public boolean isStarred;
    public long timestamp;

    public ScanRecord() {
    }

    public static ScanRecord fromContent(String content) {
        ScanRecord record = new ScanRecord();
        record.content = content;
        record.type = detectType(content);
        record.title = content.length() > 80 ? content.substring(0, 80) : content;
        record.source = "SCAN";
        record.timestamp = System.currentTimeMillis();
        return record;
    }

    public static ScanRecord fromGeneratedContent(String content, String title, String thumbnailBase64) {
        ScanRecord record = fromContent(content);
        record.source = "GENERATE";
        record.title = title == null || title.isEmpty() ? record.title : title;
        record.thumbnailBase64 = thumbnailBase64;
        return record;
    }

    public static String detectType(String content) {
        if (content == null) return "TEXT";
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return "URL";
        if (lower.startsWith("wifi:")) return "WIFI";
        return "TEXT";
    }
}

