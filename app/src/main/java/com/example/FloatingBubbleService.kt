package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private var isViewAdded = false
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: VolumePreferences

    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    // Internal reactive state holder
    private var streamTypeState = mutableIntStateOf(AudioManager.STREAM_MUSIC)
    private var currentVolState = mutableIntStateOf(0)
    private var maxVolState = mutableIntStateOf(15)
    private var isMutedState = mutableStateOf(false)
    private var isExpandedState = mutableStateOf(false)
    private var isBubbleVisibleState = mutableStateOf(true)
    private var bubbleOpacityState = mutableFloatStateOf(0.95f)
    private var bubbleSizeDpState = mutableIntStateOf(56)
    private var darkModeState = mutableIntStateOf(0)
    private var hapticEnabledState = mutableStateOf(true)

    // Dual (+ / -) Mode State
    private var isDualModeState = mutableStateOf(false)
    private var dualSizeDpState = mutableIntStateOf(48)
    private var dualOpacityState = mutableFloatStateOf(0.9f)
    private var dualGapDpState = mutableIntStateOf(8)
    private var isDualVerticalState = mutableStateOf(true)
    private var dualStreamTypeState = mutableIntStateOf(AudioManager.STREAM_MUSIC)
    private var showSystemSliderState = mutableStateOf(true)

    private var bubbleX = 32
    private var bubbleY = 260

    private fun updateBubblePosition(deltaX: Int, deltaY: Int) {
        if (isExpandedState.value || !isViewAdded) return

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Accurately determine bubble bounding box in pixels
        val density = displayMetrics.density
        val estimatedW: Int
        val estimatedH: Int
        if (isDualModeState.value) {
            val sizePx = (dualSizeDpState.intValue * density).roundToInt()
            val gapPx = (dualGapDpState.intValue * density).roundToInt()
            if (isDualVerticalState.value) {
                estimatedW = sizePx
                estimatedH = (sizePx * 2) + gapPx
            } else {
                estimatedW = (sizePx * 2) + gapPx
                estimatedH = sizePx
            }
        } else {
            val sizePx = (bubbleSizeDpState.intValue * density).roundToInt()
            estimatedW = sizePx
            estimatedH = sizePx
        }

        val viewW = if (composeView.width > 0) composeView.width else estimatedW
        val viewH = if (composeView.height > 0) composeView.height else estimatedH

        val maxX = (screenWidth - viewW).coerceAtLeast(0)
        val maxY = (screenHeight - viewH).coerceAtLeast(0)

        bubbleX = (bubbleX + deltaX).coerceIn(0, maxX)
        bubbleY = (bubbleY + deltaY).coerceIn(0, maxY)

        params.x = bubbleX
        params.y = bubbleY
        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (_: Exception) {}
    }

    private var volumeObserver: ContentObserver? = null
    private var volumeReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        prefs = VolumePreferences(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        loadPreferences()
        isServiceRunning = true
        isBubbleShowing = isBubbleVisibleState.value
        updateVolumeState(streamTypeState.intValue)

        createNotificationChannel()
        startForegroundServiceNotification()

        setupWindowManager()
        registerVolumeObservers()
        updateTileState()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun loadPreferences() {
        bubbleOpacityState.floatValue = prefs.bubbleOpacity
        bubbleSizeDpState.intValue = prefs.bubbleSizeDp
        darkModeState.intValue = prefs.darkMode
        hapticEnabledState.value = prefs.isHapticEnabled
        isBubbleVisibleState.value = prefs.isBubbleVisible
        isBubbleShowing = prefs.isBubbleVisible
        streamTypeState.intValue = prefs.selectedStream

        isDualModeState.value = prefs.isDualButtonMode
        dualSizeDpState.intValue = prefs.dualButtonSizeDp
        dualOpacityState.floatValue = prefs.dualButtonOpacity
        dualGapDpState.intValue = prefs.dualButtonGapDp
        isDualVerticalState.value = prefs.isDualVertical
        dualStreamTypeState.intValue = prefs.dualStreamType
        showSystemSliderState.value = prefs.showSystemVolumeSlider
    }

    private fun adjustDualVolume(delta: Int) {
        val stream = dualStreamTypeState.intValue
        val flags = if (showSystemSliderState.value) {
            AudioManager.FLAG_SHOW_UI
        } else {
            0
        }
        if (stream == AudioManager.STREAM_VOICE_CALL) {
            val cur = if (prefs.isCallZeroVolume) 0 else audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
            val next = (cur + delta).coerceIn(0, max)
            if (next == 0) {
                prefs.isCallZeroVolume = true
                try { audioManager.setStreamVolume(stream, 0, flags) } catch (_: Exception) {}
                try { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags) } catch (_: Exception) {}
            } else {
                prefs.isCallZeroVolume = false
                try { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags) } catch (_: Exception) {}
                try { audioManager.setStreamVolume(stream, next, flags) } catch (_: Exception) {}
            }
        } else {
            val direction = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(stream, direction, flags)
        }
        updateVolumeState(stream)
    }

    private fun updateVolumeState(streamType: Int) {
        val max = audioManager.getStreamMaxVolume(streamType)
        val current = audioManager.getStreamVolume(streamType)
        maxVolState.intValue = if (max <= 0) 15 else max
        if (streamType == AudioManager.STREAM_VOICE_CALL && prefs.isCallZeroVolume) {
            currentVolState.intValue = 0
            isMutedState.value = true
        } else {
            currentVolState.intValue = current
            isMutedState.value = current == 0
        }
    }

    private fun setExpanded(expanded: Boolean) {
        if (isExpandedState.value == expanded) return
        isExpandedState.value = expanded
        if (!isViewAdded) return
        if (expanded) {
            // Expand WindowManager layout to MATCH_PARENT so taps in empty space/outside close it
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.x = 0
            params.y = 0
        } else {
            // Collapse WindowManager layout back to bubble dimensions
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = bubbleX
            params.y = bubbleY
        }
        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (_: Exception) {}
    }

    private fun setBubbleVisibility(visible: Boolean) {
        isBubbleVisibleState.value = visible
        prefs.isBubbleVisible = visible
        isBubbleShowing = visible
        if (!visible && isExpandedState.value) {
            isExpandedState.value = false
        }
        if (!isViewAdded) {
            tryAddOverlayView()
        }
        if (::composeView.isInitialized) {
            composeView.visibility = if (visible) View.VISIBLE else View.GONE
        }
        if (isViewAdded) {
            params.width = if (isExpandedState.value) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
            params.height = if (isExpandedState.value) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
            params.x = if (isExpandedState.value) 0 else bubbleX
            params.y = if (isExpandedState.value) 0 else bubbleY
            try {
                windowManager.updateViewLayout(composeView, params)
            } catch (_: Exception) {}
        }
        updateNotification()
        updateTileState()
    }

    private fun setupWindowManager() {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            if (isExpandedState.value) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            if (isExpandedState.value) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (isExpandedState.value) 0 else bubbleX
            y = if (isExpandedState.value) 0 else bubbleY
        }

        composeView = ComposeView(this).apply {
            visibility = if (isBubbleVisibleState.value) View.VISIBLE else View.GONE
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var touchDownLocalX = 0f
            var touchDownLocalY = 0f
            var isDragging = false
            var touchDownTime = 0L

            setOnTouchListener { view, event ->
                if (isExpandedState.value) {
                    return@setOnTouchListener false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchDownLocalX = event.x
                        touchDownLocalY = event.y
                        touchDownTime = System.currentTimeMillis()
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (!isDragging && (dx * dx + dy * dy > 64)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val screenHeight = displayMetrics.heightPixels
                            val viewW = if (view.width > 0) view.width else (56 * displayMetrics.density).roundToInt()
                            val viewH = if (view.height > 0) view.height else (56 * displayMetrics.density).roundToInt()
                            val maxX = (screenWidth - viewW).coerceAtLeast(0)
                            val maxY = (screenHeight - viewH).coerceAtLeast(0)

                            params.x = (initialX + dx).coerceIn(0, maxX)
                            params.y = (initialY + dy).coerceIn(0, maxY)
                            bubbleX = params.x
                            bubbleY = params.y
                            try {
                                windowManager.updateViewLayout(composeView, params)
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            isDragging = false
                        } else {
                            val elapsed = System.currentTimeMillis() - touchDownTime
                            if (elapsed < 600) {
                                if (isDualModeState.value) {
                                    val currentH = if (view.height > 0) view.height else (56 * resources.displayMetrics.density).roundToInt()
                                    val currentW = if (view.width > 0) view.width else (56 * resources.displayMetrics.density).roundToInt()
                                    if (isDualVerticalState.value) {
                                        val midY = currentH / 2f
                                        if (touchDownLocalY < midY) {
                                            adjustDualVolume(1)
                                        } else {
                                            adjustDualVolume(-1)
                                        }
                                    } else {
                                        val midX = currentW / 2f
                                        if (touchDownLocalX < midX) {
                                            adjustDualVolume(-1)
                                        } else {
                                            adjustDualVolume(1)
                                        }
                                    }
                                } else {
                                    triggerHaptic()
                                    setExpanded(true)
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }

            setContent {
                val isDark = when (darkModeState.intValue) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                }

                MyApplicationTheme(darkTheme = isDark) {
                    FloatingBubbleRoot(
                        isDualMode = isDualModeState.value,
                        dualSizeDp = dualSizeDpState.intValue,
                        dualOpacity = dualOpacityState.floatValue,
                        dualGapDp = dualGapDpState.intValue,
                        isDualVertical = isDualVerticalState.value,
                        isExpanded = isExpandedState.value,
                        isVisible = isBubbleVisibleState.value,
                        bubbleSizeDp = bubbleSizeDpState.intValue,
                        opacity = bubbleOpacityState.floatValue,
                        streamType = streamTypeState.intValue,
                        currentVolume = currentVolState.intValue,
                        maxVolume = maxVolState.intValue,
                        isMuted = isMutedState.value,
                        onCloseClick = {
                            triggerHaptic()
                            setExpanded(false)
                        },
                        onVolumeChange = { newVolume ->
                            setVolume(newVolume)
                        },
                        onVolumeStep = { delta ->
                            adjustVolumeStep(delta)
                        },
                        onToggleMute = {
                            toggleMute()
                        },
                        onStreamChange = { newStream ->
                            streamTypeState.intValue = newStream
                            prefs.selectedStream = newStream
                            updateVolumeState(newStream)
                            triggerHaptic()
                        },
                        onOpenSettings = {
                            openMainActivity()
                        }
                    )
                }
            }
        }

        tryAddOverlayView()
    }

    private fun tryAddOverlayView() {
        if (isViewAdded) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        try {
            if (::composeView.isInitialized && composeView.parent == null) {
                windowManager.addView(composeView, params)
                isViewAdded = true
            }
        } catch (_: Exception) {
            isViewAdded = false
        }
    }

    private fun registerVolumeObservers() {
        volumeObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                updateVolumeState(streamTypeState.intValue)
                updateNotification()
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver!!
        )

        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateVolumeState(streamTypeState.intValue)
                updateNotification()
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_VOLUME_UP -> {
                adjustVolumeStep(1)
            }
            ACTION_VOLUME_DOWN -> {
                adjustVolumeStep(-1)
            }
            ACTION_TOGGLE_MUTE -> {
                toggleMute()
            }
            ACTION_SHOW_BUBBLE -> {
                setBubbleVisibility(true)
                triggerHaptic()
            }
            ACTION_TOGGLE_BUBBLE -> {
                val newVis = !isBubbleVisibleState.value
                setBubbleVisibility(newVis)
                triggerHaptic()
            }
            ACTION_UPDATE_PREFS -> {
                loadPreferences()
                if (!isViewAdded && isBubbleVisibleState.value) {
                    tryAddOverlayView()
                }
                updateVolumeState(streamTypeState.intValue)
                updateNotification()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun adjustVolumeStep(delta: Int) {
        val stream = streamTypeState.intValue
        if (stream == AudioManager.STREAM_VOICE_CALL) {
            val cur = if (prefs.isCallZeroVolume) 0 else currentVolState.intValue
            val next = (cur + delta).coerceIn(0, maxVolState.intValue)
            setVolume(next)
            return
        }
        val direction = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        updateVolumeState(stream)
        triggerHaptic()
        updateNotification()
    }

    private fun setVolume(newVolume: Int) {
        val stream = streamTypeState.intValue
        val clamped = newVolume.coerceIn(0, maxVolState.intValue)
        if (stream == AudioManager.STREAM_VOICE_CALL) {
            if (clamped == 0) {
                prefs.isCallZeroVolume = true
                currentVolState.intValue = 0
                isMutedState.value = true
                try {
                    audioManager.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                } catch (_: Exception) {}
                try {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                } catch (_: Exception) {}
            } else {
                prefs.isCallZeroVolume = false
                try {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                } catch (_: Exception) {}
                try {
                    audioManager.setStreamVolume(stream, clamped, AudioManager.FLAG_SHOW_UI)
                } catch (_: Exception) {}
                updateVolumeState(stream)
            }
        } else {
            audioManager.setStreamVolume(stream, clamped, AudioManager.FLAG_SHOW_UI)
            updateVolumeState(stream)
        }
        triggerHaptic()
        updateNotification()
    }

    private fun toggleMute() {
        val stream = streamTypeState.intValue
        if (stream == AudioManager.STREAM_VOICE_CALL) {
            if (prefs.isCallZeroVolume || isMutedState.value || currentVolState.intValue == 0) {
                prefs.isCallZeroVolume = false
                val restored = 2.coerceAtMost(maxVolState.intValue)
                try {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                } catch (_: Exception) {}
                try {
                    audioManager.setStreamVolume(stream, restored, AudioManager.FLAG_SHOW_UI)
                } catch (_: Exception) {}
                updateVolumeState(stream)
            } else {
                setVolume(0)
            }
            triggerHaptic()
            updateNotification()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isCurrentlyMuted = audioManager.isStreamMute(stream) || audioManager.getStreamVolume(stream) == 0
            val direction = if (isCurrentlyMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
            audioManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        } else {
            val current = audioManager.getStreamVolume(stream)
            if (current > 0) {
                audioManager.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
            } else {
                val restored = (maxVolState.intValue * 0.4f).roundToInt().coerceAtLeast(1)
                audioManager.setStreamVolume(stream, restored, AudioManager.FLAG_SHOW_UI)
            }
        }
        updateVolumeState(stream)
        triggerHaptic()
        updateNotification()
    }

    private fun triggerHaptic() {
        if (!hapticEnabledState.value) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(24)
            }
        } catch (_: Exception) {}
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FORCE_OPEN, true)
            putExtra(MainActivity.EXTRA_OPEN_CONFIG, true)
        }
        startActivity(intent)
        setExpanded(false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Volume Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for floating volume bubble"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_FORCE_OPEN, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Volume Bubble is active")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateTileState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.service.quicksettings.TileService.requestListeningState(
                    this,
                    android.content.ComponentName(this, VolumeTileService::class.java)
                )
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        isServiceRunning = false
        isBubbleShowing = false
        updateTileState()

        try {
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
            volumeReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}

        try {
            if (::composeView.isInitialized && isViewAdded) {
                windowManager.removeView(composeView)
            }
        } catch (_: Exception) {}
        isViewAdded = false

        super.onDestroy()
    }

    companion object {
        var isServiceRunning: Boolean = false
            internal set
        var isBubbleShowing: Boolean = false
            internal set

        const val CHANNEL_ID = "volume_bubble_control_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_VOLUME_UP = "com.example.volumebubble.ACTION_VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.example.volumebubble.ACTION_VOLUME_DOWN"
        const val ACTION_TOGGLE_MUTE = "com.example.volumebubble.ACTION_TOGGLE_MUTE"
        const val ACTION_SHOW_BUBBLE = "com.example.volumebubble.ACTION_SHOW_BUBBLE"
        const val ACTION_TOGGLE_BUBBLE = "com.example.volumebubble.ACTION_TOGGLE_BUBBLE"
        const val ACTION_UPDATE_PREFS = "com.example.volumebubble.ACTION_UPDATE_PREFS"
        const val ACTION_STOP_SERVICE = "com.example.volumebubble.ACTION_STOP_SERVICE"
    }
}

