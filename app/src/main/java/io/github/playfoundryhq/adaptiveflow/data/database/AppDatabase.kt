package io.github.playfoundryhq.adaptiveflow.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.playfoundryhq.adaptiveflow.BuildConfig
import io.github.playfoundryhq.adaptiveflow.data.dao.StudyDao
import io.github.playfoundryhq.adaptiveflow.data.model.ChatLog
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard

/**
 * `exportSchema = true` — the schema JSON is committed under `app/schemas/`.
 * Every version bump MUST ship a real [androidx.room.migration.Migration];
 * destructive fallback is allowed in **debug only** so dev iteration is cheap
 * without ever wiping a real user's decks / SRS progress on an update.
 */
@Database(entities = [Deck::class, Flashcard::class, ChatLog::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Register migrations here as the schema evolves.
        private val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

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
