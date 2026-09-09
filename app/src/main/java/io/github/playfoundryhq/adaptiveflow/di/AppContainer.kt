package io.github.playfoundryhq.adaptiveflow.di

import android.app.Application
import io.github.playfoundryhq.adaptiveflow.data.ai.AiClient
import io.github.playfoundryhq.adaptiveflow.data.database.AppDatabase
import io.github.playfoundryhq.adaptiveflow.data.repository.StudyRepository
import io.github.playfoundryhq.adaptiveflow.data.settings.SettingsStore
import io.github.playfoundryhq.adaptiveflow.tts.TtsController

/**
 * Manual DI — one place that builds the app's singletons, created in
 * [io.github.playfoundryhq.adaptiveflow.AdaptiveFlowApp.onCreate] and handed to
 * the ViewModel by its factory. No Hilt / annotation processing; the graph is
 * small enough that a plain container is clearer and has no build-time cost.
 */
class AppContainer(private val app: Application) {
    // Everything is lazy so Application.onCreate stays cheap — the DB build,
    // EncryptedSharedPreferences/Tink init, OkHttp client and TextToSpeech bind
    // all happen on first use (from the ViewModel), not on the cold-start path.
    val database: AppDatabase by lazy { AppDatabase.getDatabase(app) }
    val repository: StudyRepository by lazy { StudyRepository(database.studyDao()) }
    val settings: SettingsStore by lazy { SettingsStore(app) }
    val aiClient: AiClient by lazy { AiClient() }
    val tts: TtsController by lazy { TtsController(app, settings) }
}
