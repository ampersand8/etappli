package com.nuelto.camperexperience.ui.theme

import androidx.compose.ui.graphics.Color
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.domain.MapAccent
import com.nuelto.camperexperience.data.model.TripStatus

// One color language across lists, timeline, and maps:
// blue = planned, green = active/current, grey = done/skipped.
val PlannedBlue = Color(0xFF2963A8)
val ActiveGreen = Color(0xFF2E7D4F)
val DoneGrey = Color(0xFF6F766F)

fun statusColor(status: TripStatus): Color = when (status) {
    TripStatus.PLANNED -> PlannedBlue
    TripStatus.ACTIVE -> ActiveGreen
    TripStatus.DONE -> DoneGrey
}

/** Stop accent inside a trip: the current stop is green, done grey, upcoming blue. */
fun stopColor(state: StopState, isCurrent: Boolean): Color = when {
    isCurrent -> ActiveGreen
    state == StopState.PLANNED -> PlannedBlue
    else -> DoneGrey
}

/** Same language on the map, whoever renders it. */
fun accentColor(accent: MapAccent): Color = when (accent) {
    MapAccent.PLANNED -> PlannedBlue
    MapAccent.CURRENT -> ActiveGreen
    MapAccent.DONE -> DoneGrey
}
