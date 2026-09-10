package io.github.playfoundryhq.adaptiveflow.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.playfoundryhq.adaptiveflow.BuildConfig
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.playfoundryhq.adaptiveflow.data.dao.StudyDao
import io.github.playfoundryhq.adaptiveflow.data.model.ChatLog
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard
import io.github.playfoundryhq.adaptiveflow.data.model.Progress

/**
 * `exportSchema = true` — the schema JSON is committed under `app/schemas/`.
 * Every version bump MUST ship a real [androidx.room.migration.Migration];
 * destructive fallback is allowed in **debug only** so dev iteration is cheap
 * without ever wiping a real user's decks / SRS progress on an update.
 */
@Database(
    entities = [Deck::class, Flashcard::class, ChatLog::class, Progress::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2: gamification counters moved out of SharedPreferences. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `progress` (" +
                        "`id` INTEGER NOT NULL, `xp` INTEGER NOT NULL, `streak` INTEGER NOT NULL, " +
                        "`lastStudyDate` TEXT NOT NULL, `seeded` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `progress` (`id`,`xp`,`streak`,`lastStudyDate`,`seeded`) " +
                        "VALUES (0, 0, 0, '', 0)"
                )
            }
        }

        /**
         * v2 → v3: entity id / foreign-key columns went `Int` → `Long` in Kotlin.
         * SQLite stores every `INTEGER` column as a 64-bit value already, so the
         * on-disk schema is byte-identical and there is nothing to migrate — this
         * only exists so Room accepts the version bump.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op: INTEGER is INTEGER */ }
        }

        // Register migrations here as the schema evolves.
        private val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2, MIGRATION_2_3)

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "adaptive_flow_database",
                ).apply {
                    addMigrations(*MIGRATIONS)
                    if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true)
                }.build().also { INSTANCE = it }
            }
    }
}
