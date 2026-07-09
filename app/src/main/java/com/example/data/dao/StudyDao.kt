package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.data.model.Deck
import com.example.data.model.Flashcard
import com.example.data.model.ChatLog
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    // --- Decks ---
    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun getAllDecksFlow(): Flow<List<Deck>>

    @androidx.room.Transaction
    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun getAllDecksWithCardsFlow(): Flow<List<com.example.data.model.DeckWithCards>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: Int): Deck?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: Deck): Long

    @Update
    suspend fun updateDeck(deck: Deck)

    @Delete
    suspend fun deleteDeck(deck: Deck)

    // --- Flashcards ---
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId")
    fun getFlashcardsForDeckFlow(deckId: Int): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId")
    suspend fun getFlashcardsForDeck(deckId: Int): List<Flashcard>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardById(id: Int): Flashcard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: Flashcard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<Flashcard>)

    @Update
    suspend fun updateFlashcard(flashcard: Flashcard)

    @Delete
    suspend fun deleteFlashcard(flashcard: Flashcard)

    // --- Chat Logs ---
    @Query("SELECT * FROM chat_logs WHERE deckId = :deckId ORDER BY timestamp ASC")
    fun getChatLogsForDeckFlow(deckId: Int): Flow<List<ChatLog>>

    @Query("SELECT * FROM chat_logs WHERE deckId = :deckId ORDER BY timestamp ASC")
    suspend fun getChatLogsForDeck(deckId: Int): List<ChatLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatLog(chatLog: ChatLog): Long

    @Query("DELETE FROM chat_logs WHERE deckId = :deckId")
    suspend fun clearChatLogsForDeck(deckId: Int)
}
