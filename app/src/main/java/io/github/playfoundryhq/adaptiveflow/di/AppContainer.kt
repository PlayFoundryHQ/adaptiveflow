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
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.getDatabase(app)
    val repository: StudyRepository = StudyRepository(database.studyDao())
    val settings: SettingsStore = SettingsStore(app)
    val aiClient: AiClient = AiClient()
    val tts: TtsController = TtsController(app, settings)
}
