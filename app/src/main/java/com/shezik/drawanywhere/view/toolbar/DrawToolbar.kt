package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalHapticFeedback
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.ui.theme.DrawAnywhereTheme
import com.shezik.drawanywhere.ui.theme.Spacing

enum class ToolbarOrientation { HORIZONTAL, VERTICAL }

@Composable
fun DrawToolbar(
    viewModel: DrawViewModel,
    modifier: Modifier = Modifier,
    expandTowardStart: Boolean = false,
    onBeforeExpand: () -> Unit = {},
    onGeometryMeasured: (Offset, IntSize) -> Unit = { _, _ -> },
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragFinished: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val canClearCanvas by viewModel.canClearCanvas.collectAsState()

    val haptics = LocalHapticFeedback.current

    val allButtonsMap = createAllToolbarButtons(
        uiState = uiState,
        canUndo = canUndo,
        canRedo = canRedo,
        canClearCanvas = canClearCanvas,
        onCanvasVisibilityToggle = {
            viewModel.toggleFirstDrawer()
        },
        onCanvasPassthroughToggle = {
            if (!uiState.canvasVisible) {
                viewModel.setCanvasVisibility(true)
                if (uiState.canvasPassthrough) viewModel.toggleCanvasPassthrough()
            } else viewModel.toggleCanvasPassthrough()
        },
        onClearCanvas = viewModel::clearCanvas,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onPenTypeSwitch = viewModel::switchToPen,
        onColorChange = viewModel::setPenColor,
        onPresetColorChange = viewModel::setPresetColor,
        onStrokeWidthChange = viewModel::setStrokeWidth,
        onAlphaChange = viewModel::setStrokeAlpha,
        onChangeOrientation = {
            viewModel.setToolbarOrientation(it)
            onBeforeExpand()
        },
        onChangeAutoClearCanvas = viewModel::setAutoClearCanvas,
        onChangeVisibleOnStart = viewModel::setVisibleOnStart,
        fingerDrawingEnabled = uiState.fingerDrawingEnabled,
        onChangeFingerDrawingEnabled = viewModel::setFingerDrawingEnabled,
        onQuitApplication = viewModel::quitApplication
    ).associateBy { it.id }

    DrawAnywhereTheme {
        BoxWithConstraints(Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val size = IntSize(placeable.width, placeable.height)
            // Window position must be ready during measure, before ViewRoot
            // resizes/presents the surface. A global-position callback is too late:
            // it can show the wider toolbar at the old compact origin for a frame.
            onGeometryMeasured(Offset.Zero, size)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }) {
            DraggableToolbarCard(
                modifier = modifier
                    .wrapContentSize(unbounded = true)
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight)
                    .padding(Spacing.xs),
                haptics = haptics,
                onPositionChange = viewModel::updateToolbarPosition,
                onPositionSaved = { onDragFinished?.invoke() ?: viewModel.saveToolbarPosition() },
                onToolbarInteracted = viewModel::resetToolbarTimer,
                dragImmediately = true,
            ) {
                ToolbarButtonsContainer(
                    modifier = Modifier.padding(Spacing.sm),
                    uiState = uiState,
                    allButtonsMap = allButtonsMap,
                    onExpandToggleClick = {
                                    viewModel.toggleToolbarCollapsed()
                    },
                    expandTowardStart = expandTowardStart
                )
            }
        }
    }
}

@Composable
internal fun ToolbarButtonsContainer(
    modifier: Modifier = Modifier,
    uiState: com.shezik.drawanywhere.UiState,
    allButtonsMap: Map<String, ToolbarButton>,
    onExpandToggleClick: () -> Unit,
    expandTowardStart: Boolean = false
) {
    val alignment = if (uiState.toolbarOrientation == ToolbarOrientation.HORIZONTAL)
        Alignment.TopCenter else Alignment.CenterEnd
    @Composable fun Buttons() {
        RenderButton(allButtonsMap.getValue("passthrough"), alignment)
        RenderButton(allButtonsMap.getValue("clear"), alignment)
        RenderButton(allButtonsMap.getValue("settings").copy(popupPages = listOf({
            SimpleSettings(allButtonsMap)
        })), alignment)
    }
    if (uiState.toolbarOrientation == ToolbarOrientation.HORIZONTAL) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { Buttons() }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { Buttons() }
    }
}
