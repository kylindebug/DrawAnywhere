package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.util.scrollFadingEdges
import com.shezik.drawanywhere.ui.theme.DrawAnywhereTheme
import com.shezik.drawanywhere.ui.theme.Spacing

enum class ToolbarOrientation { HORIZONTAL, VERTICAL }

@Composable
fun DrawToolbar(
    viewModel: DrawViewModel,
    modifier: Modifier = Modifier,
    expandTowardStart: Boolean = false,
    onBeforeExpand: () -> Unit = {},
    onEyeLayout: (Offset, IntSize) -> Unit = { _, _ -> },
    onDragPosition: ((Offset) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val canClearCanvas by viewModel.canClearCanvas.collectAsState()

    val haptics = LocalHapticFeedback.current
    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()
    val compact = uiState.toolbarCollapsed || !uiState.firstDrawerOpen

    val allButtonsMap = createAllToolbarButtons(
        uiState = uiState,
        canUndo = canUndo,
        canRedo = canRedo,
        canClearCanvas = canClearCanvas,
        onCanvasVisibilityToggle = {
            if (compact) onBeforeExpand()
            viewModel.toggleFirstDrawer()
        },
        onCanvasPassthroughToggle = viewModel::toggleCanvasPassthrough,
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
        BoxWithConstraints {
            DraggableToolbarCard(
                modifier = modifier
                    .wrapContentSize(unbounded = true)
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight)
                    .scrollFadingEdges(hScrollState, false)
                    .scrollFadingEdges(vScrollState, true)
                    .horizontalScroll(hScrollState, enabled = !compact)
                    .verticalScroll(vScrollState, enabled = !compact)
                    .padding(Spacing.xs),
                haptics = haptics,
                onPositionChange = viewModel::updateToolbarPosition,
                onPositionSaved = { viewModel.saveToolbarPosition() },
                onToolbarInteracted = viewModel::resetToolbarTimer,
                dragImmediately = compact,
                onDragStart = viewModel::onDismissDragStart,
                onDragPosition = onDragPosition ?: viewModel::onDismissDragMove,
                onDragEnd = viewModel::onDismissDragEnd,
            ) {
                ToolbarButtonsContainer(
                    modifier = Modifier.padding(Spacing.sm),
                    uiState = uiState,
                    allButtonsMap = allButtonsMap,
                    onExpandToggleClick = {
                        if (compact) onBeforeExpand()
                        viewModel.toggleToolbarCollapsed()
                    },
                    expandTowardStart = expandTowardStart,
                    onEyeLayout = onEyeLayout
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
    expandTowardStart: Boolean = false,
    onEyeLayout: (Offset, IntSize) -> Unit = { _, _ -> }
) {
    val orientation = uiState.toolbarOrientation
    val isFirstDrawerOpen = uiState.firstDrawerOpen && !uiState.toolbarCollapsed
    val isSecondDrawerOpen = uiState.secondDrawerOpen
    val firstDrawerButtonIds = uiState.firstDrawerButtons
    val secondDrawerButtonIds = uiState.secondDrawerButtons
    val secondDrawerPinnedButtons = uiState.secondDrawerPinnedButtons

    val standaloneButtonIds = allButtonsMap.keys.filter {
        it !in firstDrawerButtonIds && it !in secondDrawerButtonIds
    }
    val arrangement = Arrangement.spacedBy(Spacing.sm)
    val popupAlignment = when (orientation) {
        ToolbarOrientation.HORIZONTAL -> Alignment.TopCenter
        ToolbarOrientation.VERTICAL -> Alignment.CenterEnd
    }
    val isDividerVisible = isFirstDrawerOpen &&
        (secondDrawerPinnedButtons.isNotEmpty() || (isSecondDrawerOpen && secondDrawerButtonIds.isNotEmpty()))
    val isExpandButtonVisible = uiState.firstDrawerOpen || uiState.toolbarCollapsed

    val isHorizontal = orientation == ToolbarOrientation.HORIZONTAL

    @Composable
    fun StandaloneButtons() {
        standaloneButtonIds.forEach { id -> allButtonsMap[id]?.let {
            RenderButton(it, popupAlignment, if (id == "visibility") Modifier.onGloballyPositioned { c ->
                onEyeLayout(c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f),
                    c.findRootCoordinates().size)
            } else Modifier)
        } }
    }

    @Composable
    fun FirstDrawer() {
        (if (expandTowardStart) firstDrawerButtonIds.reversed() else firstDrawerButtonIds).forEach { id ->
            allButtonsMap[id]?.let { btn ->
                if (isFirstDrawerOpen) RenderButton(btn, popupAlignment)
            }
        }
    }
    @Composable
    fun Divider() {
        if (isDividerVisible) {
            if (isHorizontal) VerticalDivider(
                Modifier.height(24.dp).padding(horizontal = Spacing.sm), thickness = 2.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            else HorizontalDivider(
                Modifier.width(24.dp).padding(vertical = Spacing.sm), thickness = 2.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
    @Composable
    fun SecondDrawer() {
        (if (expandTowardStart) secondDrawerButtonIds.reversed() else secondDrawerButtonIds).forEach { id ->
            allButtonsMap[id]?.let { btn ->
                val visible = isFirstDrawerOpen && (isSecondDrawerOpen || id in secondDrawerPinnedButtons)
                if (visible) RenderButton(btn, popupAlignment)
            }
        }
    }
    @Composable
    fun ExpandButton() {
        if (isExpandButtonVisible)
            ToolbarExpandButton(Modifier, !uiState.toolbarCollapsed, onExpandToggleClick, orientation, expandTowardStart)
    }
    @Composable
    fun ToolbarContent() {
        if (expandTowardStart) {
            ExpandButton(); SecondDrawer(); Divider(); FirstDrawer(); StandaloneButtons()
        } else {
            StandaloneButtons(); FirstDrawer(); Divider(); SecondDrawer(); ExpandButton()
        }
    }

    if (isHorizontal) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = arrangement) {
            ToolbarContent()
        }
    } else {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = arrangement) {
            ToolbarContent()
        }
    }
}