@Composable
fun FloatingBubbleRoot(
    isDualMode: Boolean,
    dualSizeDp: Int,
    dualOpacity: Float,
    dualGapDp: Int,
    isDualVertical: Boolean,
    isExpanded: Boolean,
    isVisible: Boolean,
    bubbleSizeDp: Int,
    opacity: Float,
    streamType: Int,
    currentVolume: Int,
    maxVolume: Int,
    isMuted: Boolean,
    onCloseClick: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeStep: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onStreamChange: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    if (!isVisible) return

    if (isDualMode) {
        DraggableDualBubbles(
            sizeDp = dualSizeDp,
            gapDp = dualGapDp,
            isVertical = isDualVertical,
            opacity = dualOpacity
        )
    } else {
        if (!isExpanded) {
            Box(
                modifier = Modifier.alpha(opacity)
            ) {
                DraggableBubble(
                    sizeDp = bubbleSizeDp,
                    currentVolume = currentVolume,
                    maxVolume = maxVolume,
                    isMuted = isMuted
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onCloseClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .alpha(opacity)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // consume touch inside panel
                        }
                ) {
                    VolumeControlPanel(
                        streamType = streamType,
                        currentVolume = currentVolume,
                        maxVolume = maxVolume,
                        isMuted = isMuted,
                        onClose = onCloseClick,
                        onVolumeChange = onVolumeChange,
                        onVolumeStep = onVolumeStep,
                        onToggleMute = onToggleMute,
                        onStreamChange = onStreamChange,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
fun DraggableDualBubbles(
    sizeDp: Int,
    gapDp: Int,
    isVertical: Boolean,
    opacity: Float
) {
    Box(
        modifier = Modifier.alpha(opacity)
    ) {
        if (isVertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gapDp.dp)
            ) {
                DualActionBubble(
                    sizeDp = sizeDp,
                    icon = Icons.Default.Add,
                    contentDescription = "Volume Up"
                )
                DualActionBubble(
                    sizeDp = sizeDp,
                    icon = Icons.Default.Remove,
                    contentDescription = "Volume Down"
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gapDp.dp)
            ) {
                DualActionBubble(
                    sizeDp = sizeDp,
                    icon = Icons.Default.Remove,
                    contentDescription = "Volume Down"
                )
                DualActionBubble(
                    sizeDp = sizeDp,
                    icon = Icons.Default.Add,
                    contentDescription = "Volume Up"
                )
            }
        }
    }
}

@Composable
fun DualActionBubble(
    sizeDp: Int,
    icon: ImageVector,
    contentDescription: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        primaryColor,
                        tertiaryColor
                    )
                )
            )
            .border(
                1.5.dp,
                Color.White.copy(alpha = 0.5f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size((sizeDp * 0.52f).dp)
        )
    }
}

