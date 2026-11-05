package com.harrison.todotasks.ui

import com.harrison.todotasks.data.local.fullTask
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks how due dates compare with today. */
class DueDatesTest {

    private val dueDates = DueDates(LocalDate.of(2026, 11, 4))

    private fun midnightUtc(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `today and tomorrow are midnight UTC of the local dates`() {
        assertEquals(midnightUtc(2026, 11, 4), dueDates.today)
        assertEquals(midnightUtc(2026, 11, 5), dueDates.tomorrow)
    }

    @Test
    fun `an incomplete task due before today is overdue`() {
        val task = fullTask.copy(dueAt = midnightUtc(2026, 11, 3), isComplete = false)

        assertTrue(dueDates.isOverdue(task))
    }

    @Test
    fun `a task due today isn't overdue`() {
        val task = fullTask.copy(dueAt = dueDates.today, isComplete = false)

        assertFalse(dueDates.isOverdue(task))
    }

    @Test
    fun `a completed or undated task isn't overdue`() {
        val past = midnightUtc(2026, 11, 3)

        assertFalse(dueDates.isOverdue(fullTask.copy(dueAt = past, isComplete = true)))
        assertFalse(dueDates.isOverdue(fullTask.copy(dueAt = null, isComplete = false)))
    }
}
