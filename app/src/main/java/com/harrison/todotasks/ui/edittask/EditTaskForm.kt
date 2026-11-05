package com.harrison.todotasks.ui.edittask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harrison.todotasks.R
import com.harrison.todotasks.ui.DueDates
import com.harrison.todotasks.ui.rememberDueDateFormatter
import java.time.Instant

/** The gap between the due date summary and the choices under it. */
private val DueDateSummaryGap = 4.dp

/** Keeps the due date summary as tall as its clear button, so it doesn't jump when that appears. */
private val DueDateSummaryMinHeight = 48.dp

/** The gap between the due date choice chips. */
private val DueDateChoiceGap = 8.dp

/**
 * The due date, or None, with a button that clears it, above choices for today, tomorrow, or a
 * picked day. Choosing the day that's already selected clears it.
 */
@Composable
private fun DueDateField(
    dueAt: Long?,
    dueDates: DueDates,
    onDueAtChange: (Long?) -> Unit,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DueDateSummaryGap)) {
        Row(
            modifier = Modifier.heightIn(min = DueDateSummaryMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DueDateSummary(dueAt = dueAt, modifier = Modifier.weight(1f))
            if (dueAt != null) {
                IconButton(onClick = { onDueAtChange(null) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.edit_task_clear_due_date),
                    )
                }
            }
        }
        DueDateChoices(
            dueAt = dueAt,
            dueDates = dueDates,
            onDueAtChange = onDueAtChange,
            onPick = onPick,
        )
    }
}

/** The Due date heading above [dueAt] as a date, or None. */
@Composable
private fun DueDateSummary(dueAt: Long?, modifier: Modifier = Modifier) {
    val formatter = rememberDueDateFormatter()
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.edit_task_due_date),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = dueAt?.let { formatter.format(Instant.ofEpochMilli(it)) }
                ?: stringResource(R.string.edit_task_no_due_date),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Chips for today, tomorrow, and a picked day, the one matching [dueAt] selected. Today and
 * Tomorrow set the due date, or clear it when already selected, and the third calls [onPick].
 */
@Composable
private fun DueDateChoices(
    dueAt: Long?,
    dueDates: DueDates,
    onDueAtChange: (Long?) -> Unit,
    onPick: () -> Unit,
) {
    val isToday = dueAt == dueDates.today
    val isTomorrow = dueAt == dueDates.tomorrow
    FlowRow(horizontalArrangement = Arrangement.spacedBy(DueDateChoiceGap)) {
        FilterChip(
            selected = isToday,
            onClick = { onDueAtChange(if (isToday) null else dueDates.today) },
            label = { Text(stringResource(R.string.due_date_today)) },
        )
        FilterChip(
            selected = isTomorrow,
            onClick = { onDueAtChange(if (isTomorrow) null else dueDates.tomorrow) },
            label = { Text(stringResource(R.string.due_date_tomorrow)) },
        )
        FilterChip(
            selected = dueAt != null && !isToday && !isTomorrow,
            onClick = onPick,
            label = { Text(stringResource(R.string.edit_task_pick_due_date)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_event),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
}

/** A date picker starting on [dueAt], whose OK button passes the selected day to [onConfirm]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerDialog(
    dueAt: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueAt)
    val selected = datePickerState.selectedDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
            ) {
                Text(stringResource(R.string.edit_task_date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_task_cancel))
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
