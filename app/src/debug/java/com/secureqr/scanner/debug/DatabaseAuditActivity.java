package com.secureqr.scanner.debug;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DatabaseAuditActivity extends Activity {
    private static final String TAG = "KeyScanDbAudit";
    private static final String DATABASE_PASSWORD = "";
    private static final String AUDIT_DIR_NAME = "db-audit";
    private static final String DB_FILE_NAME = "scanner.db";
    private static final List<String> AUDITED_TABLES = Arrays.asList(
            "records",
            "password_entries",
            "password_generation_records",
            "otp_tokens",
            "password_notes"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView output = new TextView(this);
        output.setTextIsSelectable(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        output.setPadding(padding, padding, padding, padding);
        setContentView(output);

        String report = runAudit();
        output.setText(report);
        Log.i(TAG, report);
    }

    private String runAudit() {
        StringBuilder report = new StringBuilder();
        report.append("KeyScan Debug Database Audit\n");
        report.append("Sensitive values are not queried or printed.\n\n");
        if (DATABASE_PASSWORD.isEmpty()) {
            report.append("Legacy fixed database password has been removed from debug builds.\n");
            report.append("This audit screen no longer opens legacy fixed-key databases.\n");
            return report.toString();
        }

        File databaseFile = resolveDatabaseFile();
        File walFile = new File(databaseFile.getParentFile(), DB_FILE_NAME + "-wal");
        File shmFile = new File(databaseFile.getParentFile(), DB_FILE_NAME + "-shm");

        report.append("scanner.db exists: ").append(databaseFile.exists()).append('\n');
        report.append("scanner.db-wal exists: ").append(walFile.exists()).append('\n');
        report.append("scanner.db-shm exists: ").append(shmFile.exists()).append("\n\n");

        if (!databaseFile.exists()) {
            report.append("Audit copy not found\n");
            return report.toString();
        }

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(),
                    DATABASE_PASSWORD.getBytes(),
                    null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    null
            );
            db.rawExecSQL("PRAGMA query_only = ON");

            report.append("PRAGMA user_version: ").append(singleValue(db, "PRAGMA user_version")).append("\n\n");
            for (String tableName : AUDITED_TABLES) {
                appendTableAudit(report, db, tableName);
            }
            report.append("password_history exists: ")
                    .append(tableExists(db, "password_history"))
                    .append("\n\n");
            report.append("PRAGMA integrity_check: ")
                    .append(singleValue(db, "PRAGMA integrity_check"))
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

    private File resolveDatabaseFile() {
        return new File(new File(getFilesDir(), AUDIT_DIR_NAME), DB_FILE_NAME);
    }

    private void appendTableAudit(StringBuilder report, SQLiteDatabase db, String tableName) {
        report.append(tableName).append(" schema:\n");
        if (!tableExists(db, tableName)) {
            report.append("  MISSING\n");
            report.append(tableName).append(" count: table missing\n\n");
            return;
        }

        Cursor schema = null;
        try {
            schema = db.rawQuery("PRAGMA table_info(" + tableName + ")", new String[0]);
            while (schema.moveToNext()) {
                report.append("  ")
                        .append(schema.getString(schema.getColumnIndexOrThrow("name")))
                        .append(" ")
                        .append(schema.getString(schema.getColumnIndexOrThrow("type")));
                if (schema.getInt(schema.getColumnIndexOrThrow("notnull")) == 1) {
                    report.append(" NOT NULL");
                }
                if (schema.getInt(schema.getColumnIndexOrThrow("pk")) == 1) {
                    report.append(" PRIMARY KEY");
                }
                String defaultValue = schema.getString(schema.getColumnIndexOrThrow("dflt_value"));
                if (defaultValue != null) {
                    report.append(" DEFAULT ").append(defaultValue);
                }
                report.append('\n');
            }
        } finally {
            if (schema != null) {
                schema.close();
            }
        }

        report.append(tableName).append(" count: ")
                .append(singleValue(db, "SELECT COUNT(*) FROM " + tableName))
                .append("\n\n");
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
}
