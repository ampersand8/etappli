package com.nuelto.camperexperience.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/** Reorder state fed by [listState]; only rows whose key is in [keys] can move. */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    keys: Set<String>,
    onMove: (from: String, to: String) -> String,
): ReorderState {
    val currentKeys by rememberUpdatedState(keys)
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) {
        ReorderState(
            rows = {
                listState.layoutInfo.visibleItemsInfo
                    .filter { it.key in currentKeys }
                    .map { ItemGeometry(it.key as String, it.offset, it.size) }
            },
            onMove = { from, to -> currentOnMove(from, to) },
        )
    }
}

/** Long-press lifts the row; dragging rewrites the order under the finger. */
@Composable
fun Modifier.reorderable(state: ReorderState, key: String, listState: LazyListState, enabled: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    if (!enabled) return this
    return this
        .zIndex(if (state.draggedKey == key) 1f else 0f)
        .graphicsLayer {
            translationY = state.translationFor(key)
            if (state.draggedKey == key) {
                shadowElevation = 8.dp.toPx()
                shape = RoundedCornerShape(12.dp)
            }
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(key) },
                onDragEnd = { state.stop() },
                onDragCancel = { state.stop() },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                    val info = listState.layoutInfo
                    val step = state.autoScroll(info.viewportStartOffset, info.viewportEndOffset)
                    if (step != 0f) scope.launch { listState.scrollBy(step) }
                },
            )
        }
}
