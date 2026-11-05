package com.harrison.todotasks.ui.edittask

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harrison.todotasks.R
import com.harrison.todotasks.ui.theme.Dimens

/** The gap between a full-screen message and the button under it. */
private val StateMessageButtonGap = 16.dp

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
