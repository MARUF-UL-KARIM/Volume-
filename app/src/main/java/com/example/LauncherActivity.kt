package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (!canOverlay) {
            // Permission not granted yet: Open MainActivity to prompt user
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val isBubbleActive = FloatingBubbleService.isServiceRunning && FloatingBubbleService.isBubbleShowing

        if (!isBubbleActive) {
            // First click: Activate & display the volume bubble directly
            // NEVER open MainActivity, NEVER create any window, NEVER appear in Recents/Overview!
            val prefs = VolumePreferences(this)
            prefs.isBubbleVisible = true

            val svcIntent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_SHOW_BUBBLE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }

            Toast.makeText(this, "Volume Bubble activated", Toast.LENGTH_SHORT).show()
            finish()
            overridePendingTransition(0, 0)
        } else {
            // Second click: Bubble is already active and visible on screen
            // User tapped the launcher icon again to open full app settings
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_FORCE_OPEN, true)
            }
            startActivity(mainIntent)
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
