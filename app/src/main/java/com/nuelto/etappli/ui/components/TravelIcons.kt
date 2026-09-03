package com.nuelto.etappli.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.nuelto.etappli.data.model.TravelMode

/** The icon for what you travel on, in the filled style the stop kinds use. */
fun modeIcon(mode: TravelMode): ImageVector = when (mode) {
    TravelMode.CAR -> Icons.Default.DirectionsCar
    TravelMode.CABLE_CAR -> CableCar
    TravelMode.FUNICULAR -> Icons.Default.DirectionsRailway
    TravelMode.FERRY -> Icons.Default.DirectionsBoat
    TravelMode.TRAM -> Icons.Default.Tram
    TravelMode.BUS -> Icons.Default.DirectionsBus
    TravelMode.TRAIN -> Icons.Default.Train
    TravelMode.TRANSIT -> Icons.Default.DirectionsTransit
}

/**
 * A gondola on its cable — the icon set has none. Same 24-unit canvas and filled style as
 * the Material icons it sits beside, so it tints and scales like them.
 */
val CableCar: ImageVector by lazy {
    ImageVector.Builder("CableCar", 24.dp, 24.dp, 24f, 24f).apply {
        // The cable, rising left to right, and the hanger down to the cabin.
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
            moveTo(2f, 8f)
            lineTo(22f, 4f)
            moveTo(12f, 6f)
            lineTo(12f, 10f)
        }
        // The cabin, with two windows cut out.
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(7f, 10f)
            lineTo(17f, 10f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            close()
            moveTo(8.5f, 12f)
            lineTo(11.5f, 12f)
            lineTo(11.5f, 15.5f)
            lineTo(8.5f, 15.5f)
            close()
            moveTo(12.5f, 12f)
            lineTo(15.5f, 12f)
            lineTo(15.5f, 15.5f)
            lineTo(12.5f, 15.5f)
            close()
        }
    }.build()
}
