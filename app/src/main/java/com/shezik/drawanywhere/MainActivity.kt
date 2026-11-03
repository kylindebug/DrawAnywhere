/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025-2026 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = MainScope()
    private var dialog: AlertDialog? = null
    private var waiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = getString(R.string.starting_overlay)
            gravity = Gravity.CENTER
        })
        scope.launch {
            OverlayStatus.state.collect { state ->
                if (waiting) when (state) {
                    OverlayState.Running -> { waiting = false; finish() }
                    is OverlayState.Failed -> { waiting = false; showError(state.details) }
                    else -> Unit
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (dialog?.isShowing == true || waiting) return
        CompatibilityErrors.pending(this)?.let { showError(it); return }
        startOrStop()
    }

    private fun startOrStop() {
        if (Settings.canDrawOverlays(this)) {
            if (MainService.isRunning) {
                stopService(Intent(this@MainActivity, MainService::class.java))
                finish()
                return
            }
            val intent = Intent(this@MainActivity, MainService::class.java)
            waiting = true
            OverlayStatus.state.value = OverlayState.Starting
            try { startForegroundService(intent) }
            catch (e: RuntimeException) { failed(FailureStage.START_SERVICE, e) }
            catch (e: LinkageError) { failed(FailureStage.START_SERVICE, e) }
        } else {
            showOverlayPermissionDialog()
        }
    }

    private fun failed(stage: FailureStage, error: Throwable) {
        waiting = false
        showError(CompatibilityErrors.report(this, stage, error))
    }

    private fun showError(details: String) {
        if (isFinishing || dialog?.isShowing == true) return
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.error_title).setMessage(details).setCancelable(false)
            .setPositiveButton(R.string.error_retry) { _, _ ->
                CompatibilityErrors.dismiss(this)
                dialog = null
                startOrStop()
            }
            .setNegativeButton(R.string.error_close) { _, _ ->
                CompatibilityErrors.dismiss(this)
                finish()
            }
            .setNeutralButton(R.string.error_copy, null).create().also { errorDialog ->
                errorDialog.show()
                errorDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DrawAnywhere error", details))
                    Toast.makeText(this, R.string.error_copied, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showOverlayPermissionDialog() {
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.overlay_permission_description))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.permission_accept)) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                dialog = null
                try { startActivity(intent) }
                catch (e: RuntimeException) { failed(FailureStage.OPEN_SETTINGS, e) }
                catch (e: LinkageError) { failed(FailureStage.OPEN_SETTINGS, e) }
            }
            .setNegativeButton(getString(R.string.permission_deny)) { _, _ ->
                finishAffinity()
            }
            .show()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        scope.cancel()
        super.onDestroy()
    }
}
