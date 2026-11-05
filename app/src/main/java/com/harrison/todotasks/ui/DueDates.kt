package com.harrison.todotasks.ui

import com.harrison.todotasks.data.model.TodoTask
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Compares due dates with [today], the device's current calendar day.
 *
 * A due date is midnight UTC of its calendar day, which is what the Material 3 date picker
 * selects, so today and tomorrow are expressed the same way: as midnight UTC of the device's
 * local dates. That keeps a task picked for today showing as today wherever the device is.
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
