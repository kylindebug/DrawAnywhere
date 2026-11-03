package com.shezik.drawanywhere

import com.shezik.drawanywhere.view.canvas.FingerModeGesture
import org.junit.Assert.*
import org.junit.Test

class FingerModeGestureTest {
    @Test fun stationaryJitterDoesNotAccumulateIntoASwitch() {
        val gesture = FingerModeGesture(48f)
        gesture.down(100f, 100f)
        repeat(100) { assertFalse(gesture.move(100f + it % 4, 102f)) }
    }
    @Test fun deliberateDisplacementTriggersOnlyOnce() {
        val gesture = FingerModeGesture(48f)
        gesture.down(100f, 100f)
        assertFalse(gesture.move(147f, 100f))
        assertTrue(gesture.move(148f, 100f))
        assertFalse(gesture.move(250f, 100f))
    }
    @Test fun canceledPalmOrMultitouchCannotCompleteGesture() {
        val gesture = FingerModeGesture(48f)
        gesture.down(100f, 100f)
        gesture.cancel()
        assertFalse(gesture.move(500f, 100f))
    }
    @Test fun newContactUsesItsOwnOrigin() {
        val gesture = FingerModeGesture(48f)
        gesture.down(0f, 0f)
        gesture.down(500f, 500f)
        assertFalse(gesture.move(510f, 500f))
        assertTrue(gesture.move(500f, 450f))
    }
}
