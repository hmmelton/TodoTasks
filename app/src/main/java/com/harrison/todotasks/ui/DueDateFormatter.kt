package com.harrison.todotasks.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Returns a formatter for due dates, in the device's locale.
 *
 * A due date is a calendar day stored as midnight UTC, which is what the Material 3 date picker
 * selects. Formatting in UTC rather than the device's time zone shows the same day that was
 * picked, wherever the device is.
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
