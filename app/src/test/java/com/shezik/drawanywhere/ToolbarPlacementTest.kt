package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.*
import com.shezik.drawanywhere.view.toolbar.*
import org.junit.Assert.*
import org.junit.Test

class ToolbarPlacementTest {
    @Test fun measureProvidesTheNewAnchorBeforeTheExpandedSurfaceIsPresented() {
        val screen = IntSize(1848, 2934)
        val anchor = Offset(1681f, 2870f)
        for (orientation in ToolbarOrientation.entries) {
            for (length in listOf(224, 910, 224)) {
                val size = if (orientation == ToolbarOrientation.HORIZONTAL) IntSize(length, 126)
                    else IntSize(126, length)
                val eye = ToolbarPlacement.eyeOffset(size, 63f, orientation, true)
                val origin = ToolbarPlacement.position(anchor, eye, size, screen)
                assertEquals(anchor, origin + eye)
                assertTrue(origin.x + size.width <= screen.width)
                assertTrue(origin.y + size.height <= screen.height)
            }
        }
    }

    @Test fun repeatedOpenCloseAtNinePositionsPreservesAnchorAndDirection() {
        val screen = IntSize(1848, 2934)
        for (orientation in ToolbarOrientation.entries) {
            for (x in listOf(100f, 924f, 1740f)) for (y in listOf(150f, 1467f, 2730f)) {
                val anchor = Offset(x, y)
                val reverse = ToolbarPlacement.towardStart(anchor, screen, orientation)
                val horizontal = orientation == ToolbarOrientation.HORIZONTAL
                val compact = if (horizontal) IntSize(224, 126) else IntSize(126, 224)
                val expanded = if (horizontal) IntSize(910, 126) else IntSize(126, 910)
                val compactEye = if (horizontal) Offset(if (reverse) 161f else 63f, 63f)
                    else Offset(63f, if (reverse) 161f else 63f)
                val expandedEye = if (horizontal) Offset(if (reverse) 847f else 63f, 63f)
                    else Offset(63f, if (reverse) 847f else 63f)
                repeat(10) {
                    val openOrigin = ToolbarPlacement.position(anchor, expandedEye, expanded, screen)
                    val closedOrigin = ToolbarPlacement.position(anchor, compactEye, compact, screen)
                    assertEquals("open $orientation $anchor", anchor, openOrigin + expandedEye)
                    assertEquals("close $orientation $anchor", anchor, closedOrigin + compactEye)
                    assertEquals(reverse, ToolbarPlacement.towardStart(closedOrigin + compactEye, screen, orientation))
                }
            }
        }
    }

    @Test fun directionFollowsFreeSpaceOnTheConfiguredAxis() {
        val screen = IntSize(1000, 1600)
        assertTrue(ToolbarPlacement.towardStart(Offset(900f, 100f), screen, ToolbarOrientation.HORIZONTAL))
        assertFalse(ToolbarPlacement.towardStart(Offset(100f, 1500f), screen, ToolbarOrientation.HORIZONTAL))
        assertTrue(ToolbarPlacement.towardStart(Offset(100f, 1500f), screen, ToolbarOrientation.VERTICAL))
        assertFalse(ToolbarPlacement.towardStart(Offset(900f, 100f), screen, ToolbarOrientation.VERTICAL))
    }

    @Test fun expandingLeftAndCollapsingKeepsEyeAtTheSameScreenPoint() {
        val anchor = Offset(950f, 100f)
        val screen = IntSize(1000, 1600)
        val compactEye = Offset(30f, 30f)
        val expandedEye = Offset(830f, 30f)
        assertEquals(anchor, ToolbarPlacement.position(anchor, compactEye, IntSize(60, 60), screen) + compactEye)
        assertEquals(anchor, ToolbarPlacement.position(anchor, expandedEye, IntSize(860, 60), screen) + expandedEye)
    }

    @Test fun expandingUpKeepsEyeAtTheSameScreenPoint() {
        val anchor = Offset(100f, 1550f)
        val eye = Offset(30f, 830f)
        assertEquals(anchor, ToolbarPlacement.position(anchor, eye, IntSize(60, 860), IntSize(1000, 1600)) + eye)
    }

    @Test fun oversizedOrRotatedToolbarStaysInsideAvailableBounds() {
        assertEquals(Offset(0f, 140f), ToolbarPlacement.position(Offset(900f, 1550f),
            Offset(30f, 30f), IntSize(860, 60), IntSize(400, 200)))
    }

    @Test fun panelsOpenAwayFromScreenEdges() {
        val screen = IntSize(1000, 1600)
        val panel = IntSize(300, 400)
        val horizontal = ToolbarPopupPositionProvider(true, 8)
        assertEquals(IntOffset(700, 68), horizontal.calculatePosition(IntRect(940, 0, 1000, 60), screen, LayoutDirection.Ltr, panel))
        assertEquals(IntOffset(0, 1132), horizontal.calculatePosition(IntRect(0, 1540, 60, 1600), screen, LayoutDirection.Ltr, panel))
        val vertical = ToolbarPopupPositionProvider(false, 8)
        assertEquals(68, vertical.calculatePosition(IntRect(0, 600, 60, 660), screen, LayoutDirection.Ltr, panel).x)
        assertEquals(632, vertical.calculatePosition(IntRect(940, 600, 1000, 660), screen, LayoutDirection.Ltr, panel).x)
    }
}
