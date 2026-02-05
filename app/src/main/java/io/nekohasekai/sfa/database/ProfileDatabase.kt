package io.nekohasekai.sfa.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, ServerTestResult::class],
    version = 3,
    exportSchema = true,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): Profile.Dao

    abstract fun serverTestResultDao(): ServerTestResultDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE profiles ADD COLUMN icon TEXT DEFAULT NULL")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `server_test_results` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `serverTag` TEXT NOT NULL,
                            `profileId` INTEGER NOT NULL,
                            `is_success` INTEGER NOT NULL,
                            `latency_ms` INTEGER,
                            `error_type` TEXT,
                            `error_message` TEXT,
                            `tested_at` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_server_test_results_serverTag_profileId` " +
                            "ON `server_test_results` (`serverTag`, `profileId`)",
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_server_test_results_profileId_is_success` " +
                            "ON `server_test_results` (`profileId`, `is_success`)",
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_server_test_results_tested_at` " +
                            "ON `server_test_results` (`tested_at`)",
                    )
                }
            }
    }
}
