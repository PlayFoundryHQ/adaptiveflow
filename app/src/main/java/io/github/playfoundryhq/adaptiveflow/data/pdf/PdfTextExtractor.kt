package io.github.playfoundryhq.adaptiveflow.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.DiagnosticLogger
import java.io.File

/**
 * Page-by-page text extraction from a PDF, using PdfBox-Android (Apache-2.0).
 * Replaces the AGPL-licensed iText 5.
 *
 * [com.tom_roush.pdfbox.util.PDFBoxResourceLoader.init] must have been called
 * once at process start (done in `AdaptiveFlowApp`).
 */
object PdfTextExtractor {

    /**
     * @return one string per page that had extractable text (empty pages dropped),
     *   or an empty list if the PDF is image-only / unreadable.
     */
    fun extractPages(file: File, onProgress: (page: Int, total: Int) -> Unit = { _, _ -> }): List<String> {
        val pages = mutableListOf<String>()
        try {
            PDDocument.load(file).use { doc ->
                val total = doc.numberOfPages
                val stripper = PDFTextStripper()
                for (p in 1..total) {
                    onProgress(p, total)
                    try {
                        stripper.startPage = p
                        stripper.endPage = p
                        val text = stripper.getText(doc)
                        if (text.isNotBlank()) pages.add(text)
                    } catch (e: Exception) {
                        DiagnosticLogger.e("PdfTextExtractor", "page $p failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.e("PdfTextExtractor", "PDF load failed", e)
        }
        return pages
    }
}
