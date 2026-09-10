package io.github.playfoundryhq.adaptiveflow.domain

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.github.playfoundryhq.adaptiveflow.data.ai.AiException
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.ParsedCard
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.ParsedDeck

/**
 * Pure, side-effect-free parsing used by [ImportPipeline]: JSON extraction /
 * repair, the offline "word: meaning" list parser, and the URL heuristics.
 * Split out so it's unit-testable without constructing the whole pipeline.
 */
object ImportParsing {

    /** Strip markdown fences and grab the outermost JSON object/array. */
    fun cleanAndExtractJson(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            val firstNewLine = text.indexOf('\n')
            if (firstNewLine != -1) text = text.substring(firstNewLine).trim()
            if (text.endsWith("```")) text = text.substring(0, text.length - 3).trim()
        }
        val firstBrace = text.indexOf('{')
        val firstBracket = text.indexOf('[')
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            val lastBrace = text.lastIndexOf('}')
            if (lastBrace > firstBrace) return text.substring(firstBrace, lastBrace + 1)
        } else if (firstBracket != -1) {
            val lastBracket = text.lastIndexOf(']')
            if (lastBracket > firstBracket) return text.substring(firstBracket, lastBracket + 1)
        }
        return text
    }

    /** Parse [jsonText] as a [ParsedDeck], accepting either the full object or a bare card array. */
    fun robustParseJsonDeck(moshi: Moshi, jsonText: String): ParsedDeck {
        val trimmed = jsonText.trim()
        runCatching {
            moshi.adapter(ParsedDeck::class.java).fromJson(trimmed)?.takeIf { it.cards.isNotEmpty() }
        }.getOrNull()?.let { return it }

        runCatching {
            val type = Types.newParameterizedType(List::class.java, ParsedCard::class.java)
            moshi.adapter<List<ParsedCard>>(type).fromJson(trimmed)?.takeIf { it.isNotEmpty() }
        }.getOrNull()?.let { return ParsedDeck(cards = it) }

        throw AiException("The AI response was not valid flashcard JSON.", AiException.Kind.EMPTY)
    }

    fun isYouTubeUrl(text: String): Boolean =
        Regex("""(youtube\.com/(watch|shorts)|youtu\.be/|m\.youtube\.com)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    fun looksLikeUrlFragment(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("http", true) || t.startsWith("//") ||
            t.equals("http", true) || t.equals("https", true) ||
            t.contains("youtu.be") || t.contains("youtube.com")
    }

    fun filterOutUrlEchoCards(cards: List<ParsedCard>): List<ParsedCard> =
        cards.filterNot { looksLikeUrlFragment(it.front) || looksLikeUrlFragment(it.back) }

    /** The honest offline importer: a plain "word: meaning" (or "-" / "=") list, no AI. */
    fun parseRawTextLocally(rawText: String, topicHint: String): ParsedDeck? {
        val lines = rawText.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
        if (lines.isEmpty()) return null

        var colon = 0; var hyphen = 0; var eq = 0
        for (l in lines) when {
            l.contains(":") -> colon++
            l.contains(" - ") || l.contains(" – ") -> hyphen++
            l.contains("=") -> eq++
        }
        val sep = when {
            colon >= 1 -> ":"
            hyphen >= 1 -> "-"
            eq >= 1 -> "="
            else -> return null
        }
        val cards = lines.mapNotNull { line ->
            val parts = line.split(sep, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val front = parts[0].trim().removePrefix("-").removePrefix("*").trim()
            val back = parts[1].trim()
            if (front.isEmpty() || back.isEmpty()) null else ParsedCard(front, back, "")
        }
        if (cards.isEmpty()) return null
        return ParsedDeck(
            deckName = topicHint.ifBlank { "📋 Imported list" },
            sourceLanguage = "Auto",
            targetLanguage = "English",
            cards = cards,
        )
    }
}
