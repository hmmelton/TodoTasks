package com.harrison.todotasks.ui.edittask

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harrison.todotasks.R
import com.harrison.todotasks.ui.theme.Dimens
import com.harrison.todotasks.ui.theme.TodoTasksTheme
import java.time.LocalDate
import java.time.ZoneOffset

/** The gap between a full-screen message and the button under it. */
private val StateMessageButtonGap = 16.dp

/**
 * The add or edit task screen, backed by [viewModel]. Calls [onBack] when the user navigates up,
 * and once the task is saved or deleted.
 */
@Composable
fun EditTaskScreen(
    viewModel: EditTaskViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Keyed on finishing rather than the whole state, so typing doesn't relaunch the effect, which
    // calls the latest callback.
    val isFinished = uiState == EditTaskUiState.Finished
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(isFinished) {
        if (isFinished) currentOnBack()
    }
    EditTaskContent(
        uiState = uiState,
        today = LocalDate.now(),
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onDueAtChange = viewModel::onDueAtChange,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        onMessageShown = viewModel::onMessageShown,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Shows [uiState]: the form, with Save under it and, for an existing task, Delete in the app bar,
 * which asks first. An [EditTaskUiState.Editing.message] shows in a snackbar, then [onMessageShown]
 * is called.
 *
 * @param today The device's current date, which the Today and Tomorrow due dates are based on.
 */
@Composable
fun EditTaskContent(
    uiState: EditTaskUiState,
    today: LocalDate,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueAtChange: (Long?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = (uiState as? EditTaskUiState.Editing)?.message
    val messageText = when (message) {
        EditTaskMessage.SaveFailed -> stringResource(R.string.edit_task_save_failed)
        EditTaskMessage.DeleteFailed -> stringResource(R.string.edit_task_delete_failed)
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
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val editing = uiState as? EditTaskUiState.Editing

    Scaffold(
        modifier = modifier,
        topBar = {
            EditTaskTopBar(
                isNewTask = editing?.isNewTask == true,
                canDelete = editing?.isNewTask == false,
                onBack = onBack,
                onDelete = { showDeleteDialog = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { innerPadding ->
        when (uiState) {
            EditTaskUiState.Loading -> CenteredBox(innerPadding) { CircularProgressIndicator() }
            // The screen is about to close, so it shows nothing.
            EditTaskUiState.Finished -> Unit
            EditTaskUiState.NotFound -> StateMessage(R.string.edit_task_not_found, innerPadding, onBack)
            EditTaskUiState.Error -> StateMessage(R.string.edit_task_error, innerPadding, onBack)
            is EditTaskUiState.Editing -> EditTaskForm(
                title = uiState.title,
                description = uiState.description,
                dueAt = uiState.dueAt,
                today = today,
                canSave = uiState.canSave,
                onTitleChange = onTitleChange,
                onDescriptionChange = onDescriptionChange,
                onDueAtChange = onDueAtChange,
                onSave = onSave,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .padding(vertical = Dimens.ScreenPadding),
            )
        }
    }

    if (showDeleteDialog) {
        DeleteTaskDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/**
 * The cobalt app bar, titled for adding or editing, with up navigation and, when [canDelete], a
 * Delete action that calls [onDelete].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTaskTopBar(
    isNewTask: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    TopAppBar(
        title = {
            Text(
                stringResource(
                    if (isNewTask) R.string.edit_task_new_title else R.string.edit_task_edit_title,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.edit_task_back),
                )
            }
        },
        actions = {
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.edit_task_delete),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.primaryContainer,
            navigationIconContentColor = colorScheme.onPrimaryContainer,
            titleContentColor = colorScheme.onPrimaryContainer,
            actionIconContentColor = colorScheme.onPrimaryContainer,
        ),
    )
}

/** Asks before deleting the task, which [onConfirm] does. */
@Composable
private fun DeleteTaskDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_task_delete_dialog_title)) },
        text = { Text(stringResource(R.string.edit_task_delete_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.edit_task_delete_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_task_cancel))
            }
        },
    )
}

/** A message that fills the screen, with a button back to the task list. */
@Composable
private fun StateMessage(@StringRes text: Int, contentPadding: PaddingValues, onBack: () -> Unit) {
    CenteredBox(contentPadding) {
        Column(
            modifier = Modifier.padding(Dimens.StateMessagePadding),
            verticalArrangement = Arrangement.spacedBy(StateMessageButtonGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(text), textAlign = TextAlign.Center)
            FilledTonalButton(onClick = onBack) {
                Text(stringResource(R.string.edit_task_back_to_tasks))
            }
        }
    }
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

@Composable
private fun EditTaskPreview(uiState: EditTaskUiState, darkTheme: Boolean) {
    TodoTasksTheme(darkTheme = darkTheme) {
        EditTaskContent(
            uiState = uiState,
            today = previewToday,
            onTitleChange = {},
            onDescriptionChange = {},
            onDueAtChange = {},
            onSave = {},
            onDelete = {},
            onMessageShown = {},
            onBack = {},
        )
    }
}

@Preview(name = "Light, due today")
@Composable
private fun EditTaskContentPreview() {
    EditTaskPreview(
        uiState = EditTaskUiState.Editing(
            title = "Buy groceries",
            description = "Milk, eggs, and bread",
            dueAt = previewToday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            isNewTask = false,
        ),
        darkTheme = false,
    )
}

@Preview(name = "Dark, picked date")
@Composable
private fun EditTaskContentDarkPreview() {
    EditTaskPreview(
        uiState = EditTaskUiState.Editing(
            title = "Book flights",
            description = "",
            dueAt = 1_795_478_400_000, // 2026-11-24 UTC
            isNewTask = false,
        ),
        darkTheme = true,
    )
}

@Preview(name = "New task")
@Composable
private fun EditTaskNewPreview() {
    EditTaskPreview(
        uiState = EditTaskUiState.Editing(
            title = "",
            description = "",
            dueAt = null,
            isNewTask = true,
        ),
        darkTheme = false,
    )
}

@Preview(name = "Not found")
@Composable
private fun EditTaskNotFoundPreview() {
    EditTaskPreview(EditTaskUiState.NotFound, darkTheme = false)
}
