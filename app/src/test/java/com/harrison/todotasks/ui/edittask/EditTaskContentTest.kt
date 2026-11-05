package com.harrison.todotasks.ui.edittask

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks what the add or edit task screen shows for each state, and which callbacks it calls. */
@RunWith(RobolectricTestRunner::class)
class EditTaskContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 4)

    private fun midnightUtc(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val dueAt = midnightUtc(LocalDate.of(2026, 11, 24))

    private val existingTask = EditTaskUiState.Editing(
        title = "Buy groceries",
        description = "Milk",
        dueAt = dueAt,
        isNewTask = false,
    )

    private val newTask = EditTaskUiState.Editing(
        title = "",
        description = "",
        dueAt = null,
        isNewTask = true,
    )

    private val titles = mutableListOf<String>()
    private val descriptions = mutableListOf<String>()
    private val dueDates = mutableListOf<Long?>()
    private var saves = 0
    private var deletes = 0
    private var backs = 0
    private var messagesShown = 0

    private fun setContent(uiState: EditTaskUiState) {
        composeRule.setContent {
            EditTaskContent(
                uiState = uiState,
                today = today,
                onTitleChange = { titles += it },
                onDescriptionChange = { descriptions += it },
                onDueAtChange = { dueDates += it },
                onSave = { saves++ },
                onDelete = { deletes++ },
                onMessageShown = { messagesShown++ },
                onBack = { backs++ },
            )
        }
    }

    @Test
    fun `a new task has an empty form, a disabled Save, and no Delete`() {
        setContent(newTask)

        composeRule.onNodeWithText("New task").assertExists()
        composeRule.onNodeWithText("None").assertExists()
        composeRule.onNodeWithText("Today").assertIsNotSelected()
        composeRule.onNodeWithText("Tomorrow").assertIsNotSelected()
        composeRule.onNodeWithText("Pick date…").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Clear due date").assertDoesNotExist()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Delete task").assertDoesNotExist()
    }

    @Test
    fun `a blank title can't be saved`() {
        setContent(existingTask.copy(title = "   "))

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `an existing task shows its fields and saves`() {
        setContent(existingTask)

        composeRule.onNodeWithText("Edit task").assertExists()
        composeRule.onNodeWithText("Buy groceries").assertExists()
        composeRule.onNodeWithText("Milk").assertExists()
        composeRule.onNodeWithText("Nov 24, 2026").assertExists()
        composeRule.onNodeWithText("Save").assertIsEnabled().performScrollTo().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `Save sits under the form`() {
        setContent(existingTask)

        val save = composeRule.onNodeWithText("Save").performScrollTo().getBoundsInRoot()
        val dueDateChip = composeRule.onNodeWithText("Pick date…").getBoundsInRoot()
        assertTrue(dueDateChip.bottom <= save.top)
    }

    @Test
    fun `typing changes the title`() {
        setContent(newTask)

        composeRule.onNode(hasSetTextAction() and hasText("Title")).performTextInput("Call mom")

        assertEquals(listOf("Call mom"), titles)
    }

    @Test
    fun `typing changes the description`() {
        setContent(newTask)

        composeRule.onNode(hasSetTextAction() and hasText("Description")).performTextInput("Sunday")

        assertEquals(listOf("Sunday"), descriptions)
    }

    @Test
    fun `the due date can be confirmed in the date picker or cleared`() {
        setContent(existingTask)

        composeRule.onNodeWithText("Pick date…")
            .assertIsSelected()
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithContentDescription("Clear due date").performScrollTo().performClick()

        // The picker starts on the due date, so confirming it keeps the same midnight UTC.
        assertEquals(listOf(dueAt, null), dueDates)
    }

    @Test
    fun `the Today and Tomorrow chips set the due date`() {
        setContent(newTask)

        composeRule.onNodeWithText("Today").performScrollTo().performClick()
        composeRule.onNodeWithText("Tomorrow").performScrollTo().performClick()

        assertEquals(listOf(midnightUtc(today), midnightUtc(today.plusDays(1))), dueDates)
    }

    @Test
    fun `the chip for the current due date is selected, and choosing it again clears it`() {
        setContent(existingTask.copy(dueAt = midnightUtc(today.plusDays(1))))

        composeRule.onNodeWithText("Today").assertIsNotSelected()
        composeRule.onNodeWithText("Pick date…").assertIsNotSelected()
        composeRule.onNodeWithText("Tomorrow")
            .assertIsSelected()
            .performScrollTo()
            .performClick()

        assertEquals(listOf<Long?>(null), dueDates)
    }

    @Test
    fun `delete asks first, and cancelling doesn't delete`() {
        setContent(existingTask)

        composeRule.onNodeWithContentDescription("Delete task").performClick()
        composeRule.onNodeWithText("Delete this task?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this task?").assertDoesNotExist()
        assertEquals(0, deletes)
    }

    @Test
    fun `confirming delete deletes`() {
        setContent(existingTask)

        composeRule.onNodeWithContentDescription("Delete task").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(1, deletes)
    }

    @Test
    fun `navigating up goes back`() {
        setContent(existingTask)

        composeRule.onNodeWithContentDescription("Navigate up").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `loading shows a progress indicator`() {
        setContent(EditTaskUiState.Loading)

        composeRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertExists()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun `a missing task says so and offers a way back`() {
        setContent(EditTaskUiState.NotFound)

        composeRule.onNodeWithText("This task no longer exists.").assertExists()
        composeRule.onNodeWithText("Back to tasks").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `an error shows a message`() {
        setContent(EditTaskUiState.Error)

        composeRule.onNodeWithText("This task couldn't be loaded.").assertExists()
    }

    @Test
    fun `a message shows in a snackbar, then is marked shown`() {
        setContent(existingTask.copy(message = EditTaskMessage.SaveFailed))

        composeRule.onNodeWithText("Couldn't save the task. Try again.").assertExists()
        assertEquals(0, messagesShown)

        // A short snackbar dismisses itself after 4 seconds.
        composeRule.mainClock.advanceTimeBy(5_000)

        assertEquals(1, messagesShown)
    }
}
