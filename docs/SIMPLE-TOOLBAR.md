# Simplified quick-note branch

Branch: `feature/simple-toolbar`, based on `4b955b0`. The previous full-toolbar branch remains separate.

The floating toolbar always shows three buttons, in order: S Pen writing on/off,
clear, settings. Edit/EditOff icons replace the finger/hand icons. Turning writing
off passes both pen and touch to the underlying app while retaining visible ink;
turning it on resumes the existing drawing mode. Concurrent finger passthrough
and pen drawing remains deferred.

Settings contains undo/redo, pen shapes/width/opacity, colors, orientation, finger
drawing, existing application options, quit, and about. Eye and fold/expand controls
are not shown, including when an older saved preference says the toolbar is folded.

Drag any button immediately after crossing touch slop; a drag does not activate it.
The bar stays where released, except within 24dp of an edge, where it snaps to that
edge. Corners can snap on both axes. Coordinates are clamped after resizing or
rotation. Screen-coordinate drag tracking avoids a moving-window coordinate frame.
On API 29+, the toolbar requests a system gesture exclusion rectangle so dragging
away from a docked edge is not intercepted as Back. Drag-to-dismiss is not used;
quit remains in settings.

Validation (2026-11-03): 114 JVM tests passed; debug app/test builds and lint passed
(0 errors, 38 warnings). Three Compose tests on SM-X910 passed: fixed three-button
layout despite old collapsed preferences, pen-mode switching, direct drag without
activation, and settings access. Those tests ran before the final service-only
system-gesture exclusion addition. The final APK passed physical-window ADB drag
checks against all four edges, including dragging away from the right edge toward
the left. The earlier leftward checks failed until gesture exclusion was added.
Installed APK hash was verified, preferences restored byte-for-byte, test APK
removed, and the service restarted. These are synthetic input tests, not manual
S Pen acceptance or a landscape grid validation.

Precise eraser rendering and one-step undo/redo from the base branch are retained.

## Clear during writing (2026-11-03)

The first in-progress stroke previously left `canClearStrokes` false until pen-up,
so the clear button stayed disabled while ink was already present. The controller
now refreshes availability at stroke start and finish (including discarded tiny
shapes). Clear finishes the active tool before retaining its undo snapshot.
The view model advances an input generation on clear; the canvas discards the old
pointer session and pending finger samples before accepting further events. A new
pen-down starts normal writing again.

Validation: 116 JVM tests and 10 SM-X910 instrumentation tests (four toolbar,
six stylus/input) passed. The toolbar regression repeats pen-down, ink movement,
and an actual clear-button tap with slight movement ten times without pen-up.
Additional checks cover remaining stylus/button events, deferred finger input,
undo/redo restoration, and discarded tiny shapes. Build/lint passed (0 errors,
38 warnings). Original preferences were restored and the installed APK hash
verified. These are synthetic input checks; they do not change Samsung's hardware
palm-rejection behavior.

## Samsung background-freeze ANR (2026-11-03)

SM-X910 recorded a 10-second input-dispatch ANR at 11:03:56. The system log
shows Samsung Freecess freezing the foreground-service process at 11:02:31.929
(`Bg-buff`) and releasing it only at 11:03:56.471 (`Signal`, during ANR capture).
The trace contains `do_freezer_trap` for its threads. AppOps reported
`RUN_ANY_IN_BACKGROUND: ignore`; Samsung MARs reported `FAS Y`.

Device remediation was limited to DrawAnywhere: allow `RUN_ANY_IN_BACKGROUND`
and add the package to the device-idle exemption list. Readback confirmed
`allow`, the package exemption, and MARs `FAS N`. No APK change was needed.
On another Samsung device, check the app's battery/background restrictions
when an otherwise-visible toolbar stops responding. Samsung documents its
[background application management](https://developer.samsung.com/mobile/app-management.html).
An APK reinstall alone does not establish that device restrictions are cleared.

After the user approved clearing the temporary ink, the app was restarted to
remove the recurring old ANR dialog. The same APK then passed 90 seconds idle,
five ADB stylus-draw/clear-button cycles (screenshot comparisons), and toolbar
drag/restore. No newer ANR or ANR dialog was found. The toolbar was returned to
its original left edge. This verifies the device remedy with synthetic input;
it is not a new manual S Pen hardware acceptance test.
