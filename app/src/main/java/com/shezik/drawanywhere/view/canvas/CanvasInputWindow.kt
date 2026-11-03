package com.shezik.drawanywhere.view.canvas

import android.hardware.input.InputManager
import android.os.Build
import android.view.View
import android.view.WindowManager
import com.shezik.drawanywhere.UiState

internal data class CanvasInputState(val visible: Boolean, val passthrough: Boolean, val fingers: Boolean)
internal val UiState.canvasInputState get() = CanvasInputState(canvasVisible, canvasPassthrough, fingerDrawingEnabled)

/** Keep a single input window alive across mode changes, including held palms. */
internal class CanvasInputWindow(
    private val windowManager: WindowManager,
    private val view: NativeDrawCanvasView,
    private val params: WindowManager.LayoutParams,
    initial: CanvasInputState,
) {
    private var state = initial
    private val passthroughAlpha = if (Build.VERSION.SDK_INT >= 31)
        view.context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else 1f

    init { updateParams(initial.passthrough); view.visibility = if (initial.visible) View.VISIBLE else View.GONE }

    fun apply(next: CanvasInputState) {
        if (next == state) return
        view.cancelInput()
        if (next.passthrough != state.passthrough) {
            updateParams(next.passthrough)
            windowManager.updateViewLayout(view, params)
        }
        if (next.visible != state.visible) view.visibility = if (next.visible) View.VISIBLE else View.GONE
        state = next
    }

    private fun updateParams(passthrough: Boolean) {
        params.flags = if (passthrough) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.alpha = if (passthrough) passthroughAlpha else 1f
    }
}
