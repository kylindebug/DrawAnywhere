# Toolbar and S Pen fork

This branch changes the toolbar and pen input while retaining the upstream AGPL-3.0 license.

The current deployment includes toolbar changes and local side-button erasure. Requirement 2 (simultaneous pen drawing and finger passthrough) is deferred; ordinary drawing/passthrough modes are retained.

## Toolbar

- Order: eye, undo, redo, clear, pen, color, hand, settings, collapse.
- Eye hides the other controls, leaving a draggable eye. Long-press and drag to move it.
- Collapse leaves eye and expand; expand restores the complete toolbar.
- The collapsed controls can be dragged immediately from either button. Tapping
  either button expands toward the free side on the configured toolbar axis.
- The eye is the persistent screen anchor. Initial launch and subsequent expansion
  use the same direction rule; layout measurement never overwrites that anchor.
- Folding uses immediate layout changes rather than competing size/visibility
  animations, avoiding eye flicker and repeated window repositioning.
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

## Collapsed toolbar positioning regression (2026-10-30)

- Fixed repeated window displacement during dragging: Compose already accounts for
  the moving window in pointer deltas. Movement delivered with pointer-up is kept,
  and saving no longer replaces the requested position with a stale rendered frame.
- 112 JVM tests passed; debug app/test APK builds and lint passed (0 errors,
  34 warnings).
- On SM-X910, checked all nine screen regions with both horizontal and vertical
  toolbars in both portrait and landscape: 36 placements, three open/close cycles
  each, 108 cycles total. Eye coordinates remained within two pixels, expansion
  followed available space, and all toolbar bounds remained on screen.
- One complete run of seven toolbar gesture tests and four stylus input tests
  passed on the tablet, with the same application APK and a temporary test-only
  thread-dump watchdog. The watchdog was removed afterward. Repeated runs also
  intermittently stalled during test activity setup/transition, including with
  original rotation settings restored; this instrumentation reliability issue
  remains unresolved. Interrupted runs are not counted as successful runs.
- Sampled an expanded-state screen recording at 1 and 4 seconds: the eye stayed in
  position; the later frame showed the existing idle dimming behavior. This is
  sampled-frame inspection, not full-video manual review.
- Original app preferences and device rotation/power settings are restored after
  testing; no accessibility service is required. Concurrent finger passthrough
  remains deferred.

## First-frame expansion and system move animation (2026-10-30)

The earlier grid checks above waited for layout to settle. They did not catch the
user-reported transient where a right-side toolbar first appeared beyond the edge
and then slid back. Those results must not be read as a transition-frame check.

Toolbar geometry is now reported during measurement, before the resized surface
is presented, instead of from a global-position callback after layout. Right- and
bottom-opening toolbars use corresponding window gravity. On Android 14/API 34+
the toolbar also calls the public
[`setCanPlayMoveAnimation(false)`](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setCanPlayMoveAnimation(boolean))
API: disabling enter/exit animations alone does not disable system position
animations. Device-wide animation settings are not changed. This animation API
is version-guarded; transition behavior below API 34 has not been validated.

The application APK passed 113 JVM tests and lint (0 errors, 37 warnings). On
SM-X910, a four-cycle recording at the same right-edge position was decoded and
checked frame by frame using open/closed eye template matching. All 359 final
frames matched the same horizontal eye coordinate (841 in the half-resolution
recording); no frame was flagged for displacement or low-confidence matching.
The old-build comparison flagged 194 of 399 frames. Sample contact sheets were
also inspected visually. This is a targeted transition regression, separate from
the earlier steady-state grid and instrumentation results.

The matching four-cycle bottom-edge recording also passed: all 357 frames kept
the eye at vertical coordinate 1435 in the half-resolution recording. The final
APK additionally passed nine-region grid checks for both toolbar orientations on
the portrait tablet (18 placements, 54 open/close cycles). No new instrumentation
suite result is claimed for this revision; its validation uses the 113 unit tests,
physical grid checks, and the two complete transition recordings above.
