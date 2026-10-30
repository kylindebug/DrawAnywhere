# Toolbar and S Pen fork

This branch changes the toolbar and pen input while retaining the upstream AGPL-3.0 license.

The current deployment includes toolbar changes and local side-button erasure. Requirement 2 (simultaneous pen drawing and finger passthrough) is deferred; ordinary drawing/passthrough modes are retained.

## Toolbar

- Order: eye, undo, redo, clear, pen, color, hand, settings, collapse.
- Eye hides the other controls, leaving a draggable eye. Long-press and drag to move it.
- Collapse leaves eye and expand; expand restores the complete toolbar.
- Both toolbar hiding operations preserve the drawing canvas and its input mode.
- Pen tools, width, opacity and a live line preview share one scrollable panel.
- Presets, recent colors, HSV wheel and hex input share one scrollable color panel.
- The viewport-lock button is removed. The hand button still toggles full input passthrough.

## Drawing and app interaction modes

Simultaneous S Pen drawing and finger passthrough is **deferred at the user's request** after the SM-X910 input diagnostic below. The experimental accessibility service, source-routing bridge, and enable-service UI have been removed from this build.

- Drawing mode: S Pen draws on the overlay. Finger drawing on allows finger ink too; off ignores fingers while retaining S Pen drawing.
- Hand button: switches the entire canvas between drawing and passthrough. In passthrough mode both pen and finger operate the underlying app.
- No accessibility service or ADB helper is needed. Only the normal overlay permission is required.

Android 12+ restricts input through application overlays. In passthrough mode this fork caps the canvas window alpha to the system's maximum obscuring opacity (normally 0.8), so ink appears slightly lighter. See [Android overlay touch restrictions](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE). External displays/DeX remain unvalidated.

## Side-button eraser

Holding either stylus side button (or using an eraser-tip tool) temporarily selects Pixel Eraser. Button changes are checked on move and button events, so release restores the selected drawing tool without requiring pen-up. Each uninterrupted eraser segment is one undoable operation. Width uses the saved Pixel Eraser width.

Pixel Eraser cuts line segments at local intersections and sweeps between event samples, including sparsely sampled ink and fast eraser motion. Rectangle/ellipse outlines become editable ink fragments after the first hit. Unaffected strokes retain their original representation; laser fragments retain their original expiry. Undo restores the original geometry and redo restores the cut fragments.

## Validation on a Samsung device

- Finger drawing on: finger ink and S Pen ink; side-button press/release while touching.
- Finger drawing off: S Pen still draws and erases; fingers do not draw.
- Hand button: verify full passthrough to the underlying app, then switch back and resume drawing.
- Test toolbar and both popups with finger and pen, including long-press dragging the lone eye and dragging while collapsed.
- Confirm eye/collapse do not clear or disable the canvas; check undo/redo after erasure, zoom and rotation.
- Switch full passthrough, rotate, quit and restart; verify the underlying app receives the pen after quitting.

Build: use JDK 21 (JetBrains, as pinned by upstream), Android SDK 37, and the included Gradle wrapper. Run `:app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`; run `:app:connectedDebugAndroidTest` on a test device/emulator for instrumentation coverage.

## Recorded validation (2026-10-30)

- 106 JVM unit tests passed, including sparse-line cuts, swept erasure, shape fragments, and exact undo/redo restoration.
- 43 instrumentation tests passed on the Android 17/API 37.2 emulator, including side-button transitions and dragging the hidden eye. Espresso was updated from the old transitive 3.5.0 to 3.7.0 to support the emulator.
- GitHub Actions build and instrumentation jobs passed on commit `e884051` (compile SDK 37.0, emulator API 35).
- Visual checks confirmed the requested toolbar order and merged pen controls/line preview on a tablet-size display.
- Historical experiment (removed from the deployment build): a virtual hardware pen created with Android `uinput` reached the accessibility service and drew ink while Finger drawing was off. Overlapping independent pen and finger streams produced a pen cancellation on Android 17. Requirement 2 was subsequently deferred.

## Physical input diagnostic (2026-10-30)

- Device: Samsung SM-X910, Android 16 / API 36 (36.1), `ro.build.version.oneui=80500`, security patch 2026-08-05. Production build, locked verified boot; no `su` found in the common checked locations.
- Separate evdev nodes: `sec_e-pen` and `sec_touchscreen`. ADB shell could read both and successfully acquire/release `EVIOCGRAB` on the pen. Only the pen was grabbed; the touchscreen remained available to Android.
- During a 60-second test the user was asked to move S Pen while sliding another finger, in both contact orders, and confirmed completing the test. A probe counted `SYN_REPORT` frames with `BTN_TOUCH` active without storing coordinates: **1,602 pen-contact frames, 329 finger-contact frames, zero overlapping contact frames on either stream**.
- This indicates suppression before the app-level input route in this test. It does not identify the exact firmware/driver component, establish behavior for every Samsung device, or prove that rooting would fix it. No applicable switch was identified in the inspected settings and exposed driver command lists.
- The probe released the pen and was removed. No system settings, original app installation, or app data were changed. This was a hardware input diagnostic, not an installation or end-to-end validation of this APK on Samsung hardware.
- The final application commit `0985eab` also passed [GitHub Actions build and API 35 instrumentation](https://github.com/kylindebug/DrawAnywhere/actions/runs/40994348377).

## SM-X910 deployment (2026-10-30)

After the user deferred simultaneous operation, the experimental service and routing UI were removed. The resulting APK was signed, installed on SM-X910, granted the normal overlay permission, and launched. The drawing foreground service and visible toolbar were confirmed. No original installation was present in user 0 at deployment time.

- Rebuilt successfully with 106 JVM tests passing and lint completing without errors.
- Seven selected instrumentation tests passed on the actual Android 16 tablet: four `StylusInputTest` cases and three `ToolbarGestureTest` cases. These exercise synthetic pen/button events, erasure undo/redo, finger filtering, canvas preservation, and toolbar drag/rendering; they do not constitute a manual physical S Pen acceptance test.
- Removed the test-only APK after testing. No accessibility service is required or enabled for this build.
- Earlier emulator/CI results above refer to the earlier experimental build, not this deployment revision.
