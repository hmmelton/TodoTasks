package com.harrison.todotasks.ui.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ChipColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harrison.todotasks.R
import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.ui.DueDates
import com.harrison.todotasks.ui.rememberDueDateFormatter
import com.harrison.todotasks.ui.theme.Dimens
import com.harrison.todotasks.ui.theme.TodoTasksTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val TASK_CONTENT_TYPE = "task"
private const val COMPLETED_HEADER_KEY = "completed-header"
private const val EMPTY_MESSAGE_CONTENT_TYPE = "empty-message"

/** A task card's padding, narrow at the start, where the checkbox's touch target leaves room. */
private val TaskCardPadding = PaddingValues(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)

/** The gap between a task's checkbox and its details. */
private val TaskDetailsStartPadding = 4.dp

/** The gap between a task's title, description, and due date. */
private val TaskDetailsLineGap = 2.dp

/** The gap between the items in the task list. */
private val TaskListItemGap = 12.dp

/** Sets the Completed heading in slightly from the cards' edges, with extra room above it. */
private val CompletedHeaderPadding = PaddingValues(start = 4.dp, top = 12.dp)

/** A floating action button's height. */
private val FabHeight = 56.dp

/** The margin between a floating action button and the bottom of the screen. */
private val FabMargin = 16.dp

/** The room under the last task: the button, its margin, and a screen padding above it. */
private val FabClearance = FabMargin + FabHeight + Dimens.ScreenPadding

/**
 * The task list screen, backed by [viewModel].
 *
 * @param onTaskClick Opens the task with the given ID for editing.
 * @param onAddTask Opens the form for adding a task.
 */
@Composable
fun TaskListScreen(
    onTaskClick: (id: String) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TaskListContent(
        uiState = uiState,
        today = LocalDate.now(),
        onTaskClick = onTaskClick,
        onCompleteChange = viewModel::onCompleteChange,
        onAddTask = onAddTask,
        onMessageShown = viewModel::onMessageShown,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
    )
}

/**
 * Shows [uiState]: the incomplete tasks, then the completed ones, each of which can be completed
 * or opened, with a button that adds one. A [TaskListUiState.Content.message] shows in a snackbar,
 * then [onMessageShown] is called.
 *
 * @param today The device's current date, which due dates are labeled and flagged overdue against.
 * @param onRefresh Syncs the tasks, when the list, or its empty state, is pulled down.
 */
@Composable
fun TaskListContent(
    uiState: TaskListUiState,
    today: LocalDate,
    onTaskClick: (id: String) -> Unit,
    onCompleteChange: (id: String, isComplete: Boolean) -> Unit,
    onAddTask: () -> Unit,
    onMessageShown: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = (uiState as? TaskListUiState.Content)?.message
    val messageText = when (message) {
        TaskListMessage.CompleteChangeFailed ->
            stringResource(R.string.task_list_complete_change_failed)
        null -> null
    }
    // The snackbar shows for seconds, so the effect calls the latest callback without restarting.
    val currentOnMessageShown by rememberUpdatedState(onMessageShown)
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            currentOnMessageShown()
        }
    }
    // Keeps the same instance for the same day, so task cards can skip recomposing.
    val dueDates = remember(today) { DueDates(today) }

    Scaffold(
        modifier = modifier,
        topBar = { TaskListTopBar() },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.task_list_add),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { innerPadding ->
        when (uiState) {
            TaskListUiState.Loading -> CenteredBox(innerPadding) { CircularProgressIndicator() }
            TaskListUiState.Error -> CenteredMessage(R.string.task_list_error, innerPadding)
            is TaskListUiState.Content -> RefreshableTasks(
                content = uiState,
                dueDates = dueDates,
                onTaskClick = onTaskClick,
                onCompleteChange = onCompleteChange,
                onRefresh = onRefresh,
                contentPadding = innerPadding,
            )
        }
    }
}

/** The cobalt app bar that titles the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListTopBar() {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.task_list_title)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/**
 * The tasks of [content], or the empty state when there are none, either of which can be pulled down
 * to call [onRefresh]. The indicator shows while [TaskListUiState.Content.isSyncing], and starts
 * below the app bar. A Sync custom action calls [onRefresh] too, for anyone who can't make the
 * gesture, since the box offers no accessibility action of its own.
 */
@Composable
private fun RefreshableTasks(
    content: TaskListUiState.Content,
    dueDates: DueDates,
    onTaskClick: (id: String) -> Unit,
    onCompleteChange: (id: String, isComplete: Boolean) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state = rememberPullToRefreshState()
    val syncLabel = stringResource(R.string.task_list_sync)
    PullToRefreshBox(
        isRefreshing = content.isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(syncLabel) {
                        onRefresh()
                        true
                    },
                )
            },
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = content.isSyncing,
                // The list scrolls under the app bar, so the indicator is moved down out from it.
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding()),
            )
        },
    ) {
        when {
            content.tasks.isEmpty() -> EmptyTaskList(contentPadding)
            else -> TaskList(
                content = content,
                dueDates = dueDates,
                onTaskClick = onTaskClick,
                onCompleteChange = onCompleteChange,
                contentPadding = contentPadding,
            )
        }
    }
}

