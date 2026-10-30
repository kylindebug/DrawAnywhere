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

## Finger passthrough with S Pen source routing (device-dependent)

**Strict simultaneous operation remains unresolved. On the Android 17 emulator, starting a separate virtual touchscreen while a virtual pen stroke was down caused the Android input layer to send `ACTION_CANCEL` to the pen. A physical SM-X910 on Android 16 also reported no overlapping pen/finger contacts during a user-performed raw-input test (details below).** Source routing works for separate finger/pen gestures; it cannot reconstruct motion discarded before it reaches the application. An ADB helper alone is not a demonstrated solution for this device.

Requires Android 14 (API 34) or newer:

1. Open toolbar Settings and choose **Enable S Pen input**.
2. In Android Accessibility settings, enable **DrawAnywhere S Pen input**. This is a user-controlled permission and cannot be enabled by the app itself.
3. Return to the canvas, turn **Finger drawing** off, and leave the hand button in drawing mode.
4. Fingers operate the underlying app; S Pen is routed to the overlay. Test overlapping gestures on your device: some systems cancel the pen stream when the finger starts. Turn Finger drawing on to draw with either input. Use the hand button for full passthrough, including the pen.

The service subscribes only to `SOURCE_STYLUS` and `SOURCE_BLUETOOTH_STYLUS` through Android's `setMotionEventSources`. Finger events stay in the system's input pipeline; they are not recorded, synthesized or replayed. The service does not request screen-content retrieval, gesture injection or touch exploration. Pen interception stops when full passthrough is selected, the canvas is hidden, finger drawing is enabled, or the drawing service exits. Toolbars and popups receive intercepted pen events in their own local coordinates.

On Android 13 and earlier, or when the accessibility service is disconnected, turning Finger drawing off enables full passthrough; pen drawing is unavailable in that mode. Settings describes this limitation. Enable Finger drawing to use ordinary drawing without the accessibility service.

Android 12+ restricts input through application overlays. During passthrough this fork caps the canvas window alpha to the system's maximum obscuring opacity (normally 0.8). Ink therefore appears slightly lighter in this mode. Apps may independently reject obscured touches. Firmware palm rejection can suppress finger events while S Pen is near the screen, and Android's input filter can cancel streams when devices change; an app cannot recover discarded events. External displays/DeX are not validated. Validate on the target Samsung device before relying on simultaneous operation.

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

## Recorded validation (2026-10-30)

- 106 JVM unit tests passed, including sparse-line cuts, swept erasure, shape fragments, and exact undo/redo restoration.
- 43 instrumentation tests passed on the Android 17/API 37.2 emulator, including side-button transitions and dragging the hidden eye. Espresso was updated from the old transitive 3.5.0 to 3.7.0 to support the emulator.
- GitHub Actions build and instrumentation jobs passed on commit `e884051` (compile SDK 37.0, emulator API 35).
- Visual checks confirmed the requested toolbar order and merged pen controls/line preview on a tablet-size display.
- A virtual hardware pen created with Android `uinput` reached the accessibility service and drew ink while Finger drawing was off. Ordinary `adb shell input stylus` injection bypasses that filter and is not evidence for this route.
- Overlapping independent `uinput` pen and finger streams produced a pen cancellation on Android 17. This is an unresolved limitation of requirement 2, not a passing simultaneous-input test.

## Physical input diagnostic (2026-10-30)

- Device: Samsung SM-X910, Android 16 / API 36 (36.1), `ro.build.version.oneui=80500`, security patch 2026-08-05. Production build, locked verified boot; no `su` found in the common checked locations.
- Separate evdev nodes: `sec_e-pen` and `sec_touchscreen`. ADB shell could read both and successfully acquire/release `EVIOCGRAB` on the pen. Only the pen was grabbed; the touchscreen remained available to Android.
- During a 60-second test the user was asked to move S Pen while sliding another finger, in both contact orders, and confirmed completing the test. A probe counted `SYN_REPORT` frames with `BTN_TOUCH` active without storing coordinates: **1,602 pen-contact frames, 329 finger-contact frames, zero overlapping contact frames on either stream**.
- This indicates suppression before the app-level input route in this test. It does not identify the exact firmware/driver component, establish behavior for every Samsung device, or prove that rooting would fix it. No applicable switch was identified in the inspected settings and exposed driver command lists.
- The probe released the pen and was removed. No system settings, original app installation, or app data were changed. This was a hardware input diagnostic, not an installation or end-to-end validation of this APK on Samsung hardware.
- The final application commit `0985eab` also passed [GitHub Actions build and API 35 instrumentation](https://github.com/kylindebug/DrawAnywhere/actions/runs/40994348377).
