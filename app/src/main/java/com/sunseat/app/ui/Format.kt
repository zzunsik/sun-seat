package com.sunseat.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.sunseat.app.R
import java.text.NumberFormat
import java.time.Duration
import java.util.Locale

fun formatDuration(context: Context, d: Duration, zeroOk: Boolean = false): String {
    val total = if (zeroOk) d.toMinutes().coerceAtLeast(0) else d.toMinutes().coerceAtLeast(1)
    val h = total / 60
    val m = total % 60
    return when {
        h == 0L -> context.getString(R.string.common_minutes, m)
        m == 0L -> context.getString(R.string.common_hours, h)
        else -> context.getString(R.string.common_hours_minutes, h, m)
    }
}
@Composable fun formatDuration(d: Duration, zeroOk: Boolean = false): String = formatDuration(LocalContext.current, d, zeroOk)
fun formatWon(context: Context, won: Int): String = context.getString(R.string.common_won, NumberFormat.getNumberInstance(Locale.KOREA).format(won))
@Composable fun formatWon(won: Int): String = formatWon(LocalContext.current, won)
