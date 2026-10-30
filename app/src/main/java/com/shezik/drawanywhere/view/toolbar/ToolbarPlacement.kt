package com.shezik.drawanywhere.view.toolbar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/** Keep the eye at its screen position while the surrounding menu changes size. */
internal object ToolbarPlacement {
    fun towardStart(anchor: Offset, screen: IntSize, orientation: ToolbarOrientation): Boolean =
        when (orientation) {
            ToolbarOrientation.HORIZONTAL -> anchor.x > screen.width / 2f
            ToolbarOrientation.VERTICAL -> anchor.y > screen.height / 2f
        }

    fun position(anchor: Offset, localEye: Offset, size: IntSize, screen: IntSize): Offset =
        Offset(
            (anchor.x - localEye.x).coerceIn(0f, (screen.width - size.width).coerceAtLeast(0).toFloat()),
            (anchor.y - localEye.y).coerceIn(0f, (screen.height - size.height).coerceAtLeast(0).toFloat())
        )
}

/** Open tool/color/settings panels on the free side, keeping them inside the window. */
internal class ToolbarPopupPositionProvider(
    private val horizontalToolbar: Boolean,
    private val gap: Int
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x: Int
        val y: Int
        if (horizontalToolbar) {
            x = anchorBounds.center.x - popupContentSize.width / 2
            y = if (anchorBounds.top > windowSize.height - anchorBounds.bottom)
                anchorBounds.top - gap - popupContentSize.height else anchorBounds.bottom + gap
        } else {
            x = if (anchorBounds.left > windowSize.width - anchorBounds.right)
                anchorBounds.left - gap - popupContentSize.width else anchorBounds.right + gap
            y = anchorBounds.center.y - popupContentSize.height / 2
        }
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}
