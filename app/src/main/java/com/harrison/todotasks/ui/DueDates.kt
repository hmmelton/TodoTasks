package com.harrison.todotasks.ui

import com.harrison.todotasks.data.model.TodoTask
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Compares due dates with [today], the device's current date.
 *
 * A due date is midnight UTC of its calendar day, as the Material 3 date picker selects, so
 * [today] and [tomorrow] are midnight UTC of the device's local dates.
 */
class DueDates(today: LocalDate) {

    /** Today, as a due date. */
    val today: Long = dueAtOf(today)

    /** Tomorrow, as a due date. */
    val tomorrow: Long = dueAtOf(today.plusDays(1))

    /** Whether [task] is incomplete and was due before today. */
    fun isOverdue(task: TodoTask): Boolean {
        val dueAt = task.dueAt
        return !task.isComplete && dueAt != null && dueAt < today
    }

    private fun dueAtOf(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
