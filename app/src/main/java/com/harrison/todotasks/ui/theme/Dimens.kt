package com.harrison.todotasks.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The dimensions more than one screen shares, each named for its role, so that roles that happen
 * to share a value can change apart. They're constants rather than a CompositionLocal because none
 * of them vary by window size or theme.
 */
object Dimens {
    /** The padding between a screen's edges and its content. */
    val ScreenPadding = 16.dp

    /** The padding around a message that fills the screen, such as an empty or error state. */
    val StateMessagePadding = 24.dp
}
