package com.sunseat.app.ui.map

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=android.app.Application::class,sdk=[36])
class PlaybackMarkerTest {
    @Test fun rotatingPlaybackIconsKeepTheirCenterOnTheRouteCoordinate() {
        val bitmap=Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888)
        val style=playbackLabelStyle(bitmap)
        assertEquals(.5f,style.anchorPoint.x,0f)
        assertEquals(.5f,style.anchorPoint.y,0f)
        assertFalse(style.isApplyDpScale)
        bitmap.recycle()
    }
}
