package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import kotlin.math.roundToInt

/**
 * Google's encoded polyline algorithm. Hand-rolled rather than pulled in
 * with `android-maps-utils`, which is not a dependency and whose `PolyUtil` would drag
 * the decode out of `domain/` — where it is covered and mutation-tested — into the
 * Android-only half of the app.
 *
 * Lenient like the rest of the parsing here: a truncated or junk string yields the
 * points that could be read, never a throw.
 */
object Polyline {

    private const val PRECISION = 1e5

    fun decode(encoded: String): List<LatLng> {
        val cursor = Cursor(encoded)
        val points = mutableListOf<LatLng>()
        var lat = 0
        var lng = 0
        while (!cursor.done) {
            lat += cursor.next() ?: return points
            lng += cursor.next() ?: return points
            points += LatLng(lat / PRECISION, lng / PRECISION)
        }
        return points
    }

    /** The inverse of [decode], for joining several of Google's lines into one (ParkAndRide). */
    fun encode(points: List<LatLng>): String = buildString {
        var lat = 0
        var lng = 0
        points.forEach { point ->
            val nextLat = (point.latitude * PRECISION).roundToInt()
            val nextLng = (point.longitude * PRECISION).roundToInt()
            chunk(nextLat - lat)
            chunk(nextLng - lng)
            lat = nextLat
            lng = nextLng
        }
    }

    /** One zig-zag-encoded delta, five bits a character, the continuation bit on all but the last. */
    private fun StringBuilder.chunk(delta: Int) {
        var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (value >= 0x20) {
            append(((0x20 or (value and 0x1f)) + 63).toChar())
            value = value shr 5
        }
        append((value + 63).toChar())
    }

    /** One zig-zag-encoded delta at a time, so latitude and longitude share the reader. */
    private class Cursor(private val text: String) {
        private var index = 0

        val done: Boolean get() = index >= text.length

        /** Null when the chunk runs off the end of the string — i.e. truncated input. */
        fun next(): Int? {
            var shift = 0
            var result = 0
            var chunk: Int
            do {
                if (done) return null
                chunk = text[index++].code - 63
                result = result or ((chunk and 0x1f) shl shift)
                shift += 5
            } while (chunk >= 0x20)
            return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }
    }
}
