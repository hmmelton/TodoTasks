package com.harrison.todotasks.ui.tasks

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.harrison.todotasks.data.local.fullTask
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Where a pull starts, as a fraction of the refresh box's height down from its top. */
private const val PULL_START_FRACTION = 0.3f

/** Checks what the task list shows for each state, and which callbacks its controls call. */
@RunWith(RobolectricTestRunner::class)
class TaskListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 4)

    private fun midnightUtc(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val dueAt = midnightUtc(LocalDate.of(2026, 11, 24))

    private val openTask = fullTask.copy(
        id = "open",
        title = "Buy groceries",
        deletedAt = null,
        dueAt = dueAt,
        isComplete = false,
    )

    private val doneTask = fullTask.copy(
        id = "done",
        title = "Renew passport",
        deletedAt = null,
        dueAt = null,
        isComplete = true,
    )

    private val taskClicks = mutableListOf<String>()
    private val completeChanges = mutableListOf<Pair<String, Boolean>>()
    private var addClicks = 0
    private var messagesShown = 0
    private var refreshes = 0

    private val indeterminateProgress = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    // Only the refresh box has the Sync action, so this finds the box around the list.
    private val refreshBox = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

    /** Drags from below the app bar, which blocks touches, to the bottom, past the threshold. */
    private fun pullDown() {
        composeRule.onNode(refreshBox).performTouchInput {
            swipeDown(startY = height * PULL_START_FRACTION, endY = bottom)
        }
    }

    private fun setContent(uiState: TaskListUiState) {
        composeRule.setContent {
            TaskListContent(
                uiState = uiState,
                today = today,
                onTaskClick = { taskClicks += it },
                onCompleteChange = { id, isComplete -> completeChanges += id to isComplete },
                onAddTask = { addClicks++ },
                onMessageShown = { messagesShown++ },
                onRefresh = { refreshes++ },
            )
        }
    }

    @Test
    fun `loading shows a progress indicator`() {
        setContent(TaskListUiState.Loading)

        composeRule.onNode(indeterminateProgress).assertExists()
    }

    @Test
    fun `an error shows a message`() {
        setContent(TaskListUiState.Error)

        composeRule.onNodeWithText("Your tasks couldn't be loaded.").assertExists()
    }

    @Test
    fun `no tasks shows the empty state`() {
        setContent(TaskListUiState.Content(tasks = emptyList()))

        composeRule.onNodeWithText("No tasks yet. Tap + to add one.").assertExists()
    }

    @Test
    fun `tasks show their titles, due dates, and completion`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask, doneTask)))

        composeRule.onNodeWithText("Buy groceries").assertExists()
        composeRule.onNodeWithText("Nov 24, 2026").assertExists()
        composeRule.onNodeWithContentDescription("Due Nov 24, 2026").assertExists()
        composeRule.onNodeWithText("Renew passport").assertExists()
        composeRule.onNodeWithContentDescription("Complete Buy groceries").assertIsOff()
        composeRule.onNodeWithContentDescription("Complete Renew passport").assertIsOn()
    }

    @Test
    fun `checking a task changes its completion`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask, doneTask)))

        composeRule.onNodeWithContentDescription("Complete Buy groceries").performClick()
        composeRule.onNodeWithContentDescription("Complete Renew passport").performClick()

        assertEquals(listOf("open" to true, "done" to false), completeChanges)
        assertTrue(taskClicks.isEmpty())
    }

    @Test
    fun `tapping a task opens it`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask, doneTask)))

        composeRule.onNodeWithText("Renew passport").performClick()

        assertEquals(listOf("done"), taskClicks)
    }

    @Test
    fun `completed tasks are listed under their own heading`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask, doneTask)))

        val open = composeRule.onNodeWithText("Buy groceries").getBoundsInRoot()
        val heading = composeRule.onNodeWithText("Completed").getBoundsInRoot()
        val done = composeRule.onNodeWithText("Renew passport").getBoundsInRoot()
        assertTrue(open.bottom <= heading.top)
        assertTrue(heading.bottom <= done.top)
    }

    @Test
    fun `there's no completed heading without completed tasks`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask)))

        composeRule.onNodeWithText("Completed").assertDoesNotExist()
    }

    @Test
    fun `an incomplete task due before today is overdue`() {
        val pastDue = midnightUtc(today.minusDays(2))
        setContent(
            TaskListUiState.Content(
                tasks = listOf(
                    openTask.copy(dueAt = pastDue),
                    doneTask.copy(dueAt = pastDue),
                ),
            ),
        )

        composeRule.onNodeWithText("Overdue · Nov 2, 2026").assertExists()
        composeRule.onNodeWithContentDescription("Overdue, due Nov 2, 2026").assertExists()
        // A completed task isn't overdue.
        composeRule.onNodeWithContentDescription("Due Nov 2, 2026").assertExists()
    }

    @Test
    fun `tasks due today or tomorrow say so`() {
        setContent(
            TaskListUiState.Content(
                tasks = listOf(
                    openTask.copy(dueAt = midnightUtc(today)),
                    doneTask.copy(dueAt = midnightUtc(today.plusDays(1)), isComplete = false),
                ),
            ),
        )

        composeRule.onNodeWithText("Today").assertExists()
        composeRule.onNodeWithContentDescription("Due Today").assertExists()
        composeRule.onNodeWithText("Tomorrow").assertExists()
        composeRule.onNodeWithContentDescription("Due Tomorrow").assertExists()
    }

    @Test
    fun `tapping a due date opens its task`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask)))

        composeRule.onNodeWithText("Nov 24, 2026").performClick()

        assertEquals(listOf("open"), taskClicks)
    }

    @Test
    fun `the add button opens the add form`() {
        setContent(TaskListUiState.Content(tasks = emptyList()))

        composeRule.onNodeWithContentDescription("New task").performClick()

        assertEquals(1, addClicks)
    }

    @Test
    fun `a message shows in a snackbar, then is marked shown`() {
        setContent(
            TaskListUiState.Content(
                tasks = listOf(openTask),
                message = TaskListMessage.CompleteChangeFailed,
            ),
        )

        composeRule.onNodeWithText("Couldn't update the task. Try again.").assertExists()
        assertEquals(0, messagesShown)

        // A short snackbar dismisses itself after 4 seconds.
        composeRule.mainClock.advanceTimeBy(5_000)

        assertEquals(1, messagesShown)
    }

    @Test
    fun `pulling the list down refreshes`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask, doneTask)))

        pullDown()

        assertEquals(1, refreshes)
    }

    @Test
    fun `pulling the empty state down refreshes`() {
        setContent(TaskListUiState.Content(tasks = emptyList()))

        pullDown()

        assertEquals(1, refreshes)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the sync action refreshes without the gesture`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask)))

        composeRule.onNode(refreshBox).performCustomAccessibilityActionWithLabel("Sync")

        assertEquals(1, refreshes)
    }

    @Test
    fun `the refresh indicator shows while syncing`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask), isSyncing = true))

        composeRule.onNode(indeterminateProgress).assertExists()
    }

    @Test
    fun `the refresh indicator is hidden when not syncing`() {
        setContent(TaskListUiState.Content(tasks = listOf(openTask)))

        composeRule.onNode(indeterminateProgress).assertDoesNotExist()
    }
}
