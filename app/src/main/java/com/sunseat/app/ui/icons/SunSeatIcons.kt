package com.sunseat.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 이 앱만의 아이콘. 24dp 격자, 선 굵기 2, 둥근 끝. docs/DESIGN.md 6
 * 해는 짧은 햇살 8개가 있는 원으로 그린다(가장자리까지 뻗는 방사선 금지).
 */
object SunSeatIcons {
    private val Ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            stroke = Ink,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )

    /** 해: 원 + 짧은 햇살 8개 */
    val Sun: ImageVector = icon("Sun") {
        path(fill = Ink) {
            moveTo(12f, 7.5f)
            arcToRelative(4.5f, 4.5f, 0f, true, true, 0f, 9f)
            arcToRelative(4.5f, 4.5f, 0f, true, true, 0f, -9f)
            close()
        }
        stroke {
            moveTo(12f, 2f); lineTo(12f, 4f)
            moveTo(12f, 20f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(4f, 12f)
            moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(4.9f, 4.9f); lineTo(6.3f, 6.3f)
            moveTo(17.7f, 17.7f); lineTo(19.1f, 19.1f)
            moveTo(4.9f, 19.1f); lineTo(6.3f, 17.7f)
            moveTo(17.7f, 6.3f); lineTo(19.1f, 4.9f)
        }
    }

    /** 그늘: 구름에 반쯤 가려진 해 */
    val Shade: ImageVector = icon("Shade") {
        // 구름 뒤로 보이는 해의 윗부분과 짧은 햇살
        stroke {
            moveTo(9f, 3f); lineTo(9f, 4.5f)
            moveTo(3.5f, 8.5f); lineTo(5f, 8.5f)
            moveTo(5.1f, 4.6f); lineTo(6.1f, 5.6f)
            moveTo(12.9f, 4.6f); lineTo(11.9f, 5.6f)
            moveTo(6f, 10.5f)
            arcTo(3.2f, 3.2f, 0f, false, true, 12f, 8.2f)
        }
        // 구름(채움)
        path(fill = Ink) {
            moveTo(7.5f, 20f)
            lineTo(18f, 20f)
            arcToRelative(3.5f, 3.5f, 0f, false, false, 0.4f, -6.98f)
            arcToRelative(5f, 5f, 0f, false, false, -9.6f, 1.2f)
            arcToRelative(2.9f, 2.9f, 0f, false, false, -0.8f, 5.78f)
            close()
        }
    }

    /** 좌석: 옆에서 본 의자 */
    val Seat: ImageVector = icon("Seat") {
        stroke {
            moveTo(7f, 3f)
            lineTo(8.5f, 13f)
            lineTo(17f, 13f)
            moveTo(8.5f, 13f)
            lineTo(8f, 17f)
            lineTo(18f, 17f)
            moveTo(10f, 17f); lineTo(9f, 21f)
            moveTo(16f, 17f); lineTo(17f, 21f)
        }
    }

    /** 진행 방향: 위로 향한 차량 윤곽과 화살표 */
    val Heading: ImageVector = icon("Heading") {
        stroke {
            moveTo(12f, 2f); lineTo(8f, 6f)
            moveTo(12f, 2f); lineTo(16f, 6f)
            moveTo(12f, 2f); lineTo(12f, 8f)
        }
        stroke {
            moveTo(9f, 10f)
            lineTo(15f, 10f)
            quadTo(17f, 10f, 17f, 12f)
            lineTo(17f, 20f)
            quadTo(17f, 22f, 15f, 22f)
            lineTo(9f, 22f)
            quadTo(7f, 22f, 7f, 20f)
            lineTo(7f, 12f)
            quadTo(7f, 10f, 9f, 10f)
            close()
        }
    }
}
