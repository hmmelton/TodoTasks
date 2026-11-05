package com.harrison.todotasks.ui.edittask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.harrison.todotasks.R
import com.harrison.todotasks.ui.DueDates
import com.harrison.todotasks.ui.rememberDueDateFormatter
import com.harrison.todotasks.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate

private val SaveButtonGap = 16.dp
private val FieldsCardPadding = 16.dp
private val FieldGap = 16.dp
private val DueDateSummaryGap = 4.dp

/** Keeps the due date summary as tall as its clear button, so it doesn't jump when that appears. */
private val DueDateSummaryMinHeight = 48.dp

private val DueDateChoiceGap = 8.dp

/**
 * The add or edit task form: a title, a description, and a due date that can be set to today or
 * tomorrow, picked from a date picker, or cleared, above a Save button enabled when [canSave].
 *
 * [dueAt] is midnight UTC of the due day, which is what the date picker selects.
 *
 * @param today The device's current date, which the Today and Tomorrow choices are based on.
 */
@Composable
fun EditTaskForm(
    title: String,
    description: String,
    dueAt: Long?,
    today: LocalDate,
    canSave: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueAtChange: (Long?) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SaveButtonGap),
    ) {
        TaskFieldsCard(
            title = title,
            description = description,
            dueAt = dueAt,
            dueDates = DueDates(today),
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onDueAtChange = onDueAtChange,
            onPickDueDate = { showDatePicker = true },
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
            enabled = canSave,
        ) {
            Text(stringResource(R.string.edit_task_save))
        }
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            dueAt = dueAt,
            onConfirm = { selected ->
                onDueAtChange(selected)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun TaskFieldsCard(
    title: String,
    description: String,
    dueAt: Long?,
    dueDates: DueDates,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueAtChange: (Long?) -> Unit,
    onPickDueDate: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(FieldsCardPadding),
            verticalArrangement = Arrangement.spacedBy(FieldGap),
        ) {
            TitleField(title, onTitleChange, fieldColors)
            DescriptionField(description, onDescriptionChange, fieldColors)
            DueDateField(
                dueAt = dueAt,
                dueDates = dueDates,
                onDueAtChange = onDueAtChange,
                onPick = onPickDueDate,
            )
        }
    }
}

@Composable
private fun TitleField(title: String, onTitleChange: (String) -> Unit, colors: TextFieldColors) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.edit_task_title_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
        colors = colors,
    )
}

@Composable
private fun DescriptionField(
    description: String,
    onDescriptionChange: (String) -> Unit,
    colors: TextFieldColors,
) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.edit_task_description_label)) },
        minLines = 3,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
        ),
        colors = colors,
    )
}

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

/** Chips for today, tomorrow, and a picked day. Today or Tomorrow clears the date if selected. */
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
