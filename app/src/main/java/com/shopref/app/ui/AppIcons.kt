package com.shopref.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Simple 24×24 tab icons drawn for the app (single colour; tinted by Icon). */
object AppIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
        }
        return builder.build()
    }

    /** End mill: shank, fluted body with helical flute bands, tip. */
    val Mill: ImageVector = icon(
        "Mill",
        "M9,2H15V9H9Z",
        "M8,10H16V19L14.5,21H9.5L8,19Z M8,12.6L16,10.6L16,11.9L8,13.9Z M8,16.1L16,14.1L16,15.4L8,17.4Z",
    )

    /** Twist drill with a pointed tip. */
    val Drill: ImageVector = icon(
        "Drill",
        "M9.5,2H14.5V9H9.5Z",
        "M9,9.5H15V18L12,22.5L9,18Z M9,12.2L15,10.2L15,11.5L9,13.5Z M9,15.6L15,13.6L15,14.9L9,16.9Z",
    )

    /** Tap: square drive shank and a threaded body. */
    val Tap: ImageVector = icon(
        "Tap",
        "M10,2H14V8H10Z",
        "M8,9H16L15,10.5L16,12L15,13.5L16,15L15,16.5L16,18L13.5,21H10.5L8,18L9,16.5L8,15L9,13.5L8,12L9,10.5Z",
    )

    /** Lathe: stepped workpiece and a triangular insert. */
    val Turn: ImageVector = icon(
        "Turn",
        "M2,8H14V16H2Z M14,10H20V14H14Z",
        "M13,18L16.5,16L20,22H13Z",
    )

    /** Book with a bookmark. */
    val Book: ImageVector = icon(
        "Book",
        "M6,2H18A1,1 0 0,1 19,3V21A1,1 0 0,1 18,22H6A2,2 0 0,1 4,20V4A2,2 0 0,1 6,2Z M8,4V11L10.5,9.5L13,11V4Z",
    )
}
