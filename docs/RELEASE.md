# Simple distribution build

Version: `2.3-simple.1` (code 6). Package: `com.shezik.drawanywhere.simple`.
Minimum Android: 8.0 / API 26; target API 36. The namespace remains the upstream
namespace. The separate release package avoids signing conflicts with upstream
and the existing development package; it has its own permissions and settings.

Release enables R8 optimization/minification and resource shrinking. Build with
`:app:assembleRelease :app:lintRelease :app:testDebugUnitTest`. Then run
`tools/sign-release.ps1 -OutputApk <absolute-output.apk>` on Windows with
`JAVA_HOME` and `ANDROID_HOME` set. This script expects SDK build-tools 37.0.0.
It checks alignment, signs and verifies the APK. The local build used the installed
37.2 SDK via `-I tools/local-mirrors.init.gradle`; that optional init script also
uses public Aliyun Maven mirrors. Ordinary repository builds use compileSdk 37.

The signing identity is in ignored `.signing/`. Keep the PKCS12 key and its password
for future updates. The password file is DPAPI encrypted for the current Windows
account; copying it to another account/computer is not a usable password backup.
Arrange a secure independent key/password backup before moving build machines.
Never include `.signing/` in the distribution or Git history. The public certificate
may be shared. Keep the R8 `mapping.txt` for interpreting reported release traces.

## Failure reporting

Startup stays in a visible Activity until overlay startup succeeds or fails.
Permission denial, missing system interfaces, preference loading errors and
window creation failures produce a dialog with a cause, device/version details,
copy, retry and close controls. The service rechecks overlay permission because
some systems silently hide disallowed windows rather than throwing from addView.
Foreground status is established before settings IO. API 34+ uses specialUse;
older supported systems use the compatible foreground-service call.

Runtime window/settings coroutine errors stop the overlay, retain details and
show a toast. If notification permission exists, a notification opens the report;
otherwise the next launch displays it. Partially initialized windows and jobs are
cleaned up, and failed services do not automatically restart in a loop. Error
handling does not run on each stroke sample. Settings are not reset on failure.

This covers catchable app startup/window/settings failures. It cannot prevent OS
process termination, native crashes, or Samsung system freezing. Android versions
below the minimum are rejected by the package installer before app code can run.

Physical held-palm limits are documented in `SIMPLE-TOOLBAR.md`: S Pen operation
was verified with the menu away from the edge; finger operation while retaining
a palm remains unreliable.

## Optional automatic mode

Settings contains an experimental automatic-mode checkbox, off by default.
It requires Android 14 / API 34 or newer and the app's accessibility service.
Older devices show an unsupported-version explanation and keep manual control.
No page content is read and no finger gesture is injected or replayed.

While drawing, a single finger moving 48 dp from its initial position switches
to touch control. Small back-and-forth movement does not accumulate; multi-touch,
cancellation and an active pen reset detection. The first swipe can require
lifting and swiping again. A stylus DOWN switches back and forwards that same
event to the canvas, preserving the first point. Finger drawing is suspended
while this option is enabled; its original preference is retained.

Android's accessibility motion-event subscription consumes stylus events. The
service routes them to our canvas or toolbar and suspends capture while settings
are open. Stopping the overlay or disabling auto mode releases capture. This is
sequential automatic switching, not simultaneous pen/finger interaction. Manual
mode remains available by turning the option off. Other accessibility services,
external displays and manufacturer input filtering may behave differently.

On 2026-11-03 the SM-X910 user confirmed the write → finger swipe → write cycle
was usable and the first returning stroke was complete ("完整可行"). API 37
emulator testing also exercises a raw uinput stylus through the system input
pipeline, rather than relying only on direct app event callbacks.

## Validation of 2.3-simple.1

- 121 JVM tests passed, including jitter, threshold, cancellation and origin reset.
- Release lint: 0 errors, 19 warnings. APK signature/alignment verified; no
  debuggable flag; arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries included.
- Signed optimized release: denied-permission dialog, startup, drawing, clear,
  mode toggle and installed APK hash checked on API 37 emulator.
- 29 debug instrumented tests passed: stroke performance, hover, stylus, cached
  geometry, precise pixel erasing, synthetic palm/window input, toolbar gestures,
  and real overlay-permission denial with copy/close error-dialog controls.
- Automatic mode: raw uinput stylus restores writing from passthrough; screenshot
  comparisons check first/end points and stylus toolbar clear; finger movement
  restores passthrough. The user separately confirmed physical S Pen behavior on
  SM-X910 (Android 16 / API 36). The final signed build is installed on that tablet.

These checks do not establish compatibility on every API 26+ device or solve
physical held-palm finger rejection. No undocumented input injection is shipped;
uinput is used only by the emulator validation harness.

Distribute the APK with LICENSE, checksums, these limitations and corresponding
source. The source is also at https://github.com/kylindebug/DrawAnywhere/tree/feature/simple-toolbar.
