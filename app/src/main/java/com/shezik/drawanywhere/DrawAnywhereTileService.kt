package com.shezik.drawanywhere

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.provider.Settings
import android.app.PendingIntent

class DrawAnywhereTileService: TileService(){

    override fun onClick() {
        super.onClick()
        val serviceIntent = Intent(this, MainService::class.java)

        if (MainService.isRunning) {
            stopService(serviceIntent)
            qsTile?.state = Tile.STATE_INACTIVE
        } else {
            // Start through the visible Activity so compatibility failures can
            // be reported in a dialog, including blocked foreground startup.
            try {
                val permissionIntent = Intent(this, MainActivity::class.java)
                permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(this, 0, permissionIntent, PendingIntent.FLAG_IMMUTABLE)
                    pendingIntent?.let { startActivityAndCollapse(it) }
                } else {
                    @SuppressLint("StartActivityAndCollapseDeprecated")
                    startActivityAndCollapse(permissionIntent)
                }

            } catch (e: RuntimeException) {
                CompatibilityErrors.report(this, FailureStage.START_SERVICE, e)
                CompatibilityErrors.notifyStopped(this)
            } catch (e: LinkageError) {
                CompatibilityErrors.report(this, FailureStage.START_SERVICE, e)
                CompatibilityErrors.notifyStopped(this)
            }
        }

        qsTile?.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.state = if (MainService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

}
