package io.github.playfoundryhq.adaptiveflow.data.repository

import io.github.playfoundryhq.adaptiveflow.data.dao.StudyDao
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard
import io.github.playfoundryhq.adaptiveflow.data.model.ChatLog
import io.github.playfoundryhq.adaptiveflow.data.model.DeckWithCards
import io.github.playfoundryhq.adaptiveflow.data.model.Progress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class StudyRepository(private val studyDao: StudyDao) {
    val allDecksFlow = studyDao.getAllDecksFlow()
    val allDecksWithCardsFlow: Flow<List<DeckWithCards>> = studyDao.getAllDecksWithCardsFlow()

    /** One-shot read of all decks (used for the first-run seed guard). */
    suspend fun allDecksFlowSnapshot(): List<Deck> = studyDao.getAllDecksFlow().first()

    suspend fun getDeckById(id: Long): Deck? = studyDao.getDeckById(id)

    suspend fun insertDeck(deck: Deck): Long = studyDao.insertDeck(deck)

    suspend fun updateDeck(deck: Deck) = studyDao.updateDeck(deck)

    suspend fun deleteDeck(deck: Deck) = studyDao.deleteDeck(deck)

    fun getFlashcardsForDeckFlow(deckId: Long): Flow<List<Flashcard>> =
        studyDao.getFlashcardsForDeckFlow(deckId)

    suspend fun getFlashcardsForDeck(deckId: Long): List<Flashcard> =
        studyDao.getFlashcardsForDeck(deckId)

    suspend fun getFlashcardById(id: Long): Flashcard? = studyDao.getFlashcardById(id)

    suspend fun insertFlashcard(flashcard: Flashcard): Long = studyDao.insertFlashcard(flashcard)

    suspend fun insertFlashcards(flashcards: List<Flashcard>) = studyDao.insertFlashcards(flashcards)

    suspend fun updateFlashcard(flashcard: Flashcard) = studyDao.updateFlashcard(flashcard)

    suspend fun deleteFlashcard(flashcard: Flashcard) = studyDao.deleteFlashcard(flashcard)

    fun getChatLogsForDeckFlow(deckId: Long): Flow<List<ChatLog>> =
        studyDao.getChatLogsForDeckFlow(deckId)

    suspend fun getChatLogsForDeck(deckId: Long): List<ChatLog> =
        studyDao.getChatLogsForDeck(deckId)

    suspend fun insertChatLog(chatLog: ChatLog): Long = studyDao.insertChatLog(chatLog)

    suspend fun clearChatLogsForDeck(deckId: Long) = studyDao.clearChatLogsForDeck(deckId)

    // ---- Progress (gamification) ----

    val progressFlow: Flow<Progress> = studyDao.progressFlow().map { it ?: Progress() }

    suspend fun getProgress(): Progress = studyDao.getProgress() ?: Progress()

    suspend fun updateProgress(transform: (Progress) -> Progress) {
        studyDao.upsertProgress(transform(getProgress()))
    }
}
