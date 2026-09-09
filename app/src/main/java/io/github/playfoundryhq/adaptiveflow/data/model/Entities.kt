package io.github.playfoundryhq.adaptiveflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId"])]
)
data class Flashcard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deckId: Int,
    val front: String,  // Word to learn
    val back: String,   // Definition / translation
    val notes: String? = null,
    val easeFactor: Float = 2.5f,
    val interval: Int = 1, // Days interval
    val repetitions: Int = 0,
    val nextReview: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_logs",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId"])]
)
data class ChatLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deckId: Int,
    val flashcardId: Int? = null,
    val sender: String, // "user" or "ai"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DeckWithCards(
    @androidx.room.Embedded val deck: Deck,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "deckId"
    )
    val flashcards: List<Flashcard>
)

/**
 * Single-row table (`id` is always 0) holding gamification + one-time-setup
 * state. Was in SharedPreferences; moved to Room in schema v2 so XP / streak
 * updates are transactional with the study writes that trigger them.
 */
@Entity(tableName = "progress")
data class Progress(
    @PrimaryKey val id: Int = 0,
    val xp: Int = 0,
    val streak: Int = 0,
    val lastStudyDate: String = "",
    val seeded: Boolean = false,
)
