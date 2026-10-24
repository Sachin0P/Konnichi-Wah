package com.osakapatnam.konnichiwah.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Line icons for the bottom tabs, drawn on a 24 × 24 grid. Writing them as
// vectors keeps us free of an icon library dependency. Icon() tints them.
object TabIcons {
    val Home = lineIcon("home") {
        moveTo(4f, 10.5f); lineTo(12f, 4f); lineTo(20f, 10.5f)
        lineTo(20f, 20f); lineTo(14.5f, 20f); lineTo(14.5f, 14.5f)
        lineTo(9.5f, 14.5f); lineTo(9.5f, 20f); lineTo(4f, 20f); close()
    }

    // A torii gate, for the trip plan.
    val Plan = lineIcon("plan") {
        moveTo(3f, 5.5f); quadTo(12f, 3.5f, 21f, 5.5f)
        moveTo(5f, 9.5f); lineTo(19f, 9.5f)
        moveTo(7.5f, 6f); lineTo(7.5f, 20f)
        moveTo(16.5f, 6f); lineTo(16.5f, 20f)
    }

    val Camera = lineIcon("camera") {
        moveTo(3f, 8f); lineTo(8f, 8f); lineTo(9.5f, 5.5f); lineTo(14.5f, 5.5f); lineTo(16f, 8f)
        lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(12f, 10f); arcToRelative(3.2f, 3.2f, 0f, true, true, 0f, 6.4f)
        arcToRelative(3.2f, 3.2f, 0f, true, true, 0f, -6.4f)
    }

    val Chat = lineIcon("chat") {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 16f); lineTo(10.5f, 16f)
        lineTo(6.5f, 19.5f); lineTo(6.5f, 16f); lineTo(4f, 16f); close()
    }

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.9f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathBuilder = block,
                )
            }
            .build()
}