@Composable
fun DraggableBubble(
    sizeDp: Int,
    currentVolume: Int,
    maxVolume: Int,
    isMuted: Boolean
) {
    val volumePercent = if (maxVolume > 0) {
        ((currentVolume.toFloat() / maxVolume) * 100).roundToInt()
    } else 0

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        primaryColor,
                        tertiaryColor
                    )
                )
            )
            .border(
                1.5.dp,
                Color.White.copy(alpha = 0.4f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when {
                    isMuted || currentVolume == 0 -> Icons.Default.VolumeMute
                    currentVolume < maxVolume / 2 -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeUp
                },
                contentDescription = "Volume Bubble",
                tint = Color.White,
                modifier = Modifier.size((sizeDp * 0.46f).dp)
            )
            Text(
                text = "$volumePercent%",
                color = Color.White,
                fontSize = (sizeDp * 0.19f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun VolumeControlPanel(
    streamType: Int,
    currentVolume: Int,
    maxVolume: Int,
    isMuted: Boolean,
    onClose: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeStep: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onStreamChange: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val volumePercent = if (maxVolume > 0) {
        ((currentVolume.toFloat() / maxVolume) * 100).roundToInt()
    } else 0

    Card(
        modifier = Modifier
            .width(300.dp)
            .wrapContentHeight()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Drag Handle & Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isMuted || currentVolume == 0 -> Icons.Default.VolumeMute
                                streamType == AudioManager.STREAM_VOICE_CALL -> Icons.Default.Phone
                                currentVolume < maxVolume / 2 -> Icons.Default.VolumeDown
                                else -> Icons.Default.VolumeUp
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (streamType) {
                                AudioManager.STREAM_VOICE_CALL -> "Call Volume"
                                AudioManager.STREAM_RING -> "Ring Volume"
                                AudioManager.STREAM_ALARM -> "Alarm Volume"
                                else -> "Media Volume"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap space to close",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Stream Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StreamChip(
                    icon = Icons.Default.MusicNote,
                    label = "Media",
                    selected = streamType == AudioManager.STREAM_MUSIC,
                    modifier = Modifier.weight(1f),
                    onClick = { onStreamChange(AudioManager.STREAM_MUSIC) }
                )
                StreamChip(
                    icon = Icons.Default.Notifications,
                    label = "Ring",
                    selected = streamType == AudioManager.STREAM_RING,
                    modifier = Modifier.weight(1f),
                    onClick = { onStreamChange(AudioManager.STREAM_RING) }
                )
                StreamChip(
                    icon = Icons.Default.Alarm,
                    label = "Alarm",
                    selected = streamType == AudioManager.STREAM_ALARM,
                    modifier = Modifier.weight(1f),
                    onClick = { onStreamChange(AudioManager.STREAM_ALARM) }
                )
                StreamChip(
                    icon = Icons.Default.Phone,
                    label = "Call",
                    selected = streamType == AudioManager.STREAM_VOICE_CALL,
                    modifier = Modifier.weight(1f),
                    onClick = { onStreamChange(AudioManager.STREAM_VOICE_CALL) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Volume Percentage Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (streamType) {
                        AudioManager.STREAM_VOICE_CALL -> "Voice Call Level"
                        AudioManager.STREAM_RING -> "Ringtone Level"
                        AudioManager.STREAM_ALARM -> "Alarm Level"
                        else -> "Media Level"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMuted || currentVolume == 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (isMuted || currentVolume == 0) "0% (Zero)" else "$volumePercent%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMuted || currentVolume == 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Volume Slider
            Slider(
                value = currentVolume.toFloat(),
                onValueChange = { onVolumeChange(it.roundToInt()) },
                valueRange = 0f..maxVolume.toFloat(),
                steps = if (maxVolume > 1) maxVolume - 1 else 0,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Step-by-Step Plus and Minus Buttons + Mute Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus (-) Button
                FilledTonalIconButton(
                    onClick = { onVolumeStep(-1) },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Volume Down",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Mute / Unmute Button
                FilledIconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isMuted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Mute"
                    )
                }

                // Plus (+) Button
                FilledTonalIconButton(
                    onClick = { onVolumeStep(1) },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Volume Up",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun StreamChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier.height(32.dp)
    )
}


