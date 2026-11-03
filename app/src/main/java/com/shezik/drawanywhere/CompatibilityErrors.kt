package com.shezik.drawanywhere

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

internal enum class FailureStage { START_SERVICE, OPEN_SETTINGS, LOAD_SETTINGS, CREATE_OVERLAY, UPDATE_OVERLAY, SAVE_SETTINGS, AUTO_MODE }
internal enum class FailureKind { PERMISSION, SYSTEM_API, SETTINGS, WINDOW, STARTUP }

internal fun failureKind(stage: FailureStage, error: Throwable): FailureKind = when {
    error is SecurityException -> FailureKind.PERMISSION
    error is LinkageError || stage == FailureStage.OPEN_SETTINGS || stage == FailureStage.AUTO_MODE -> FailureKind.SYSTEM_API
    stage == FailureStage.LOAD_SETTINGS || stage == FailureStage.SAVE_SETTINGS -> FailureKind.SETTINGS
    stage == FailureStage.CREATE_OVERLAY || stage == FailureStage.UPDATE_OVERLAY -> FailureKind.WINDOW
    else -> FailureKind.STARTUP
}

internal sealed interface OverlayState {
    data object Idle : OverlayState
    data object Starting : OverlayState
    data object Running : OverlayState
    data class Failed(val details: String) : OverlayState
}
internal object OverlayStatus { val state = MutableStateFlow<OverlayState>(OverlayState.Idle) }

internal object CompatibilityErrors {
    private const val PREFS = "compatibility_error"
    private const val KEY = "pending"
    fun pending(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        context.getSystemService(NotificationManager::class.java)?.cancel(101)
    }

    fun report(context: Context, stage: FailureStage, error: Throwable): String {
        Log.e("DrawCompatibility", "Failure at $stage", error)
        val reason = when (failureKind(stage, error)) {
            FailureKind.PERMISSION -> R.string.error_permission
            FailureKind.SYSTEM_API -> R.string.error_system_api
            FailureKind.SETTINGS -> R.string.error_settings
            FailureKind.WINDOW -> R.string.error_window
            FailureKind.STARTUP -> R.string.error_startup
        }
        val details = "${context.getString(reason)}\n\n" +
            "Code: $stage/${failureKind(stage, error)}\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME}\n" +
            error.stackTraceToString().take(4000)
        // Storage may itself be the failing component. The foreground Activity
        // also receives the in-memory report, so reporting does not depend on disk.
        try { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, details).apply() }
        catch (e: RuntimeException) { Log.w("DrawCompatibility", "Cannot retain error", e) }
        OverlayStatus.state.value = OverlayState.Failed(details)
        return details
    }

    fun notifyStopped(context: Context) {
        Toast.makeText(context, R.string.error_stopped, Toast.LENGTH_LONG).show()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel("errors", context.getString(R.string.error_title), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 101, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(101, NotificationCompat.Builder(context, "errors")
                .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(context.getString(R.string.error_title))
                .setContentText(context.getString(R.string.error_stopped)).setContentIntent(open).setAutoCancel(true).build())
        } catch (e: RuntimeException) { Log.w("DrawCompatibility", "Cannot post error notification", e) }
    }
}
