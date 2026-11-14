package com.sunseat.app.ui

import java.text.NumberFormat
import java.time.Duration
import java.util.Locale

/** "1시간 5분", "42분". 1분 미만은 "1분"([zeroOk]면 0은 "0분") */
fun formatDuration(d: Duration, zeroOk: Boolean = false): String {
    if (zeroOk && d.toMinutes() <= 0) return "0분"
    val total = d.toMinutes().coerceAtLeast(1)
    val h = total / 60
    val m = total % 60
    return when {
        h == 0L -> "${m}분"
        m == 0L -> "${h}시간"
        else -> "${h}시간 ${m}분"
    }
}

/** "1,550원" */
fun formatWon(won: Int): String = NumberFormat.getNumberInstance(Locale.KOREA).format(won) + "원"
