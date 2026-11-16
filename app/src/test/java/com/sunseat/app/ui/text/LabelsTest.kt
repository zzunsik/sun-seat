package com.sunseat.app.ui.text

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sunseat.app.domain.CarSeat
import com.sunseat.app.domain.Side
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class LabelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun sidesAreSpelledOut() {
        assertEquals("왼쪽", Side.LEFT.word(context))
        assertEquals("오른쪽", Side.RIGHT.word(context))
    }

    @Test fun carSeatsKeepOrderAndJoinWithSeparator() {
        assertEquals("", emptyList<CarSeat>().label(context))
        assertEquals("조수석", listOf(CarSeat.PASSENGER).label(context))
        assertEquals("뒷좌석 왼쪽", listOf(CarSeat.REAR_LEFT).label(context))
        assertEquals("뒷좌석 오른쪽·조수석", listOf(CarSeat.REAR_RIGHT, CarSeat.PASSENGER).label(context))
        assertEquals("조수석·뒷좌석 왼쪽·뒷좌석 오른쪽", CarSeat.entries.label(context))
    }
}
