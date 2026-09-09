package io.github.playfoundryhq.adaptiveflow

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AdaptiveFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Loads the font/resource assets PdfBox needs for text extraction.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
