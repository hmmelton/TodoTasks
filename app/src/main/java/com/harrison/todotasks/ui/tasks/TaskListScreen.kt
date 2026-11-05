package com.harrison.todotasks.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ChipColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harrison.todotasks.R
import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.ui.DueDates
import com.harrison.todotasks.ui.rememberDueDateFormatter
import java.time.Instant

/** A task card's padding, narrow at the start, where the checkbox's touch target leaves room. */
private val TaskCardPadding = PaddingValues(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)

/** The gap between a task's checkbox and its details. */
private val TaskDetailsStartPadding = 4.dp

/** The gap between a task's title, description, and due date. */
private val TaskDetailsLineGap = 2.dp

@Composable
private fun TaskCard(
    task: TodoTask,
    dueDates: DueDates,
    onClick: () -> Unit,
    onCompleteChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(R.string.task_list_edit_label),
                    onClick = onClick,
                )
                .padding(TaskCardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompleteToggle(task, onCompleteChange)
            TaskDetails(
                task = task,
                dueDates = dueDates,
                onClick = onClick,
                modifier = Modifier.weight(1f).padding(start = TaskDetailsStartPadding),
            )
        }
    }
}

/**
 * The title of [task], struck through once it's complete, above its description and due date when
 * it has them.
 */
@Composable
private fun TaskDetails(
    task: TodoTask,
    dueDates: DueDates,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TaskDetailsLineGap)) {
        Text(
            text = task.title,
            color = when {
                task.isComplete -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (task.isComplete) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.titleMedium,
        )
        if (task.description.isNotBlank()) {
            Text(
                text = task.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        task.dueAt?.let { dueAt -> DueDateChip(task, dueAt, dueDates, onClick) }
    }
}

/**
 * A checkbox that completes [task]. Its label names the task, so it reads apart from the card that
 * opens the same task.
 */
@Composable
private fun CompleteToggle(task: TodoTask, onCompleteChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.task_list_complete, task.title)
    Checkbox(
        checked = task.isComplete,
        onCheckedChange = onCompleteChange,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/**
 * The due date of [task], [dueAt], labeled Today or Tomorrow when it is one of those. It stands out
 * when the task is overdue or due today, and opens the task like the rest of the card.
 */
@Composable
private fun DueDateChip(task: TodoTask, dueAt: Long, dueDates: DueDates, onClick: () -> Unit) {
    val label = dueDateLabel(dueAt, dueDates)
    val isOverdue = dueDates.isOverdue(task)
    val isDueToday = !task.isComplete && dueAt == dueDates.today
    val description = when {
        isOverdue -> stringResource(R.string.task_list_overdue, label)
        else -> stringResource(R.string.task_list_due, label)
    }
    AssistChip(
        onClick = onClick,
        // Says overdue in words too, so the chip doesn't rely on its color alone.
        label = {
            Text(if (isOverdue) stringResource(R.string.task_list_overdue_label, label) else label)
        },
        modifier = Modifier.semantics { contentDescription = description },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_event),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        colors = dueDateChipColors(isOverdue = isOverdue, isDueToday = isDueToday),
        // A filled chip needs no outline.
        border = if (isOverdue || isDueToday) null else AssistChipDefaults.assistChipBorder(true),
    )
}

/** [dueAt] as Today or Tomorrow when it is one of those, or else as a date. */
@Composable
private fun dueDateLabel(dueAt: Long, dueDates: DueDates): String {
    val formatter = rememberDueDateFormatter()
    return when (dueAt) {
        dueDates.today -> stringResource(R.string.due_date_today)
        dueDates.tomorrow -> stringResource(R.string.due_date_tomorrow)
        else -> formatter.format(Instant.ofEpochMilli(dueAt))
    }
}

/** Error colors for an overdue task, secondary ones for a task due today, or the outlined default. */
@Composable
private fun dueDateChipColors(isOverdue: Boolean, isDueToday: Boolean): ChipColors {
    val colorScheme = MaterialTheme.colorScheme
    return when {
        isOverdue -> AssistChipDefaults.assistChipColors(
            containerColor = colorScheme.errorContainer,
            labelColor = colorScheme.onErrorContainer,
            leadingIconContentColor = colorScheme.onErrorContainer,
        )
        isDueToday -> AssistChipDefaults.assistChipColors(
            containerColor = colorScheme.secondaryContainer,
            labelColor = colorScheme.onSecondaryContainer,
            leadingIconContentColor = colorScheme.onSecondaryContainer,
        )
        else -> AssistChipDefaults.assistChipColors(leadingIconContentColor = colorScheme.primary)
    }
}
