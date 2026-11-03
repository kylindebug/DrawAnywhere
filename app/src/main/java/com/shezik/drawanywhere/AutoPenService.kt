package com.shezik.drawanywhere

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

/** Only receives stylus input, and only while this app's optional auto mode is active. */
class AutoPenService : AccessibilityService() {
    companion object {
        internal val connected = MutableStateFlow(false)
        private var instance: AutoPenService? = null
        private var receiver: ((MotionEvent) -> Unit)? = null
        private var cancelReceiver: (() -> Unit)? = null
        private var enabled = false
        private var popupOpen = false
        internal var penDown = false
            private set
        internal fun attach(onCancel: () -> Unit, value: (MotionEvent) -> Unit) {
            cancelReceiver = onCancel; receiver = value; instance?.refresh()
        }
        internal fun detach() { receiver = null; cancelReceiver = null; enabled = false; popupOpen = false; penDown = false; instance?.refresh() }
        internal fun enable(value: Boolean) { enabled = value; instance?.refresh() }
        internal fun popup(value: Boolean) { popupOpen = value; instance?.refresh() }
    }
    private var listening = false
    override fun onServiceConnected() {
        instance = this
        connected.value = Build.VERSION.SDK_INT >= 34
        refresh()
    }
    private fun refresh() {
        if (Build.VERSION.SDK_INT < 34) return
        val listen = enabled && receiver != null && !popupOpen
        if (listen == listening) return
        try {
            serviceInfo = serviceInfo.apply {
                setMotionEventSources(if (listen) InputDevice.SOURCE_STYLUS else 0)
            }
            listening = listen
        } catch (e: RuntimeException) { unavailable(e) }
        catch (e: LinkageError) { unavailable(e) }
    }
    private fun unavailable(error: Throwable) {
        cancelReceiver?.invoke()
        penDown = false
        listening = false
        connected.value = false
        enabled = false
        // Release exclusive stylus capture if changing the subscription failed.
        disableSelf()
        CompatibilityErrors.report(this, FailureStage.AUTO_MODE, error)
        CompatibilityErrors.notifyStopped(this)
    }
    override fun onMotionEvent(event: MotionEvent) {
        if (!listening) return
        if (event.actionMasked == MotionEvent.ACTION_DOWN) penDown = true
        try { receiver?.invoke(event) }
        finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                penDown = false
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { cancelReceiver?.invoke(); penDown = false }
    override fun onUnbind(intent: Intent?): Boolean { disconnected(); return super.onUnbind(intent) }
    override fun onDestroy() { disconnected(); super.onDestroy() }
    private fun disconnected() { cancelReceiver?.invoke(); instance = null; connected.value = false; penDown = false; listening = false }
}
