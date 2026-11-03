package com.shezik.drawanywhere.view.toolbar

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shezik.drawanywhere.AutoPenService
import com.shezik.drawanywhere.CompatibilityErrors
import com.shezik.drawanywhere.FailureStage
import com.shezik.drawanywhere.R

@Composable
internal fun AutoModeControls(enabled: Boolean, onChange: (Boolean) -> Unit, prepareSettings: () -> Unit) {
    val context = LocalContext.current
    val connected by AutoPenService.connected.collectAsState()
    val close = LocalDismissToolbarPopup.current
    val supported = Build.VERSION.SDK_INT >= 34
    fun openSettings() {
        prepareSettings()
        close()
        try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: RuntimeException) {
            CompatibilityErrors.report(context, FailureStage.OPEN_SETTINGS, e)
            CompatibilityErrors.notifyStopped(context)
        }
    }
    CheckboxControl(stringResource(R.string.auto_mode), enabled, { checked ->
        if (checked && !supported) Toast.makeText(context, R.string.auto_mode_requires_android, Toast.LENGTH_LONG).show()
        else {
            onChange(checked)
            if (checked && !connected) openSettings()
        }
    })
    Text(stringResource(if (!supported) R.string.auto_mode_requires_android else R.string.auto_mode_description),
        style = MaterialTheme.typography.bodySmall)
    if (supported && enabled) {
        Text(stringResource(if (connected) R.string.auto_mode_ready else R.string.auto_mode_needs_service),
            style = MaterialTheme.typography.bodySmall)
        if (!connected) TextButton(onClick = ::openSettings) { Text(stringResource(R.string.auto_mode_open_settings)) }
    }
}
