package com.shezik.drawanywhere.input

import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread, in-process connection. No events are stored or sent off device. */
object StylusInputBridge {
    private val connectedState = MutableStateFlow(false)
    val connected = connectedState.asStateFlow()
    internal var service: StylusAccessibilityService? = null
        set(value) {
            field = value
            connectedState.value = value != null
            value?.setCapturing(listener != null)
        }

    private var listener: ((MotionEvent) -> Unit)? = null

    fun setListener(value: ((MotionEvent) -> Unit)?) {
        listener = value
        service?.setCapturing(value != null)
    }

    internal fun dispatch(event: MotionEvent) { listener?.invoke(event) }

    // Compose Popup windows are separate roots. Register them above the toolbar so
    // intercepted pen events can still operate controls in stylus-only mode.
    private val popups = linkedMapOf<View, () -> Unit>()
    fun registerPopup(view: View, dismiss: () -> Unit) { popups[view] = dismiss }
    fun unregisterPopup(view: View) { popups.remove(view) }
    fun topPopup(): Pair<View, () -> Unit>? =
        popups.entries.lastOrNull { it.key.isAttachedToWindow }?.let { it.key to it.value }
}
