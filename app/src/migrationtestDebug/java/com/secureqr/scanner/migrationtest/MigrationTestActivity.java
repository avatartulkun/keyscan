package com.secureqr.scanner.migrationtest;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.secureqr.scanner.data.database.AppDatabase;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MigrationTestActivity extends Activity {
    private static final String TAG = "KeyScanMigrationTest";
    private static final String DATABASE_PASSWORD = "";
    private static final String TEST_DIR_NAME = "db-migration";
    private static final String DB_FILE_NAME = "scanner.db";

    private static final Map<String, Integer> EXPECTED_COUNTS = new LinkedHashMap<>();

    static {
        EXPECTED_COUNTS.put("records", 3);
        EXPECTED_COUNTS.put("password_entries", 6);
        EXPECTED_COUNTS.put("password_generation_records", 2);
        EXPECTED_COUNTS.put("otp_tokens", 16);
        EXPECTED_COUNTS.put("password_notes", 6);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView output = new TextView(this);
        output.setTextIsSelectable(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        output.setPadding(padding, padding, padding, padding);
        setContentView(output);

        String report = runMigrationTest();
        output.setText(report);
        Log.i(TAG, report);
    }

    private String runMigrationTest() {
        StringBuilder report = new StringBuilder();
        File databaseFile = new File(new File(getFilesDir(), TEST_DIR_NAME), DB_FILE_NAME);
        File walFile = new File(databaseFile.getParentFile(), DB_FILE_NAME + "-wal");
        File shmFile = new File(databaseFile.getParentFile(), DB_FILE_NAME + "-shm");

        report.append("KeyScan migrationtest\n");
        report.append("package: ").append(getPackageName()).append('\n');
        report.append("target: files/").append(TEST_DIR_NAME).append('/').append(DB_FILE_NAME).append('\n');
        if (DATABASE_PASSWORD.isEmpty()) {
            report.append("Legacy fixed database password has been removed from migration test builds.\n");
            report.append("This screen no longer opens legacy fixed-key databases.\n");
            return report.toString();
        }
        report.append("scanner.db exists: ").append(databaseFile.exists()).append('\n');
        report.append("scanner.db-wal exists: ").append(walFile.exists()).append('\n');
        report.append("scanner.db-shm exists: ").append(shmFile.exists()).append("\n\n");

        if (!databaseFile.exists()) {
            report.append("Migration test copy not found\n");
            return report.toString();
        }

        SQLiteDatabase db = null;
        try {
            db = openDatabase(databaseFile, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            int initialVersion = db.getVersion();
            report.append("Before test\n");
            report.append("user_version: ").append(initialVersion).append('\n');
            appendExpectedCounts(report, db);
            String notesHashBefore = notesSha256(db);
            report.append("notes SHA-256 before: ").append(notesHashBefore).append("\n\n");

            if (initialVersion == 6) {
                AppDatabase.MIGRATION_6_7.migrate(db);
                db.setVersion(7);
            } else if (initialVersion != 7) {
                report.append("ABORT: expected user_version 6 or 7 before test\n");
                return report.toString();
            }

            AppDatabase.backfillMissingPasswordEntryItemIds(db);
            String itemIdSnapshotAfterFirstBackfill = itemIdSnapshotSha256(db);
            AppDatabase.backfillMissingPasswordEntryItemIds(db);
            String itemIdSnapshotAfterSecondBackfill = itemIdSnapshotSha256(db);
            String historyCoreReport = runPasswordHistoryCoreTest(db);
            db.close();
            db = null;

            db = openDatabase(databaseFile, SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            db.rawExecSQL("PRAGMA query_only = ON");

            report.append("After migration\n");
            appendUserVersion(report, db, 7);
            appendExpectedCounts(report, db);
            report.append("password_history exists: ")
                    .append(passFail(tableExists(db, "password_history")))
                    .append('\n');
            report.append("password_history count: ")
                    .append(passFail(countRows(db, "password_history") == 0))
                    .append(" actual=")
                    .append(countRows(db, "password_history"))
                    .append(" expected=0\n");
            report.append("password_entries.itemId exists: ")
                    .append(passFail(columnExists(db, "password_entries", "itemId")))
                    .append('\n');
            int itemIdCount = countNonEmptyItemIds(db);
            report.append("password_entries non-empty itemId count: ")
                    .append(passFail(itemIdCount == 6))
                    .append(" actual=")
                    .append(itemIdCount)
                    .append(" expected=6\n");
            int distinctItemIdCount = countDistinctItemIds(db);
            report.append("password_entries distinct itemId count: ")
                    .append(passFail(distinctItemIdCount == 6))
                    .append(" actual=")
                    .append(distinctItemIdCount)
                    .append(" expected=6\n");
            report.append("itemId unchanged after second run: ")
                    .append(passFail(itemIdSnapshotAfterFirstBackfill.equals(itemIdSnapshotAfterSecondBackfill)))
                    .append('\n');
            report.append('\n').append(historyCoreReport).append('\n');
            String notesHashAfter = notesSha256(db);
            report.append("notes SHA-256 after: ").append(notesHashAfter).append('\n');
            report.append("notes SHA-256 unchanged: ")
                    .append(passFail(notesHashBefore.equals(notesHashAfter)))
                    .append('\n');
            String integrity = singleValue(db, "PRAGMA integrity_check");
            report.append("integrity_check: ")
                    .append(passFail("ok".equalsIgnoreCase(integrity)))
                    .append(" actual=")
                    .append(integrity)
                    .append('\n');
        } catch (Exception e) {
            report.append("ERROR: ").append(e.getClass().getSimpleName()).append(": ")
                    .append(e.getMessage())
                    .append('\n');
        } finally {
            if (db != null) {
                db.close();
            }
        }

        return report.toString();
    }

    private SQLiteDatabase openDatabase(File databaseFile, int flags) {
        return SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(),
                DATABASE_PASSWORD.getBytes(),
                null,
                flags,
                null
        );
    }

    private void appendUserVersion(StringBuilder report, SQLiteDatabase db, int expected) {
        int actual = db.getVersion();
        report.append("user_version: ")
                .append(passFail(actual == expected))
                .append(" actual=")
                .append(actual)
                .append(" expected=")
                .append(expected)
                .append('\n');
    }

    private void appendExpectedCounts(StringBuilder report, SQLiteDatabase db) {
        for (Map.Entry<String, Integer> entry : EXPECTED_COUNTS.entrySet()) {
            int actual = countRows(db, entry.getKey());
            report.append(entry.getKey())
                    .append(" count: ")
                    .append(passFail(actual == entry.getValue()))
                    .append(" actual=")
                    .append(actual)
                    .append(" expected=")
                    .append(entry.getValue())
                    .append('\n');
        }
    }

    private int countRows(SQLiteDatabase db, String tableName) {
        return Integer.parseInt(singleValue(db, "SELECT COUNT(*) FROM " + tableName));
    }

    private int countNonEmptyItemIds(SQLiteDatabase db) {
        return Integer.parseInt(singleValue(db, "SELECT COUNT(*) FROM password_entries WHERE itemId IS NOT NULL AND TRIM(itemId) != ''"));
    }

    private int countDistinctItemIds(SQLiteDatabase db) {
        return Integer.parseInt(singleValue(db, "SELECT COUNT(DISTINCT itemId) FROM password_entries WHERE itemId IS NOT NULL AND TRIM(itemId) != ''"));
    }

    private String runPasswordHistoryCoreTest(SQLiteDatabase db) {
        StringBuilder report = new StringBuilder();
        String marker = "migrationtest-" + UUID.randomUUID();
        String itemId = UUID.randomUUID().toString();
        String passwordA = "test-password-a";
        String passwordB = "test-password-b";
        String passwordC = "test-password-c";
        String testTitle = "history-core-test";
        long now = System.currentTimeMillis();

        db.beginTransaction();
        try {
            int initialHistoryCount = countRows(db, "password_history");
            db.execSQL(
                    "INSERT INTO password_entries (`itemId`, `title`, `websiteDomain`, `appPackageName`, `username`, `password`, `account`, `remark`, `notes`, `lastUsedAt`, `createdAt`, `updatedAt`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{itemId, testTitle, "example.test", "", marker, passwordA, marker, "initial remark", "legacy-note-json-stays-untouched", 0, now, now}
            );
            long entryId = Long.parseLong(singleValue(db, "SELECT last_insert_rowid()"));
            int afterInsertHistoryCount = countRows(db, "password_history");

            db.execSQL(
                    "UPDATE password_entries SET remark = ?, updatedAt = ? WHERE id = ?",
                    new Object[]{"remark-only", now + 1, entryId}
            );
            int afterNonPasswordHistoryCount = countRows(db, "password_history");

            writeManualPasswordHistory(db, entryId, passwordB, now + 2);
            int afterFirstPasswordHistoryCount = countRows(db, "password_history");
            boolean firstOldPasswordMatches = passwordA.equals(singleValue(db, "SELECT oldPassword FROM password_history WHERE entryItemId = ? ORDER BY id DESC LIMIT 1", itemId));

            writeManualPasswordHistory(db, entryId, passwordC, now + 3);
            int afterSecondPasswordHistoryCount = countRows(db, "password_history");
            int distinctHistoryIds = Integer.parseInt(singleValue(db, "SELECT COUNT(DISTINCT historyId) FROM password_history WHERE entryItemId = ?", itemId));
            boolean secondOldPasswordMatches = passwordB.equals(singleValue(db, "SELECT oldPassword FROM password_history WHERE entryItemId = ? ORDER BY id DESC LIMIT 1", itemId));

            report.append("Password history core test\n");
            report.append("new entry itemId non-empty: ").append(passFail(!itemId.trim().isEmpty())).append('\n');
            report.append("history count unchanged after insert: ")
                    .append(passFail(afterInsertHistoryCount == initialHistoryCount))
                    .append(" before=")
                    .append(initialHistoryCount)
                    .append(" after=")
                    .append(afterInsertHistoryCount)
                    .append('\n');
            report.append("history count unchanged after non-password update: ")
                    .append(passFail(afterNonPasswordHistoryCount == initialHistoryCount))
                    .append(" actual=")
                    .append(afterNonPasswordHistoryCount)
                    .append('\n');
            report.append("first password change history +1: ")
                    .append(passFail(afterFirstPasswordHistoryCount == initialHistoryCount + 1))
                    .append(" actual=")
                    .append(afterFirstPasswordHistoryCount)
                    .append('\n');
            report.append("first oldPassword SHA-256 matches test value: ")
                    .append(passFail(firstOldPasswordMatches))
                    .append(" sha256=")
                    .append(sha256(passwordA))
                    .append('\n');
            report.append("second password change history +2: ")
                    .append(passFail(afterSecondPasswordHistoryCount == initialHistoryCount + 2))
                    .append(" actual=")
                    .append(afterSecondPasswordHistoryCount)
                    .append('\n');
            report.append("second oldPassword SHA-256 matches test value: ")
                    .append(passFail(secondOldPasswordMatches))
                    .append(" sha256=")
                    .append(sha256(passwordB))
                    .append('\n');
            report.append("historyId distinct count: ")
                    .append(passFail(distinctHistoryIds == 2))
                    .append(" actual=")
                    .append(distinctHistoryIds)
                    .append(" expected=2\n");

            db.execSQL("DELETE FROM password_history WHERE entryItemId = ?", new Object[]{itemId});
            db.execSQL("DELETE FROM password_entries WHERE id = ?", new Object[]{entryId});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            report.append("Password history core test ERROR: ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append('\n');
        } finally {
            db.endTransaction();
        }
        return report.toString();
    }

    private void writeManualPasswordHistory(SQLiteDatabase db, long entryId, String newPassword, long updatedAt) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT itemId, password FROM password_entries WHERE id = ? LIMIT 1", new String[]{Long.toString(entryId)});
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("Test entry missing");
            }
            String entryItemId = cursor.getString(0);
            String oldPassword = cursor.getString(1);
            if (oldPassword != null && oldPassword.equals(newPassword)) {
                return;
            }
            db.execSQL(
                    "INSERT INTO password_history (`historyId`, `entryItemId`, `oldPassword`, `createdAt`, `source`, `deviceId`, `note`) VALUES (?, ?, ?, ?, ?, NULL, NULL)",
                    new Object[]{UUID.randomUUID().toString(), entryItemId, oldPassword == null ? "" : oldPassword, System.currentTimeMillis(), "manual_edit"}
            );
            db.execSQL(
                    "UPDATE password_entries SET password = ?, updatedAt = ? WHERE id = ?",
                    new Object[]{newPassword, updatedAt, entryId}
            );
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean tableExists(SQLiteDatabase db, String tableName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = ? AND name = ? LIMIT 1",
                    new String[]{"table", tableName}
            );
            return cursor.moveToFirst();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", new String[0]);
            while (cursor.moveToNext()) {
                if (columnName.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String notesSha256(SQLiteDatabase db) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT id, notes FROM password_entries ORDER BY id", new String[0]);
            while (cursor.moveToNext()) {
                updateDigest(digest, Long.toString(cursor.getLong(0)));
                updateDigest(digest, "\u001f");
                if (!cursor.isNull(1)) {
                    updateDigest(digest, cursor.getString(1));
                }
                updateDigest(digest, "\u001e");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return toHex(digest.digest());
    }

    private String itemIdSnapshotSha256(SQLiteDatabase db) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT id, itemId FROM password_entries ORDER BY id", new String[0]);
            while (cursor.moveToNext()) {
                updateDigest(digest, Long.toString(cursor.getLong(0)));
                updateDigest(digest, "\u001f");
                if (!cursor.isNull(1)) {
                    updateDigest(digest, cursor.getString(1));
                }
                updateDigest(digest, "\u001e");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return toHex(digest.digest());
    }

    private void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private String singleValue(SQLiteDatabase db, String sql) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, new String[0]);
            if (!cursor.moveToFirst()) {
                return "";
            }
            return cursor.getString(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String singleValue(SQLiteDatabase db, String sql, String arg) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, new String[]{arg});
            if (!cursor.moveToFirst()) {
                return "";
            }
            return cursor.getString(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String passFail(boolean pass) {
        return pass ? "PASS" : "FAIL";
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }
}
