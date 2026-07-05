package com.secureqr.scanner.data.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGenerationRecord;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.ScanRecord;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

@Database(entities = {ScanRecord.class, PasswordEntry.class, PasswordGenerationRecord.class, OtpToken.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String LOCAL_DATABASE_PASSWORD = "DefaultKey2024";
    private static volatile AppDatabase INSTANCE;

    public abstract RecordDao recordDao();
    public abstract PasswordEntryDao passwordEntryDao();
    public abstract PasswordGenerationDao passwordGenerationDao();
    public abstract OtpTokenDao otpTokenDao();

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

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    SupportOpenHelperFactory factory = new SupportOpenHelperFactory(LOCAL_DATABASE_PASSWORD.getBytes());
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "scanner.db")
                            .openHelperFactory(factory)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void closeInstance() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}

