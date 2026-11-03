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
    @Test fun clearButtonWorksBeforePenUpAndWithSmallTapMovement() {
        val c = DrawController(PenConfig())
        val vm = DrawViewModel(c, PreferencesManager(context), UiState(), ServiceState(), {})
        rule.setContent { DrawToolbar(vm) }
        repeat(10) {
            rule.runOnIdle {
                vm.startStroke(Offset.Zero, com.shezik.drawanywhere.model.StrokeModifier.None)
                vm.updateStroke(Offset(100f, 100f))
            }
            rule.onNodeWithContentDescription(context.getString(R.string.clear_canvas))
                .assertIsEnabled().performTouchInput {
                    down(center); moveBy(Offset(2f, 1f)); up()
                }
            rule.runOnIdle {
                assertTrue(c.strokeList.isEmpty())
                vm.updateStroke(Offset(200f, 200f)); vm.finishStroke()
                assertTrue(c.strokeList.isEmpty())
                assertEquals(Offset(32f, 64f), vm.serviceState.value.toolbarPosition)
            }
        }
    }
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
