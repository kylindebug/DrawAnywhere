package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun DraggableToolbarCard(
    modifier: Modifier = Modifier,
    haptics: HapticFeedback,
    onPositionChange: (Offset) -> Unit,
    onPositionSaved: () -> Unit,
    onToolbarInteracted: () -> Unit,
    dragImmediately: Boolean = false,
    onDragStart: (() -> Unit)? = null,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    val immediate by rememberUpdatedState(dragImmediately)
    val move by rememberUpdatedState(onPositionChange)
    val interact by rememberUpdatedState(onToolbarInteracted)
    val startDrag by rememberUpdatedState(onDragStart)
    val dragPosition by rememberUpdatedState(onDragPosition)
    val endDrag by rememberUpdatedState(onDragEnd)
    val save by rememberUpdatedState(onPositionSaved)
    Card(
        modifier = modifier
            .testTag("toolbar_card")
            .pointerInput(Unit) {
                // A single, stable gesture node survives folding and unfolding.
                // Pick the gesture policy at DOWN instead of cancelling a handler
                // in the middle of dispatch when a button changes compact state.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    interact()
                    var overslop = Offset.Zero
                    val fastDrag = immediate
                    val start = if (fastDrag) awaitTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        overslop = over
                    } else awaitLongPressOrCancellation(down.id)
                    if (start != null) {
                        if (!fastDrag) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        startDrag?.invoke()
                        if (overslop != Offset.Zero) move(overslop)
                        val finished = drag(start.id) { change ->
                            // Compose already maps the previous pointer position into
                            // the current window coordinates. Adding window movement
                            // again makes a moving overlay accelerate away from the finger.
                            move(change.positionChange())
                            change.consume()
                            interact()
                            dragPosition?.invoke(change.position)
                        }
                        if (finished) currentEvent.changes.forEach { change ->
                            if (!change.pressed) {
                                // Include movement delivered with UP, before saving
                                // the desired position. The window may render it later.
                                move(change.positionChange())
                                dragPosition?.invoke(change.position)
                                change.consume()
                            }
                        }
                        val shouldSave = endDrag?.invoke() != false
                        if (finished && shouldSave) save()
                    }
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
        )
    ) {
        content()
    }
}
