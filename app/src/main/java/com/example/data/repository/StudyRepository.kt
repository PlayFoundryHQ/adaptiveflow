package com.example.data.repository

import com.example.data.dao.StudyDao
import com.example.data.model.Deck
import com.example.data.model.Flashcard
import com.example.data.model.ChatLog
import kotlinx.coroutines.flow.Flow

class StudyRepository(private val studyDao: StudyDao) {
    val allDecksFlow: Flow<List<Deck>> = studyDao.getAllDecksFlow()
    val allDecksWithCardsFlow: Flow<List<com.example.data.model.DeckWithCards>> = studyDao.getAllDecksWithCardsFlow()

    suspend fun getDeckById(id: Int): Deck? = studyDao.getDeckById(id)

    suspend fun insertDeck(deck: Deck): Long = studyDao.insertDeck(deck)

    suspend fun updateDeck(deck: Deck) = studyDao.updateDeck(deck)

    suspend fun deleteDeck(deck: Deck) = studyDao.deleteDeck(deck)

    fun getFlashcardsForDeckFlow(deckId: Int): Flow<List<Flashcard>> =
        studyDao.getFlashcardsForDeckFlow(deckId)

    suspend fun getFlashcardsForDeck(deckId: Int): List<Flashcard> =
        studyDao.getFlashcardsForDeck(deckId)

    suspend fun getFlashcardById(id: Int): Flashcard? = studyDao.getFlashcardById(id)

    suspend fun insertFlashcard(flashcard: Flashcard): Long = studyDao.insertFlashcard(flashcard)

    suspend fun insertFlashcards(flashcards: List<Flashcard>) = studyDao.insertFlashcards(flashcards)

    suspend fun updateFlashcard(flashcard: Flashcard) = studyDao.updateFlashcard(flashcard)

    suspend fun deleteFlashcard(flashcard: Flashcard) = studyDao.deleteFlashcard(flashcard)

    fun getChatLogsForDeckFlow(deckId: Int): Flow<List<ChatLog>> =
        studyDao.getChatLogsForDeckFlow(deckId)

    suspend fun getChatLogsForDeck(deckId: Int): List<ChatLog> =
        studyDao.getChatLogsForDeck(deckId)

    suspend fun insertChatLog(chatLog: ChatLog): Long = studyDao.insertChatLog(chatLog)

    suspend fun clearChatLogsForDeck(deckId: Int) = studyDao.clearChatLogsForDeck(deckId)
}
