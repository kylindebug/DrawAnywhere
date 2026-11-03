package com.shezik.drawanywhere

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real WindowManager denial after foreground-service creation, with native error UI. */
class CompatibilityFailureTest {
    @Test fun deniedOverlayStopsServiceAndShowsActionableError() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()) }
        val previous = shell("cmd appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        assertTrue("Harness must grant overlay before test", previous.contains("allow"))
        val scenario = ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java))
        try {
            shell("cmd appops set ${context.packageName} SYSTEM_ALERT_WINDOW ignore")
            instrumentation.runOnMainSync {
                CompatibilityErrors.dismiss(context)
                OverlayStatus.state.value = OverlayState.Idle
                context.startForegroundService(Intent(context, MainService::class.java))
            }
            val deadline = SystemClock.uptimeMillis() + 5000
            while (OverlayStatus.state.value !is OverlayState.Failed && SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(25)
            val failed = OverlayStatus.state.value as? OverlayState.Failed
            assertNotNull("Window denial must become a report, not a process crash", failed)
            assertTrue(failed!!.details.contains("CREATE_OVERLAY"))
            assertFalse(MainService.isRunning)
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onView(withText(R.string.error_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.error_copy)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.error_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.error_close)).inRoot(isDialog()).perform(click())
        } finally {
            shell("cmd appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            context.stopService(Intent(context, MainService::class.java))
            instrumentation.runOnMainSync { CompatibilityErrors.dismiss(context) }
            scenario.close()
        }
    }
}
