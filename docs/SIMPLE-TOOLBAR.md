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
