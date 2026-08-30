package com.nuelto.camperexperience.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** A laid-out reorderable row — all the drag math needs from the lazy list. */
data class ItemGeometry(val key: String, val offset: Int, val size: Int)

/**
 * Drag-to-reorder bookkeeping for a lazy list whose reorderable rows ([rows]) are a
 * subset of its items. The lifted row is drawn at the finger; [onMove] rewrites the
 * real order as soon as the finger's centre crosses another row, and the relayout that
 * follows keeps the card under the finger — the translation is always "where the finger
 * put it" minus "where the row now sits".
 */
class ReorderState(
    private val rows: () -> List<ItemGeometry>,
    private val onMove: (from: String, to: String) -> Unit,
) {
    var draggedKey by mutableStateOf<String?>(null)
        private set

    private var dragged by mutableFloatStateOf(0f)
    private var startOffset = 0
    private var size = 0

    fun start(key: String) {
        val row = rows().firstOrNull { it.key == key } ?: return
        startOffset = row.offset
        size = row.size
        dragged = 0f
        draggedKey = key
    }

    fun drag(deltaY: Float) {
        val key = draggedKey ?: return
        dragged += deltaY
        val centre = startOffset + dragged + size / 2f
        // Nearest row centre: rows swap at the halfway mark, a drag fast enough to fly
        // over several rows lands on the right one, and hovering the lifted row's own
        // slot is a no-op — so a move fires once per crossing, reordered data or not.
        val target = rows().minByOrNull { abs(it.offset + it.size / 2f - centre) } ?: return
        if (target.key != key) onMove(key, target.key)
    }

    fun stop() {
        draggedKey = null
    }

    /** Vertical px to draw [key] away from its laid-out slot. */
    fun translationFor(key: String): Float {
        if (key != draggedKey) return 0f
        val row = rows().firstOrNull { it.key == key } ?: return 0f
        return startOffset + dragged - row.offset
    }

    /** Px to scroll so a card dragged against a viewport edge can keep going. */
    fun autoScroll(viewportStart: Int, viewportEnd: Int): Float {
        if (draggedKey == null) return 0f
        val top = startOffset + dragged
        val overshoot = (top - viewportStart).coerceAtMost(0f) + (top + size - viewportEnd).coerceAtLeast(0f)
        return overshoot.coerceIn(-MAX_SCROLL_STEP, MAX_SCROLL_STEP)
    }

    private companion object {
        const val MAX_SCROLL_STEP = 32f
    }
}
