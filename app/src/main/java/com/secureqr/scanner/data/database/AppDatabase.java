package com.secureqr.scanner.data.database;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;
import com.secureqr.scanner.data.model.PasswordHistory;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.model.PasswordNote;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.model.VaultAttachment;
import com.secureqr.scanner.data.model.TrashItem;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.DatabaseOpenState;
import com.secureqr.scanner.security.SecurityAuditLog;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Database(entities = {ScanRecord.class, PasswordEntry.class, PasswordGenerationRecord.class, OtpToken.class, PasswordNote.class, PasswordHistory.class, PasswordGroup.class, VaultItem.class, VaultAttachment.class, TrashItem.class}, version = 13, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "scanner.db";
    private static volatile AppDatabase INSTANCE;

    public abstract RecordDao recordDao();
    public abstract PasswordEntryDao passwordEntryDao();
    public abstract PasswordHistoryDao passwordHistoryDao();
    public abstract PasswordGroupDao passwordGroupDao();
    public abstract PasswordNoteDao passwordNoteDao();
    public abstract PasswordGenerationDao passwordGenerationDao();
    public abstract OtpTokenDao otpTokenDao();
    public abstract VaultItemDao vaultItemDao();
    public abstract VaultAttachmentDao vaultAttachmentDao();
    public abstract TrashItemDao trashItemDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `password_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `password` TEXT, `account` TEXT, `remark` TEXT, `createdAt` INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `records` ADD COLUMN `source` TEXT");
            database.execSQL("ALTER TABLE `records` ADD COLUMN `thumbnailBase64` TEXT");
            database.execSQL("UPDATE `records` SET `source` = 'SCAN' WHERE `source` IS NULL");
            database.execSQL("CREATE TABLE IF NOT EXISTS `password_generation_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `password` TEXT, `length` INTEGER NOT NULL, `configSummary` TEXT, `createdAt` INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `otp_tokens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountName` TEXT, `issuer` TEXT, `secret` TEXT, `digits` INTEGER NOT NULL, `period` INTEGER NOT NULL, `algorithm` TEXT, `pinned` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `password_generation_records` ADD COLUMN `remark` TEXT");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `title` TEXT");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `websiteDomain` TEXT");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `appPackageName` TEXT");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `username` TEXT");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `notes` TEXT");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `lastUsedAt` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE `password_entries` SET `title` = `remark` WHERE `title` IS NULL");
            database.execSQL("UPDATE `password_entries` SET `username` = `account` WHERE `username` IS NULL");
            database.execSQL("UPDATE `password_entries` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `password_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT, `title` TEXT, `primaryText` TEXT, `secondaryText` TEXT, `contentJson` TEXT, `sourcePasswordEntryId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `itemId` TEXT");
            database.execSQL("CREATE TABLE IF NOT EXISTS `password_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `historyId` TEXT NOT NULL, `entryItemId` TEXT NOT NULL, `oldPassword` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `source` TEXT NOT NULL, `deviceId` TEXT, `note` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_password_history_historyId` ON `password_history` (`historyId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_entryItemId` ON `password_history` (`entryItemId`)");
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `password_groups` (`id` TEXT NOT NULL, `name` TEXT, `sortOrder` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `groupId` TEXT");
            database.execSQL("INSERT OR IGNORE INTO `password_groups` (`id`, `name`, `sortOrder`, `isDefault`, `createdAt`, `updatedAt`) VALUES ('" + PasswordGroup.DEFAULT_ID + "', '" + PasswordGroup.DEFAULT_NAME + "', 0, 1, strftime('%s','now') * 1000, strftime('%s','now') * 1000)");
            database.execSQL("UPDATE `password_entries` SET `groupId` = '" + PasswordGroup.DEFAULT_ID + "' WHERE `groupId` IS NULL OR TRIM(`groupId`) = ''");
        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `vault_items` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `title` TEXT NOT NULL, `fieldsJson` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdTime` INTEGER NOT NULL, `updatedTime` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `vault_attachments` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `filename` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `encryptedPath` TEXT NOT NULL, `hash` TEXT NOT NULL, `size` INTEGER NOT NULL, `createdTime` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_attachments_itemId` ON `vault_attachments` (`itemId`)");
            database.execSQL("INSERT OR IGNORE INTO `vault_items` (`id`,`type`,`category`,`title`,`fieldsJson`,`notes`,`createdTime`,`updatedTime`) " +
                    "SELECT lower(hex(randomblob(4)))||'-'||lower(hex(randomblob(2)))||'-4'||substr(lower(hex(randomblob(2))),2)||'-a'||substr(lower(hex(randomblob(2))),2)||'-'||lower(hex(randomblob(6))), " +
                    "CASE type WHEN 'software_license' THEN 'SOFTWARE_LICENSE' WHEN 'server' THEN 'SSH_KEY' WHEN 'identity' THEN 'OTHER_ID' WHEN 'bank_card' THEN 'BANK_CARD' WHEN 'secure_note' THEN 'SECURE_FILE' ELSE 'CUSTOM' END, " +
                    "CASE type WHEN 'software_license' THEN 'KEYS_LICENSES' WHEN 'server' THEN 'KEYS_LICENSES' WHEN 'identity' THEN 'IDENTITY' WHEN 'bank_card' THEN 'FINANCIAL' WHEN 'secure_note' THEN 'SECURE_FILES' ELSE 'CUSTOM' END, " +
                    "COALESCE(NULLIF(title,''),NULLIF(primaryText,''),'鏈懡鍚嶈祫鏂?), COALESCE(NULLIF(contentJson,''),'{}'), COALESCE(secondaryText,''), createdAt, updatedAt FROM password_notes WHERE type IS NULL OR type <> 'login'");
        }
    };

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `trash_items` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `originalId` TEXT NOT NULL, `title` TEXT NOT NULL, `payload` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        }
    };

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `otpId` INTEGER");
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `password_generation_records` ADD COLUMN `source` TEXT");
            database.execSQL("ALTER TABLE `password_generation_records` ADD COLUMN `website` TEXT");
            database.execSQL("ALTER TABLE `password_generation_records` ADD COLUMN `account` TEXT");
            database.execSQL("ALTER TABLE `password_generation_records` ADD COLUMN `linkedPasswordEntryId` INTEGER");
            database.execSQL("UPDATE `password_generation_records` SET `source` = 'GENERATOR' WHERE `source` IS NULL");
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `otp_tokens` ADD COLUMN `itemId` TEXT");
            database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `otpItemId` TEXT");
            database.execSQL("ALTER TABLE `password_generation_records` ADD COLUMN `itemId` TEXT");
            database.execSQL("ALTER TABLE `password_generation_records` ADD COLUMN `linkedPasswordEntryItemId` TEXT");
            backfillStableBusinessIds(database);
            database.execSQL("UPDATE `password_groups` SET `name`='' WHERE `id`='" + PasswordGroup.DEFAULT_ID + "'");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_password_entries_itemId` ON `password_entries` (`itemId`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_otp_tokens_itemId` ON `otp_tokens` (`itemId`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_password_generation_records_itemId` ON `password_generation_records` (`itemId`)");
        }
    };

    private static final RoomDatabase.Callback ITEM_ID_BACKFILL_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onOpen(SupportSQLiteDatabase db) {
            ensureDefaultPasswordGroup(db);
            backfillMissingPasswordEntryGroupIds(db);
            backfillMissingPasswordEntryItemIds(db);
            backfillStableBusinessIds(db);
        }
    };

    private static void ensureDefaultPasswordGroup(SupportSQLiteDatabase database) {
        database.execSQL("INSERT OR IGNORE INTO `password_groups` (`id`, `name`, `sortOrder`, `isDefault`, `createdAt`, `updatedAt`) VALUES ('" + PasswordGroup.DEFAULT_ID + "', '" + PasswordGroup.DEFAULT_NAME + "', 0, 1, strftime('%s','now') * 1000, strftime('%s','now') * 1000)");
        database.execSQL("UPDATE `password_groups` SET `name`='' WHERE `id`='" + PasswordGroup.DEFAULT_ID + "'");
    }

    private static void backfillMissingPasswordEntryGroupIds(SupportSQLiteDatabase database) {
        database.beginTransaction();
        Cursor cursor = null;
        try {
            cursor = database.query("SELECT `id` FROM `password_entries` WHERE `groupId` IS NULL OR TRIM(`groupId`) = ''");
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                database.execSQL(
                        "UPDATE `password_entries` SET `groupId` = ? WHERE `id` = ? AND (`groupId` IS NULL OR TRIM(`groupId`) = '')",
                        new Object[]{PasswordGroup.DEFAULT_ID, id}
                );
            }
            database.setTransactionSuccessful();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            database.endTransaction();
        }
    }

    public static void backfillMissingPasswordEntryItemIds(SupportSQLiteDatabase database) {
        database.beginTransaction();
        Cursor cursor = null;
        try {
            cursor = database.query("SELECT `id` FROM `password_entries` WHERE `itemId` IS NULL OR TRIM(`itemId`) = ''");
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                database.execSQL(
                        "UPDATE `password_entries` SET `itemId` = ? WHERE `id` = ? AND (`itemId` IS NULL OR TRIM(`itemId`) = '')",
                        new Object[]{UUID.randomUUID().toString(), id}
                );
            }
            database.setTransactionSuccessful();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            database.endTransaction();
        }
    }

    private static void backfillStableBusinessIds(SupportSQLiteDatabase database) {
        backfillUuidColumn(database, "otp_tokens", "itemId");
        backfillUuidColumn(database, "password_generation_records", "itemId");
        database.execSQL("UPDATE `password_entries` SET `otpItemId`=(SELECT `itemId` FROM `otp_tokens` WHERE `otp_tokens`.`id`=`password_entries`.`otpId`) WHERE `otpId` IS NOT NULL AND (`otpItemId` IS NULL OR TRIM(`otpItemId`)='')");
        database.execSQL("UPDATE `password_generation_records` SET `linkedPasswordEntryItemId`=(SELECT `itemId` FROM `password_entries` WHERE `password_entries`.`id`=`password_generation_records`.`linkedPasswordEntryId`) WHERE `linkedPasswordEntryId` IS NOT NULL AND (`linkedPasswordEntryItemId` IS NULL OR TRIM(`linkedPasswordEntryItemId`)='')");
    }

    private static void backfillUuidColumn(SupportSQLiteDatabase database, String table, String column) {
        Cursor cursor = database.query("SELECT `id` FROM `" + table + "` WHERE `" + column + "` IS NULL OR TRIM(`" + column + "`) = ''");
        try { while (cursor.moveToNext()) database.execSQL("UPDATE `" + table + "` SET `" + column + "`=? WHERE `id`=?", new Object[]{UUID.randomUUID().toString(), cursor.getLong(0)}); }
        finally { cursor.close(); }
    }

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();
                    boolean existingDatabase = databaseFileExists(appContext);
                    INSTANCE = buildDatabase(appContext, DatabaseKeyManager.getDatabaseKey(appContext));
                    try {
                        INSTANCE.getOpenHelper().getWritableDatabase();
                        DatabaseKeyManager.markDatabaseOpened(appContext);
                        SecurityAuditLog.record(appContext, "Database opened with dynamic key v1", true);
                    } catch (RuntimeException openError) {
                        closeInstance();
                        DatabaseOpenState state = classifyOpenFailure(openError, null);
                        if (isDebuggable(appContext) && existingDatabase && resetDevelopmentDatabase(appContext)) {
                            INSTANCE = buildDatabase(appContext, DatabaseKeyManager.getDatabaseKey(appContext));
                            INSTANCE.getOpenHelper().getWritableDatabase();
                            DatabaseKeyManager.markDatabaseOpened(appContext);
                            SecurityAuditLog.record(appContext, "Development database reinitialized with dynamic key v1", true);
                            return INSTANCE;
                        }
                        DatabaseKeyManager.markDatabaseKeyError(appContext, state, openError);
                        SecurityAuditLog.record(appContext, (existingDatabase ? "Database open failed: " : "Database create failed: ") + state.name(), false);
                        throw databaseOpenException(appContext, state, openError);
                    }
                }
            }
        }
        return INSTANCE;
    }

    private static AppDatabase buildDatabase(Context context, String databaseKey) {
        if (databaseKey == null || databaseKey.isEmpty()) {
            throw new IllegalStateException("Secure vault is not initialized. Database key is unavailable.");
        }
        SupportOpenHelperFactory factory = new SupportOpenHelperFactory(databaseKey.getBytes(StandardCharsets.UTF_8));
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .addCallback(ITEM_ID_BACKFILL_CALLBACK)
                .build();
    }

    private static boolean databaseFileExists(Context context) {
        File db = context.getDatabasePath(DATABASE_NAME);
        return db != null && db.exists() && db.length() > 0;
    }

    private static DatabaseOpenState classifyOpenFailure(RuntimeException primary, RuntimeException secondary) {
        String text = ((primary == null ? "" : primary.getClass().getSimpleName() + " " + primary.getMessage())
                + " " + (secondary == null ? "" : secondary.getClass().getSimpleName() + " " + secondary.getMessage()))
                .toLowerCase(java.util.Locale.US);
        if (text.contains("migration") || text.contains("room cannot verify") || text.contains("schema")) {
            return DatabaseOpenState.DATABASE_MIGRATION_ERROR;
        }
        if (text.contains("corrupt") || text.contains("malformed") || text.contains("not a database")) {
            return DatabaseOpenState.DATABASE_CORRUPTED;
        }
        if (text.contains("permission") || text.contains("access") || text.contains("readonly")
                || text.contains("read-only") || text.contains("i/o") || text.contains("ioexception")) {
            return DatabaseOpenState.DATABASE_ACCESS_ERROR;
        }
        return DatabaseOpenState.DATABASE_KEY_ERROR;
    }

    private static IllegalStateException databaseOpenException(Context context, DatabaseOpenState state, RuntimeException cause) {
        return new IllegalStateException(userMessageFor(context, state), cause);
    }

    private static String userMessageFor(Context context, DatabaseOpenState state) {
        if (state == DatabaseOpenState.DATABASE_MIGRATION_ERROR) {
            return context.getString(com.secureqr.scanner.R.string.database_migration_failed);
        }
        if (state == DatabaseOpenState.DATABASE_CORRUPTED) {
            return context.getString(com.secureqr.scanner.R.string.database_corrupted);
        }
        if (state == DatabaseOpenState.DATABASE_ACCESS_ERROR) {
            return context.getString(com.secureqr.scanner.R.string.database_access_failed);
        }
        return context.getString(com.secureqr.scanner.R.string.database_open_failed);
    }

    private static boolean resetDevelopmentDatabase(Context context) {
        if (!isDebuggable(context)) return false;
        context.deleteDatabase(DATABASE_NAME);
        File database = context.getDatabasePath(DATABASE_NAME);
        return database == null || !database.exists();
    }

    private static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static void closeInstance() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}

