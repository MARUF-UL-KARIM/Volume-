package com.example

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager

class VolumePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("volume_bubble_prefs", Context.MODE_PRIVATE)

    var bubbleSizeDp: Int
        get() = prefs.getInt(KEY_BUBBLE_SIZE, 56)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_SIZE, value.coerceIn(40, 80)).apply()

    var bubbleOpacity: Float
        get() = prefs.getFloat(KEY_BUBBLE_OPACITY, 0.95f)
        set(value) = prefs.edit().putFloat(KEY_BUBBLE_OPACITY, value.coerceIn(0.3f, 1.0f)).apply()

    // 0 = System, 1 = Light, 2 = Dark
    var darkMode: Int
        get() = prefs.getInt(KEY_DARK_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_DARK_MODE, value).apply()

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var selectedStream: Int
        get() = prefs.getInt(KEY_STREAM, AudioManager.STREAM_MUSIC)
        set(value) = prefs.edit().putInt(KEY_STREAM, value).apply()

    var isBubbleVisible: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE_VISIBLE, value).apply()

    var autoStartOnOpen: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var isCallZeroVolume: Boolean
        get() = prefs.getBoolean(KEY_CALL_ZERO_VOL, false)
        set(value) = prefs.edit().putBoolean(KEY_CALL_ZERO_VOL, value).apply()

    companion object {
        private const val KEY_BUBBLE_SIZE = "bubble_size_dp"
        private const val KEY_BUBBLE_OPACITY = "bubble_opacity"
        private const val KEY_DARK_MODE = "dark_mode_mode"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_STREAM = "selected_stream"
        private const val KEY_BUBBLE_VISIBLE = "bubble_visible"
        private const val KEY_AUTO_START = "auto_start_on_open"
        private const val KEY_CALL_ZERO_VOL = "call_zero_vol"

        const val ACTION_PREFS_CHANGED = "com.example.volumebubble.PREFS_CHANGED"
    }
}
