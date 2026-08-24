package com.kolktech.kahawai.util

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kolktech.kahawai.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "1h 42m" / "1h" / "17m" / "17s" — falls back to seconds under a minute
 * rather than always rendering "0m", which reads as broken when you're
 * only a few seconds into something.
 */
fun formatDurationCoarse(context: Context, ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> context.getString(R.string.runtime_hours_minutes, hours, minutes)
        hours > 0 -> context.getString(R.string.runtime_hours, hours)
        minutes > 0 -> context.getString(R.string.runtime_minutes, minutes)
        else -> context.getString(R.string.runtime_seconds, seconds)
    }
}

@Composable
fun formatDurationCoarse(ms: Long): String = formatDurationCoarse(LocalContext.current, ms)

/**
 * A wall-clock time following the device's 12h/24h setting (Settings >
 * System > Date & time > "Use 24-hour format"), not just locale — two
 * devices in the same locale can differ on this.
 */
fun formatClockTime(context: Context, epochMs: Long): String {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

/** "Ends 9:47 PM" / "Ends 21:47" for a duration remaining from now. */
fun formatEndsAt(context: Context, remainingMs: Long): String =
    context.getString(R.string.ends_at_format, formatClockTime(context, System.currentTimeMillis() + remainingMs))

@Composable
fun formatEndsAt(remainingMs: Long): String {
    val context = LocalContext.current
    return stringResource(R.string.ends_at_format, formatClockTime(context, System.currentTimeMillis() + remainingMs))
}
