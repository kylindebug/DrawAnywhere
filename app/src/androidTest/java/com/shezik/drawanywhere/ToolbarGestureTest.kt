package com.shezik.drawanywhere

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.view.toolbar.DrawToolbar
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ToolbarGestureTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun setup(): DrawViewModel {
        val vm = DrawViewModel(DrawController(PenConfig()), PreferencesManager(context),
            UiState(toolbarCollapsed = true, firstDrawerOpen = false), ServiceState(), {})
        rule.setContent { DrawToolbar(vm) }
        return vm
    }
    @Test fun threeButtonsIgnoreOldCollapsedPreferenceAndTogglePenMode() {
        val vm = setup()
        rule.onAllNodes(hasClickAction()).assertCountEquals(3)
        rule.onNodeWithContentDescription(context.getString(R.string.pen_enabled)).performClick()
        rule.runOnIdle { assertTrue(vm.uiState.value.canvasPassthrough) }
        rule.onNodeWithContentDescription(context.getString(R.string.pen_disabled)).performClick()
        rule.runOnIdle { assertFalse(vm.uiState.value.canvasPassthrough) }
        rule.onNodeWithContentDescription(context.getString(R.string.expand_toolbar)).assertDoesNotExist()
    }
    @Test fun draggingModeButtonMovesWithoutToggling() {
        val vm = setup()
        val initial = vm.serviceState.value.toolbarPosition
        rule.onNodeWithContentDescription(context.getString(R.string.pen_enabled)).performTouchInput {
            down(center); advanceEventTime(20); moveBy(Offset(80f, 60f)); up()
        }
        rule.runOnIdle {
            assertTrue(vm.serviceState.value.toolbarPosition.x > initial.x)
            assertFalse(vm.uiState.value.canvasPassthrough)
        }
    }
    @Test fun settingsContainsUndoRedoAndPenControls() {
        setup()
        rule.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.undo)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.redo)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.color_picker)).assertExists()
    }
}
