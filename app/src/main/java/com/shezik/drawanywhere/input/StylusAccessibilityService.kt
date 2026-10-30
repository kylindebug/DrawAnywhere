package com.shezik.drawanywhere.input

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

/** Android 14 source-level interception: touchscreens are never subscribed to. */
class StylusAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        if (Build.VERSION.SDK_INT >= 34) StylusInputBridge.service = this
    }

    internal fun setCapturing(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 34) return
        val info = serviceInfo ?: return
        val sources = if (enabled) InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_BLUETOOTH_STYLUS else 0
        if (info.motionEventSources == sources) return
        info.setMotionEventSources(sources)
        serviceInfo = info
    }

    override fun onMotionEvent(event: MotionEvent) {
        StylusInputBridge.dispatch(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (StylusInputBridge.service === this) StylusInputBridge.service = null
        super.onDestroy()
    }
}
