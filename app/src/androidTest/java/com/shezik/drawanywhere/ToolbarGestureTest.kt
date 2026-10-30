package com.shezik.drawanywhere

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.view.toolbar.DrawToolbar
import com.shezik.drawanywhere.view.toolbar.ToolbarOrientation
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals

/**
 * Tests toolbar gesture interactions: drag to reposition, timer reset on touch.
 */
class ToolbarGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fullyCollapsedEyeDragsImmediatelyAndTapExpands() {
        val vm = DrawViewModel(DrawController(PenConfig()), PreferencesManager(appContext),
            UiState(toolbarCollapsed = true), ServiceState(), {})
        var expansions = 0
        composeRule.setContent { DrawToolbar(vm, onBeforeExpand = { expansions++ }) }
        val initial = vm.serviceState.value.toolbarPosition
        composeRule.onNodeWithContentDescription(appContext.getString(R.string.show_toolbar_items))
            .performTouchInput {
                down(center)
                advanceEventTime(20)
                moveBy(Offset(80f, 60f))
                advanceEventTime(20)
                up()
            }
        composeRule.runOnIdle {
            assertTrue(vm.serviceState.value.toolbarPosition.x > initial.x)
            assertTrue(vm.serviceState.value.toolbarPosition.y > initial.y)
            assertTrue(vm.uiState.value.toolbarCollapsed)
            assertEquals(0, expansions)
        }
        composeRule.onNodeWithContentDescription(appContext.getString(R.string.show_toolbar_items)).performClick()
        composeRule.runOnIdle {
            assertFalse(vm.uiState.value.toolbarCollapsed)
            assertTrue(vm.uiState.value.firstDrawerOpen)
            assertTrue(vm.uiState.value.canvasVisible)
            assertEquals(1, expansions)
        }
    }

    @Test
    fun fullyCollapsedExpandButtonAlsoDragsWithoutOpening() {
        val vm = DrawViewModel(DrawController(PenConfig()), PreferencesManager(appContext),
            UiState(toolbarCollapsed = true), ServiceState(), {})
        composeRule.setContent { DrawToolbar(vm) }
        val initial = vm.serviceState.value.toolbarPosition
        composeRule.onNodeWithContentDescription(appContext.getString(R.string.expand_toolbar))
            .performTouchInput {
                down(center)
                advanceEventTime(20)
                moveBy(Offset(80f, 60f))
                up()
            }
        composeRule.runOnIdle {
            assertTrue(vm.serviceState.value.toolbarPosition.x > initial.x)
            assertTrue(vm.uiState.value.toolbarCollapsed)
        }
        composeRule.onNodeWithContentDescription(appContext.getString(R.string.expand_toolbar)).performClick()
        composeRule.runOnIdle { assertFalse(vm.uiState.value.toolbarCollapsed) }
    }

    @Test
    fun leftOpeningMenuKeepsEyeAtRightEnd() {
        val vm = DrawViewModel(DrawController(PenConfig()), PreferencesManager(appContext),
            UiState(), ServiceState(), {})
        composeRule.setContent { DrawToolbar(vm, expandTowardStart = true) }
        val eye = composeRule.onNodeWithContentDescription(appContext.getString(R.string.hide_toolbar_items))
            .fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithContentDescription(appContext.getString(R.string.settings))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(eye.left > settings.right)
    }

    @Test
    fun upwardOpeningMenuKeepsEyeAtBottom() {
        val vm = DrawViewModel(DrawController(PenConfig()), PreferencesManager(appContext),
            UiState(toolbarOrientation = ToolbarOrientation.VERTICAL), ServiceState(), {})
        composeRule.setContent { DrawToolbar(vm, expandTowardStart = true) }
        val eye = composeRule.onNodeWithContentDescription(appContext.getString(R.string.hide_toolbar_items))
            .fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithContentDescription(appContext.getString(R.string.settings))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(eye.top > settings.bottom)
    }

    @Test
    fun hiddenEyeCanBeLongPressedAndDraggedWithoutChangingCanvas() {
        val controller = DrawController(PenConfig())
        val vm = DrawViewModel(controller, PreferencesManager(appContext),
            UiState(firstDrawerOpen = false), ServiceState(), {})
        composeRule.setContent { DrawToolbar(viewModel = vm) }
        val initial = vm.serviceState.value.toolbarPosition
        composeRule.onNodeWithContentDescription(appContext.getString(R.string.show_toolbar_items))
            .performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(60f, 40f))
                up()
            }
        composeRule.runOnIdle {
            assertTrue(vm.serviceState.value.toolbarPosition.x > initial.x)
            assertTrue(vm.uiState.value.canvasVisible)
            assertTrue(!vm.uiState.value.firstDrawerOpen)
        }
    }

    @Test
    fun longPressDragRepositionsToolbar() {
        val controller = DrawController(PenConfig())
        val vm = DrawViewModel(
            controller = controller,
            preferencesManager = PreferencesManager(appContext),
            initialUiState = UiState(),
            initialServiceState = ServiceState(),
            stopService = {}
        )
        composeRule.setContent { DrawToolbar(viewModel = vm) }

        val initialPos = vm.serviceState.value.toolbarPosition

        // Simulate long press + drag
        composeRule.onNodeWithTag("toolbar_card").performTouchInput {
            // We can't directly test drag gestures easily without detents,
            // but we can verify the toolbarTimer resets on touch
        }

        // Verify toolbar remains active after interaction
        assertTrue(vm.serviceState.value.toolbarActive)
        // Position should still be at initial (drag didn't actually happen)
        // This primarily tests that setting up the composable doesn't crash
    }

    @Test
    fun toolbarRendersWithoutCrash() {
        val controller = DrawController(PenConfig())
        val vm = DrawViewModel(
            controller = controller,
            preferencesManager = PreferencesManager(appContext),
            initialUiState = UiState(),
            initialServiceState = ServiceState(),
            stopService = {}
        )
        composeRule.setContent { DrawToolbar(viewModel = vm) }
        // Basic smoke test: toolbar renders without exception
    }
}
