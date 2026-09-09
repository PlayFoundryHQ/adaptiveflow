package io.github.playfoundryhq.adaptiveflow

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.playfoundryhq.adaptiveflow.di.AppContainer

class AdaptiveFlowApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Loads the font/resource assets PdfBox needs for text extraction.
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
