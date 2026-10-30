/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025-2026 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.input.InputManager
import android.view.MotionEvent
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.round
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shezik.drawanywhere.view.DismissTargetView
import com.shezik.drawanywhere.view.ToolbarLifecycleOwner
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import com.shezik.drawanywhere.view.toolbar.DrawToolbar
import com.shezik.drawanywhere.input.StylusInputBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.combine

class MainService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "default_channel"
        var isRunning: Boolean = false
            private set
    }

    private val toolbarLifecycleOwner = ToolbarLifecycleOwner()
    private lateinit var drawController: DrawController
    private lateinit var windowManager: WindowManager
    private lateinit var canvasView: NativeDrawCanvasView
    private lateinit var toolbarView: ComposeView
    private lateinit var dismissTargetView: DismissTargetView
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: DrawViewModel
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stylusTarget: View? = null
    private var splitInputActive = false
    private var previousInputState: Triple<Boolean, Boolean, Boolean>? = null

    override fun onCreate() {
        super.onCreate()

        preferencesManager = PreferencesManager(this)
        val (initialUiState, initialServiceState) = runBlocking {
            preferencesManager.getSavedUiState() to preferencesManager.getSavedServiceState()
        }
        drawController = DrawController(initialUiState.currentPenConfig)
        viewModel = DrawViewModel(
            controller = drawController,
            preferencesManager = preferencesManager,
            initialUiState = initialUiState,
            initialServiceState = initialServiceState,
            stopService = { stopSelf() },
            containsDismissTarget = { x, y -> dismissTargetView.containsScreenPoint(x, y) },
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        // -------- Setup native canvas --------
        canvasView = NativeDrawCanvasView(this, drawController, viewModel)
        drawController.onStrokesChanged = { canvasView.invalidate() }

        val canvasParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        applyCanvasPassthrough(canvasParams, initialUiState.canvasPassthrough || !initialUiState.fingerDrawingEnabled)

        // -------- Setup toolbar (Compose) --------
        toolbarLifecycleOwner.start()
        toolbarView = ComposeView(this).apply {
            setContent { DrawToolbar(viewModel = viewModel) }
        }
        toolbarLifecycleOwner.attachTo(toolbarView)

        val toolbarParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        applyToolbarPosition(toolbarParams, initialServiceState)
        // ---------------------------------------

        // -------- Setup dismiss target (shown when dragging toolbar) --------
        dismissTargetView = DismissTargetView(this)
        val dismissSize = (DismissTargetView.SIZE_DP * resources.displayMetrics.density).toInt()
        val dismissParams = LayoutParams(
            dismissSize, dismissSize,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (DismissTargetView.BOTTOM_OFFSET_DP * resources.displayMetrics.density).toInt()
        }
        dismissTargetView.visibility = View.GONE
        // --------------------------------------------------------------------

        windowManager.addView(canvasView, canvasParams)
        windowManager.addView(toolbarView, toolbarParams)
        windowManager.addView(dismissTargetView, dismissParams)

        // Defer toolbar position validation until layout is complete
        toolbarView.post {
            applyToolbarPosition(toolbarParams, viewModel.serviceState.value)
            windowManager.updateViewLayout(toolbarView, toolbarParams)
        }

        // Observe UI state changes
        serviceScope.launch {
            combine(viewModel.uiState, StylusInputBridge.connected) { state, connected -> state to connected }
                .collect { (state, connected) ->
                val splitInput = connected && !state.fingerDrawingEnabled &&
                    !state.canvasPassthrough && state.canvasVisible
                val inputState = Triple(state.canvasVisible, state.canvasPassthrough, state.fingerDrawingEnabled)
                if (splitInputActive != splitInput || previousInputState != inputState) {
                    canvasView.cancelInput()
                    stylusTarget = null
                    splitInputActive = splitInput
                    previousInputState = inputState
                }
                StylusInputBridge.setListener(if (splitInput) ::dispatchStylusEvent else null)
                applyCanvasPassthrough(canvasParams, state.canvasPassthrough || !state.fingerDrawingEnabled)
                windowManager.updateViewLayout(canvasView, canvasParams)
                canvasView.visibility = if (state.canvasVisible) View.VISIBLE else View.GONE
            }
        }

        // Observe service state changes
        serviceScope.launch {
            viewModel.serviceState.collect { state ->
                applyToolbarPosition(toolbarParams, state)
                windowManager.updateViewLayout(toolbarView, toolbarParams)

                val targetAlpha = if (state.toolbarActive) 1.0f else DrawViewModel.TOOLBAR_DIM_ALPHA
                toolbarView.animate()
                    .alpha(targetAlpha)
                    .setDuration(DrawViewModel.TOOLBAR_DIM_DURATION_MS)
                    .start()
            }
        }

        // Observe dismiss target
        serviceScope.launch {
            viewModel.dismissTarget.collect { target ->
                when (target) {
                    is DismissTarget.Hidden -> dismissTargetView.hide()
                    is DismissTarget.Visible -> {
                        dismissTargetView.active = target.active
                        dismissTargetView.show()
                    }
                }
            }
        }

        isRunning = true
    }

    private fun applyCanvasPassthrough(params: LayoutParams, passthrough: Boolean) {
        params.flags = if (passthrough)
            params.flags or LayoutParams.FLAG_NOT_TOUCHABLE
        else
            params.flags and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        // Android 12+ blocks touches through opaque application overlays. Respect
        // the system limit so the real finger stream reaches the underlying app.
        params.alpha = if (passthrough && Build.VERSION.SDK_INT >= 31)
            getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        else 1f
    }

    private fun contains(view: View, event: MotionEvent): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return event.x >= location[0] && event.x < location[0] + view.width &&
            event.y >= location[1] && event.y < location[1] + view.height
    }

    private fun dispatchStylusEvent(event: MotionEvent) {
        if (Build.VERSION.SDK_INT >= 29 && event.displayId != canvasView.display?.displayId) return
        if ((0 until event.pointerCount).none {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        }) return
        val hover = event.actionMasked == MotionEvent.ACTION_HOVER_ENTER ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_EXIT
        if (event.actionMasked == MotionEvent.ACTION_DOWN || hover) {
            val popup = StylusInputBridge.topPopup()
            stylusTarget = when {
                popup != null && contains(popup.first, event) -> popup.first
                popup != null -> {
                    if (!hover) popup.second.invoke()
                    null // Dismiss without drawing behind the popup.
                }
                contains(toolbarView, event) -> toolbarView
                else -> canvasView
            }
        }
        val target = stylusTarget ?: return
        if (!target.isAttachedToWindow) { stylusTarget = null; return }
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val local = MotionEvent.obtain(event)
        try {
            local.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
            if (target === canvasView) canvasView.onTouchEvent(local)
            else if (hover || event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS ||
                event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) target.dispatchGenericMotionEvent(local)
            else target.dispatchTouchEvent(local)
        } finally { local.recycle() }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
            stylusTarget = null
    }

    private fun applyToolbarPosition(params: LayoutParams, state: ServiceState) {
        val rounded = state.toolbarPosition.round()
        val (screenWidth, screenHeight) = getUsableScreenSize(windowManager)
        params.x = rounded.x.coerceIn(0, (screenWidth - toolbarView.width).coerceAtLeast(0))
        params.y = rounded.y.coerceIn(0, (screenHeight - toolbarView.height).coerceAtLeast(0))
        viewModel.setToolbarPosition(
            Offset(params.x.toFloat(), params.y.toFloat())
        )
    }

    @Suppress("DEPRECATION")
    private fun getUsableScreenSize(wm: WindowManager): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
            val b = metrics.bounds
            (b.width() - insets.left - insets.right) to (b.height() - insets.top - insets.bottom)
        } else {
            val size = Point()
            wm.defaultDisplay.getSize(size)
            size.x to size.y
        }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        StylusInputBridge.setListener(null)
        if (::canvasView.isInitialized) canvasView.cancelInput()
        serviceScope.cancel()
        if (::toolbarView.isInitialized && toolbarView.isAttachedToWindow)
            windowManager.removeView(toolbarView)
        if (::canvasView.isInitialized && canvasView.isAttachedToWindow)
            windowManager.removeView(canvasView)
        if (::dismissTargetView.isInitialized && dismissTargetView.isAttachedToWindow)
            windowManager.removeView(dismissTargetView)
        toolbarLifecycleOwner.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