/**
 * The message that there are no tasks, centered in a list of its own so that it can still be pulled
 * to refresh, which a new device does to fetch its tasks.
 */
@Composable
private fun EmptyTaskList(contentPadding: PaddingValues) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(contentType = EMPTY_MESSAGE_CONTENT_TYPE) {
            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                StateMessage(R.string.task_list_empty)
            }
        }
    }
}

/** The tasks of [content], in display order, with the completed ones under their own heading. */
@Composable
private fun TaskList(
    content: TaskListUiState.Content,
    dueDates: DueDates,
    onTaskClick: (id: String) -> Unit,
    onCompleteChange: (id: String, isComplete: Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    val (openTasks, doneTasks) = content.tasks.partition { !it.isComplete }
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + Dimens.ScreenPadding,
            top = contentPadding.calculateTopPadding() + Dimens.ScreenPadding,
            end = contentPadding.calculateEndPadding(layoutDirection) + Dimens.ScreenPadding,
            // Leaves room to scroll the last task out from under the button.
            bottom = contentPadding.calculateBottomPadding() + FabClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(TaskListItemGap),
    ) {
        taskItems(openTasks, dueDates, onTaskClick, onCompleteChange)
        if (doneTasks.isNotEmpty()) {
            item(key = COMPLETED_HEADER_KEY, contentType = COMPLETED_HEADER_KEY) {
                Text(
                    text = stringResource(R.string.task_list_completed),
                    modifier = Modifier
                        .animateItem()
                        .padding(CompletedHeaderPadding)
                        .semantics { heading() },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        taskItems(doneTasks, dueDates, onTaskClick, onCompleteChange)
    }
}

/** A card for each of [tasks], keyed by ID so a task animates when it moves between sections. */
private fun LazyListScope.taskItems(
    tasks: List<TodoTask>,
    dueDates: DueDates,
    onTaskClick: (id: String) -> Unit,
    onCompleteChange: (id: String, isComplete: Boolean) -> Unit,
) {
    items(tasks, key = { it.id }, contentType = { TASK_CONTENT_TYPE }) { task ->
        TaskCard(
            task = task,
            dueDates = dueDates,
            onClick = { onTaskClick(task.id) },
            onCompleteChange = { onCompleteChange(task.id, it) },
            modifier = Modifier.animateItem(),
        )
    }
}

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

@Composable
private fun CenteredMessage(
    @StringRes text: Int,
    contentPadding: PaddingValues,
) {
    CenteredBox(contentPadding) { StateMessage(text) }
}

/** A message that fills the screen in place of the tasks, such as an empty or error state. */
@Composable
private fun StateMessage(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        modifier = Modifier.padding(Dimens.StateMessagePadding),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun CenteredBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private val previewToday = LocalDate.of(2026, 11, 4)

// Titles, due dates as days from today, and completion.
private val previewTasks = listOf(
    Triple("Pay the electric bill", -2L, false),
    Triple("Buy groceries", 0L, false),
    Triple("Call the dentist", 1L, false),
    Triple("Book flights", 20L, false),
    Triple("Renew passport", null, true),
    Triple("Return library books", -1L, true),
).mapIndexed { index, (title, dueIn, isComplete) ->
    TodoTask(
        id = "$index",
        title = title,
        description = if (index == 1) "Milk, eggs, and bread" else "",
        createdAt = 0,
        lastUpdatedAt = 0,
        deletedAt = null,
        dueAt = dueIn?.let {
            previewToday.plusDays(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
        isComplete = isComplete,
        version = null,
    )
}

@Composable
private fun TaskListPreview(uiState: TaskListUiState, darkTheme: Boolean) {
    TodoTasksTheme(darkTheme = darkTheme) {
        TaskListContent(
            uiState = uiState,
            today = previewToday,
            onTaskClick = {},
            onCompleteChange = { _, _ -> },
            onAddTask = {},
            onMessageShown = {},
            onRefresh = {},
        )
    }
}

@Preview(name = "Light")
@Composable
private fun TaskListContentPreview() {
    TaskListPreview(TaskListUiState.Content(previewTasks), darkTheme = false)
}

@Preview(name = "Dark")
@Composable
private fun TaskListContentDarkPreview() {
    TaskListPreview(TaskListUiState.Content(previewTasks), darkTheme = true)
}

@Preview(name = "Syncing")
@Composable
private fun TaskListSyncingPreview() {
    TaskListPreview(TaskListUiState.Content(previewTasks, isSyncing = true), darkTheme = false)
}

@Preview(name = "Empty")
@Composable
private fun TaskListEmptyPreview() {
    TaskListPreview(TaskListUiState.Content(emptyList()), darkTheme = false)
}
