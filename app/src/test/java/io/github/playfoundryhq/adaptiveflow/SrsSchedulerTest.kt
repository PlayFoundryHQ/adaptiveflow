package io.github.playfoundryhq.adaptiveflow

import io.github.playfoundryhq.adaptiveflow.domain.SrsScheduler
import io.github.playfoundryhq.adaptiveflow.domain.SrsScheduler.Grade
import io.github.playfoundryhq.adaptiveflow.domain.SrsScheduler.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsSchedulerTest {

    private val fresh = State(repetitions = 0, intervalDays = 1, easeFactor = 2.5f)

    @Test
    fun `AGAIN resets repetitions and pins interval to 1 day`() {
        val r = SrsScheduler.next(State(4, 30, 2.6f), Grade.AGAIN)
        assertEquals(0, r.repetitions)
        assertEquals(1, r.intervalDays)
    }

    @Test
    fun `AGAIN drops ease by 0_25 but never below 1_3`() {
        assertEquals(2.25f, SrsScheduler.next(fresh, Grade.AGAIN).easeFactor, 1e-4f)
        val atFloor = SrsScheduler.next(State(2, 5, 1.4f), Grade.AGAIN)
        assertEquals(1.3f, atFloor.easeFactor, 1e-4f)
    }

    @Test
    fun `GOOD keeps ease and grows interval on the 1-3-then-ease ladder`() {
        val first = SrsScheduler.next(fresh, Grade.GOOD)
        assertEquals(1, first.repetitions)
        assertEquals(1, first.intervalDays)
        assertEquals(2.5f, first.easeFactor, 1e-4f)

        val second = SrsScheduler.next(first, Grade.GOOD)
        assertEquals(3, second.intervalDays)

        val third = SrsScheduler.next(second, Grade.GOOD) // (3 * 2.5) -> 7, floor 6
        assertEquals(7, third.intervalDays)
    }

    @Test
    fun `EASY raises ease up to a 3_0 ceiling and applies the 1_4x interval bonus`() {
        val easy = SrsScheduler.next(State(2, 10, 2.9f), Grade.EASY)
        assertEquals(3.0f, easy.easeFactor, 1e-4f) // 2.9 + 0.15 -> clamped
        assertEquals(3, easy.repetitions)
        // reps==3 -> base = (10 * 2.9).toInt()=29, * 1.4 -> 40
        assertEquals(40, easy.intervalDays)
    }

    @Test
    fun `interval is always at least one day`() {
        repeat(20) { i ->
            val r = SrsScheduler.next(State(i, i, 1.3f + i * 0.01f), Grade.entries[i % 3])
            assertTrue("interval $i", r.intervalDays >= 1)
            assertTrue("ease $i", r.easeFactor in 1.3f..3.0f)
        }
    }
}
