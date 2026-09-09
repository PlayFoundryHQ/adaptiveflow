package io.github.playfoundryhq.adaptiveflow.domain

/**
 * The spaced-repetition scheduler — an SM-2 variant with a confidence-based ease
 * adjustment. Pure and deterministic so it can be unit-tested against known
 * vectors; the ViewModel only maps its confidence enum onto [Grade] and turns
 * [State.intervalDays] into a `nextReview` timestamp.
 */
object SrsScheduler {

    enum class Grade { AGAIN, GOOD, EASY }

    data class State(
        val repetitions: Int,
        val intervalDays: Int,
        val easeFactor: Float,
    )

    private const val EASE_MIN = 1.3f
    private const val EASE_MAX = 3.0f

    fun next(current: State, grade: Grade): State {
        val correct = grade != Grade.AGAIN
        val repetitions = if (correct) current.repetitions + 1 else 0

        val ease = when (grade) {
            Grade.AGAIN -> (current.easeFactor - 0.25f).coerceAtLeast(EASE_MIN)
            Grade.GOOD -> current.easeFactor
            Grade.EASY -> (current.easeFactor + 0.15f).coerceAtMost(EASE_MAX)
        }

        val base = if (!correct) 1 else when (repetitions) {
            1 -> 1
            2 -> 3
            else -> (current.intervalDays * current.easeFactor).toInt().coerceAtLeast(6)
        }
        val multiplier = if (grade == Grade.EASY) 1.4f else 1.0f
        val intervalDays = (base * multiplier).toInt().coerceAtLeast(1)

        return State(repetitions = repetitions, intervalDays = intervalDays, easeFactor = ease)
    }
}
