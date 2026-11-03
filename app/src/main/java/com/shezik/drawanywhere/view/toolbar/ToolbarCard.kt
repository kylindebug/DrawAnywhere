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
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private class DragScreenPosition { var current = Offset.Zero }

@OptIn(ExperimentalComposeUiApi::class)
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
    val screen = remember { DragScreenPosition() }
    Card(
        modifier = modifier
            .testTag("toolbar_card")
            .motionEventSpy { event -> screen.current = Offset(event.rawX, event.rawY) }
            .pointerInput(Unit) {
                // A single, stable gesture node survives folding and unfolding.
                // Pick the gesture policy at DOWN instead of cancelling a handler
                // in the middle of dispatch when a button changes compact state.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downOnScreen = screen.current
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
                        var previousScreen = if (fastDrag) downOnScreen else screen.current
                        move(screen.current - previousScreen)
                        previousScreen = screen.current
                        var finished = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == start.id } ?: break
                            // Screen coordinates remain stable while WindowManager
                            // moves or clamps this window under the pointer.
                            move(screen.current - previousScreen)
                            previousScreen = screen.current
                            change.consume()
                            interact()
                            dragPosition?.invoke(change.position)
                            if (!change.pressed) { finished = true; break }
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
