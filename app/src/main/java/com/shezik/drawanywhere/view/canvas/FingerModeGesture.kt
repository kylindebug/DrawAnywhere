package com.shezik.drawanywhere.view.canvas

/** A deliberate single-finger displacement; stationary palms do not trigger it. */
internal class FingerModeGesture(private val threshold: Float) {
    private var tracking = false
    private var startX = 0f
    private var startY = 0f
    fun down(x: Float, y: Float) { tracking = true; startX = x; startY = y }
    fun cancel() { tracking = false }
    fun move(x: Float, y: Float): Boolean {
        if (!tracking) return false
        val dx = x - startX; val dy = y - startY
        if (dx * dx + dy * dy < threshold * threshold) return false
        tracking = false
        return true
    }
}
