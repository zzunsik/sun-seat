package com.sunseat.app.ui.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.sunseat.app.R
import com.sunseat.app.domain.CarSeat
import com.sunseat.app.domain.Side

fun Side.word(context: Context): String = context.getString(if (this == Side.LEFT) R.string.common_left else R.string.common_right)
@Composable fun Side.word(): String = word(LocalContext.current)
fun List<CarSeat>.label(context: Context): String = joinToString(context.getString(R.string.common_seat_separator)) { seat ->
    context.getString(when (seat) {
        CarSeat.PASSENGER -> R.string.common_passenger
        CarSeat.REAR_LEFT -> R.string.common_rear_left
        CarSeat.REAR_RIGHT -> R.string.common_rear_right
    })
}
@Composable fun List<CarSeat>.label(): String = label(LocalContext.current)
