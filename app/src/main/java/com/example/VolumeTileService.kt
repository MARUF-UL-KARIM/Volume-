package com.example

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class VolumeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        // If overlay permission not granted, open app to request it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_FORCE_OPEN, true)
            }
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
            return
        }

        // Toggle / Spawn or remove bubble directly from Quick Settings (Notification Bar)
        val prefs = VolumePreferences(this)
        val isShowing = FloatingBubbleService.isServiceRunning && FloatingBubbleService.isBubbleShowing

        if (!isShowing) {
            prefs.isBubbleVisible = true
            val intent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_SHOW_BUBBLE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Volume Bubble spawned", Toast.LENGTH_SHORT).show()

            // Close notification shade so bubble is immediately visible on screen
            try {
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            } catch (_: Exception) {}
        } else {
            prefs.isBubbleVisible = false
            val intent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_TOGGLE_BUBBLE
            }
            startService(intent)
            Toast.makeText(this, "Volume Bubble removed", Toast.LENGTH_SHORT).show()
        }

        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isShowing = FloatingBubbleService.isServiceRunning && FloatingBubbleService.isBubbleShowing
        tile.state = if (isShowing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Volume Bubble"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isShowing) "Active" else "Inactive"
        }
        tile.updateTile()
    }
}
