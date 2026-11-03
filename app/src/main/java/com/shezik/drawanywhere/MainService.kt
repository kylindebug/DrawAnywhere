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
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shezik.drawanywhere.view.DismissTargetView
import com.shezik.drawanywhere.view.ToolbarLifecycleOwner
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import com.shezik.drawanywhere.view.canvas.CanvasInputWindow
import com.shezik.drawanywhere.view.canvas.canvasInputState
import com.shezik.drawanywhere.view.toolbar.DrawToolbar
import com.shezik.drawanywhere.view.toolbar.ToolbarPlacement
import com.shezik.drawanywhere.view.toolbar.ToolbarOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "default_channel"
        internal const val OVERLAY_INPUT_FLAGS = LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_SPLIT_TOUCH
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
    private var toolbarExpandsTowardStart by mutableStateOf(false)
    private var eyeInWindow: Offset? = null
    private var measuredToolbarSize = IntSize.Zero
    private lateinit var toolbarParams: LayoutParams

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
            OVERLAY_INPUT_FLAGS or
                    LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val canvasInput = CanvasInputWindow(windowManager, canvasView, canvasParams, initialUiState.canvasInputState)

        // -------- Setup toolbar (Compose) --------
        toolbarLifecycleOwner.start()
        chooseToolbarExpansion()
        toolbarView = ComposeView(this).apply {
            // A docked toolbar must remain draggable away from the edge instead
            // of having its horizontal gesture intercepted as system Back.
            if (Build.VERSION.SDK_INT >= 29) addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                view.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, view.width, view.height))
            }
            setContent { DrawToolbar(
                viewModel = viewModel,
                expandTowardStart = toolbarExpandsTowardStart,
                onBeforeExpand = ::chooseToolbarExpansion,
                onDragFinished = ::snapToolbarToEdge,
                onGeometryMeasured = ::onToolbarMeasured,
                onDragPosition = { local ->
                    viewModel.onDismissDragMove(local + toolbarWindowOrigin() -
                        viewModel.serviceState.value.toolbarPosition)
                }
            ) }
        }
        toolbarLifecycleOwner.attachTo(toolbarView)

        toolbarParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            OVERLAY_INPUT_FLAGS or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            windowAnimations = 0
            // Position animations are separate from enter/exit animations.
            // Resizing an end-anchored window also moves its top-left corner.
            if (Build.VERSION.SDK_INT >= 34) setCanPlayMoveAnimation(false)
        }

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

        // Split touch lets the toolbar receive a new pointer while a palm is
        // still on the canvas. Tool/color changes do not alter either window.
        serviceScope.launch {
            viewModel.uiState.map { it.canvasInputState }.distinctUntilChanged().collect(canvasInput::apply)
        }

        // Observe service state changes
        serviceScope.launch {
            viewModel.serviceState.map { it.toolbarPosition }.distinctUntilChanged().collect {
                applyToolbarPosition(toolbarParams, viewModel.serviceState.value)
                windowManager.updateViewLayout(toolbarView, toolbarParams)
            }
        }
        serviceScope.launch {
            viewModel.serviceState.map { it.toolbarActive }.distinctUntilChanged().collect { active ->
                val targetAlpha = if (active) 1.0f else DrawViewModel.TOOLBAR_DIM_ALPHA
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

    private fun applyToolbarPosition(params: LayoutParams, state: ServiceState) {
        val (width, height) = getUsableScreenSize(windowManager)
        val position = ToolbarPlacement.position(state.toolbarPosition, Offset.Zero,
            measuredToolbarSize, IntSize(width, height))
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = position.x.toInt()
        params.y = position.y.toInt()
        if (position != state.toolbarPosition) viewModel.setToolbarPosition(position)
    }

    private fun chooseToolbarExpansion() { /* Three fixed buttons never reverse order. */ }

    private fun snapToolbarToEdge() {
        val (width, height) = getUsableScreenSize(windowManager)
        viewModel.setToolbarPosition(ToolbarPlacement.snap(viewModel.serviceState.value.toolbarPosition,
            measuredToolbarSize, IntSize(width, height), 24f * resources.displayMetrics.density))
        viewModel.saveToolbarPosition()
    }

    private fun toolbarWindowOrigin(): Offset {
        if (::toolbarView.isInitialized && toolbarView.isAttachedToWindow) {
            val location = IntArray(2)
            toolbarView.getLocationOnScreen(location)
            return Offset(location[0].toFloat(), location[1].toFloat())
        }
        return Offset(toolbarParams.x.toFloat(), toolbarParams.y.toFloat())
    }

    private fun onToolbarMeasured(localEye: Offset, size: IntSize) {
        if (eyeInWindow == localEye && measuredToolbarSize == size) return
        eyeInWindow = localEye
        measuredToolbarSize = size
        if (toolbarView.isAttachedToWindow) {
            applyToolbarPosition(toolbarParams, viewModel.serviceState.value)
            windowManager.updateViewLayout(toolbarView, toolbarParams)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::toolbarView.isInitialized && toolbarView.isAttachedToWindow) {
            applyToolbarPosition(toolbarParams, viewModel.serviceState.value)
            windowManager.updateViewLayout(toolbarView, toolbarParams)
            chooseToolbarExpansion()
        }
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
