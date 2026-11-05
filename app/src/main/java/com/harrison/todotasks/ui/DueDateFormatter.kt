package com.harrison.todotasks.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Returns a formatter for due dates in the device's locale. Due dates are midnight UTC, so it
 * formats in UTC to show the day that was picked wherever the device is.
 */
@Composable
fun rememberDueDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneOffset.UTC)
    }
}
