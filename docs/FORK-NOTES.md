# Toolbar and S Pen fork

This branch changes the toolbar and pen input while retaining the upstream AGPL-3.0 license.

## Toolbar

- Order: eye, undo, redo, clear, pen, color, hand, settings, collapse.
- Eye hides the other controls, leaving a draggable eye. Long-press and drag to move it.
- Collapse leaves eye and expand; expand restores the complete toolbar.
- Both toolbar hiding operations preserve the drawing canvas and its input mode.
- Pen tools, width, opacity and a live line preview share one scrollable panel.
- Presets, recent colors, HSV wheel and hex input share one scrollable color panel.
- The viewport-lock button is removed. The hand button still toggles full input passthrough.

## Simultaneous finger passthrough and S Pen drawing

Requires Android 14 (API 34) or newer:

1. Open toolbar Settings and choose **Enable S Pen input**.
2. In Android Accessibility settings, enable **DrawAnywhere S Pen input**. This is a user-controlled permission and cannot be enabled by the app itself.
3. Return to the canvas, turn **Finger drawing** off, and leave the hand button in drawing mode.
4. Fingers now operate the underlying app; S Pen draws on the overlay. Turn Finger drawing on to draw with either input. Use the hand button for full passthrough, including the pen.

The service subscribes only to `SOURCE_STYLUS` and `SOURCE_BLUETOOTH_STYLUS` through Android's `setMotionEventSources`. Finger events stay in the system's input pipeline; they are not recorded, synthesized or replayed. The service does not request screen-content retrieval, gesture injection or touch exploration. Pen interception stops when full passthrough is selected, the canvas is hidden, finger drawing is enabled, or the drawing service exits. Toolbars and popups receive intercepted pen events in their own local coordinates.

On Android 13 and earlier, or when the accessibility service is disconnected, turning Finger drawing off enables full passthrough; pen drawing is unavailable in that mode. Settings describes this limitation. Enable Finger drawing to use ordinary drawing without the accessibility service.

Android 12+ restricts input through application overlays. During passthrough this fork caps the canvas window alpha to the system's maximum obscuring opacity (normally 0.8). Ink therefore appears slightly lighter in this mode. Apps may independently reject obscured touches. Firmware palm rejection can also suppress finger events while S Pen is near the screen; an app cannot recover events that the device never emits. Validate on the target Samsung device before relying on simultaneous operation.

References: [Android motion event interception](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onMotionEvent(android.view.MotionEvent)), [source selection](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#setMotionEventSources(int)), [overlay touch restrictions](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE).

## Side-button eraser

Holding either stylus side button (or using an eraser-tip tool) temporarily selects Pixel Eraser. Button changes are checked on move and button events, so release restores the selected drawing tool without requiring pen-up. Each uninterrupted eraser segment is one undoable operation. Width uses the saved Pixel Eraser width.

Pixel Eraser cuts line segments at local intersections and sweeps between event samples, including sparsely sampled ink and fast eraser motion. Rectangle/ellipse outlines become editable ink fragments after the first hit. Unaffected strokes retain their original representation; laser fragments retain their original expiry. Undo restores the original geometry and redo restores the cut fragments.

## Validation on a Samsung device

- Finger drawing on: finger ink and S Pen ink; side-button press/release while touching.
- Finger drawing off + service enabled: continuously scroll a long page with a finger while drawing/erasing with S Pen; verify links, page turns and pinch gestures reach the underlying app.
- Repeat with S Pen already hovering before the finger touches down; note any firmware palm rejection.
- Test toolbar and both popups with finger and pen, including long-press dragging the lone eye and dragging while collapsed.
- Confirm eye/collapse do not clear or disable the canvas; check undo/redo after erasure, zoom and rotation.
- Disable/re-enable the accessibility service, switch full passthrough, rotate, quit and restart; verify the underlying app receives the pen after quitting.

Build: use JDK 21 (JetBrains, as pinned by upstream), Android SDK 37, and the included Gradle wrapper. Run `:app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`; run `:app:connectedDebugAndroidTest` on a test device/emulator for instrumentation coverage.
