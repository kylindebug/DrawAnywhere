package com.shezik.drawanywhere.view.toolbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.shezik.drawanywhere.R
import com.shezik.drawanywhere.UiState
import com.shezik.drawanywhere.model.PenType

internal data class ToolbarButton(
    val id: String,
    val icon: ImageVector,
    val color: Color? = null,
    val contentDescription: String,
    val isEnabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val popupPages: List<@Composable () -> Unit> = emptyList(),
    val iconContent: (@Composable () -> Unit)? = null,
) {
    val hasPopup: Boolean
        get() = popupPages.isNotEmpty()
}

@Composable
internal fun createAllToolbarButtons(
    uiState: UiState,
    canUndo: Boolean,
    canRedo: Boolean,
    canClearCanvas: Boolean,
    onCanvasVisibilityToggle: () -> Unit,
    onCanvasPassthroughToggle: () -> Unit,
    onClearCanvas: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPenTypeSwitch: (PenType) -> Unit,
    onColorChange: (Color) -> Unit,
    onPresetColorChange: (Color) -> Unit = onColorChange,
    onStrokeWidthChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onChangeOrientation: (ToolbarOrientation) -> Unit,
    onChangeAutoClearCanvas: (Boolean) -> Unit,
    onChangeVisibleOnStart: (Boolean) -> Unit,
    fingerDrawingEnabled: Boolean,
    onChangeFingerDrawingEnabled: (Boolean) -> Unit,
    onQuitApplication: () -> Unit
): List<ToolbarButton> = listOf(
    ToolbarButton(
        id = "visibility",
        icon = if (uiState.firstDrawerOpen && !uiState.toolbarCollapsed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
        contentDescription = if (uiState.firstDrawerOpen && !uiState.toolbarCollapsed) stringResource(R.string.hide_toolbar_items) else stringResource(R.string.show_toolbar_items),
        onClick = onCanvasVisibilityToggle
    ),
    ToolbarButton(
        id = "undo",
        icon = Icons.AutoMirrored.Filled.Undo,
        contentDescription = stringResource(R.string.undo),
        isEnabled = uiState.canvasVisible && canUndo,
        onClick = onUndo
    ),
    ToolbarButton(
        id = "clear",
        icon = if (canClearCanvas) Icons.Filled.Delete else Icons.Outlined.Delete,
        contentDescription = stringResource(R.string.clear_canvas),
        isEnabled = uiState.canvasVisible && canClearCanvas,
        onClick = onClearCanvas
    ),
    ToolbarButton(
        id = "tool_controls",
        icon = uiState.currentPenType.icon,
        contentDescription = stringResource(R.string.tool_controls),
        popupPages = listOf(
            { Column {
                PenTypeSelector(currentPenType = uiState.currentPenType, onPenTypeSwitch = onPenTypeSwitch)
                PenControls(penConfig = uiState.currentPenConfig, onStrokeWidthChange = onStrokeWidthChange, onAlphaChange = onAlphaChange, alphaEnabled = !uiState.currentPenType.isEraser)
            } }
        )
    ),
    ToolbarButton(
        id = "color_picker",
        icon = Icons.Default.Palette,
        color = if (!uiState.currentPenType.isEraser) uiState.currentPenConfig.color else null,
        contentDescription = stringResource(R.string.color_picker),
        isEnabled = !uiState.currentPenType.isEraser,
        popupPages = listOf(
            { ColorPicker(
                selectedColor = uiState.currentPenConfig.color,
                onColorSelected = onColorChange,
                onPresetSelected = onPresetColorChange,
                recentColors = uiState.recentColors
            ) }
        )
    ),
    ToolbarButton(
        id = "passthrough",
        icon = if (uiState.canvasPassthrough) Icons.Default.DoNotTouch else Icons.Default.TouchApp,
        contentDescription = if (uiState.canvasPassthrough) stringResource(R.string.disable_passthrough) else stringResource(R.string.enable_passthrough),
        isEnabled = uiState.canvasVisible,
        onClick = onCanvasPassthroughToggle
    ),
    ToolbarButton(
        id = "redo",
        icon = Icons.AutoMirrored.Filled.Redo,
        contentDescription = stringResource(R.string.redo),
        isEnabled = uiState.canvasVisible && canRedo,
        onClick = onRedo
    ),
    ToolbarButton(
        id = "settings",
        icon = Icons.Default.Tune,
        contentDescription = stringResource(R.string.settings),
        popupPages = listOf(
            { ToolbarControls(
                currentOrientation = uiState.toolbarOrientation,
                onChangeOrientation = onChangeOrientation,
                autoClearCanvas = uiState.autoClearCanvas,
                onChangeAutoClearCanvas = onChangeAutoClearCanvas,
                visibleOnStart = uiState.visibleOnStart,
                onChangeVisibleOnStart = onChangeVisibleOnStart,
                fingerDrawingEnabled = fingerDrawingEnabled,
                onChangeFingerDrawingEnabled = onChangeFingerDrawingEnabled,
                onQuitApplication = onQuitApplication
            ) },
            { AboutScreen() }
        )
    )
)
