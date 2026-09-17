package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FORCE_OPEN = "extra_force_open"
    }

    private lateinit var prefs: VolumePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = VolumePreferences(this)

        enableEdgeToEdge()

        // If overlay permission is already granted, launch or refresh the service immediately
        if (canDrawOverlays()) {
            startBubbleService()
        }

        setContent {
            var darkMode by remember { mutableIntStateOf(prefs.darkMode) }
            val isDarkTheme = when (darkMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    prefs = prefs,
                    onDarkModeChange = { newMode ->
                        darkMode = newMode
                        prefs.darkMode = newMode
                        notifyServicePrefsChanged()
                    },
                    onRequestOverlayPermission = {
                        requestOverlayPermission()
                    },
                    onStartService = {
                        startBubbleService()
                    },
                    onStopService = {
                        stopBubbleService()
                    },
                    onPrefsUpdated = {
                        notifyServicePrefsChanged()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If permission was just granted and service not yet active, start service
        if (canDrawOverlays() && prefs.autoStartOnOpen) {
            startBubbleService()
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startBubbleService() {
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBubbleService() {
        val intent = Intent(this, FloatingBubbleService::class.java).apply {
            action = FloatingBubbleService.ACTION_STOP_SERVICE
        }
        startService(intent)
    }

    private fun notifyServicePrefsChanged() {
        val intent = Intent(this, FloatingBubbleService::class.java).apply {
            action = FloatingBubbleService.ACTION_UPDATE_PREFS
        }
        startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: VolumePreferences,
    onDarkModeChange: (Int) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onPrefsUpdated: () -> Unit
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    // Refresh overlay permission status when resuming
    DisposableEffect(Unit) {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
        onDispose { }
    }

    var bubbleSize by remember { mutableIntStateOf(prefs.bubbleSizeDp) }
    var bubbleOpacity by remember { mutableFloatStateOf(prefs.bubbleOpacity) }
    var darkModeOption by remember { mutableIntStateOf(prefs.darkMode) }
    var isHapticEnabled by remember { mutableStateOf(prefs.isHapticEnabled) }
    var selectedStream by remember { mutableIntStateOf(prefs.selectedStream) }
    var isServiceRunning by remember { mutableStateOf(hasOverlayPermission) }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var inAppVolume by remember {
        mutableIntStateOf(
            if (selectedStream == AudioManager.STREAM_VOICE_CALL && prefs.isCallZeroVolume) 0
            else audioManager.getStreamVolume(selectedStream)
        )
    }
    val inAppMaxVolume = remember(selectedStream) { audioManager.getStreamMaxVolume(selectedStream) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Volume Bubble",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner (if not granted)
            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Overlay Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To display the draggable floating bubble over other apps, please grant 'Appear on top' permission.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestOverlayPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else {
                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isServiceRunning) "Floating Bubble Active" else "Bubble Paused",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Quick controls active in status bar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { isChecked ->
                                isServiceRunning = isChecked
                                if (isChecked) {
                                    onStartService()
                                } else {
                                    onStopService()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }

            // Quick Test Volume Slider (inside app)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Volume Test",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (inAppVolume == 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            val percent = if (inAppMaxVolume > 0) ((inAppVolume.toFloat() / inAppMaxVolume) * 100).roundToInt() else 0
                            Text(
                                text = if (inAppVolume == 0) "0% (Zero)" else "$percent%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (inAppVolume == 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stream Selector Chips (Media, Ring, Alarm, Call)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedStream == AudioManager.STREAM_MUSIC,
                            onClick = {
                                selectedStream = AudioManager.STREAM_MUSIC
                                prefs.selectedStream = AudioManager.STREAM_MUSIC
                                inAppVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                onPrefsUpdated()
                            },
                            label = { Text("Media", fontSize = 10.5.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(13.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedStream == AudioManager.STREAM_RING,
                            onClick = {
                                selectedStream = AudioManager.STREAM_RING
                                prefs.selectedStream = AudioManager.STREAM_RING
                                inAppVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                                onPrefsUpdated()
                            },
                            label = { Text("Ring", fontSize = 10.5.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(13.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedStream == AudioManager.STREAM_ALARM,
                            onClick = {
                                selectedStream = AudioManager.STREAM_ALARM
                                prefs.selectedStream = AudioManager.STREAM_ALARM
                                inAppVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                                onPrefsUpdated()
                            },
                            label = { Text("Alarm", fontSize = 10.5.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(13.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedStream == AudioManager.STREAM_VOICE_CALL,
                            onClick = {
                                selectedStream = AudioManager.STREAM_VOICE_CALL
                                prefs.selectedStream = AudioManager.STREAM_VOICE_CALL
                                inAppVolume = if (prefs.isCallZeroVolume) 0 else audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                                onPrefsUpdated()
                            },
                            label = { Text("Call", fontSize = 10.5.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = inAppVolume.toFloat(),
                        onValueChange = {
                            val newVol = it.roundToInt()
                            inAppVolume = newVol
                            if (selectedStream == AudioManager.STREAM_VOICE_CALL) {
                                if (newVol == 0) {
                                    prefs.isCallZeroVolume = true
                                    try { audioManager.setStreamVolume(selectedStream, 0, 0) } catch (_: Exception) {}
                                    try { audioManager.adjustStreamVolume(selectedStream, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
                                } else {
                                    prefs.isCallZeroVolume = false
                                    try { audioManager.adjustStreamVolume(selectedStream, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
                                    try { audioManager.setStreamVolume(selectedStream, newVol, 0) } catch (_: Exception) {}
                                }
                            } else {
                                audioManager.setStreamVolume(selectedStream, newVol, 0)
                            }
                            onPrefsUpdated()
                        },
                        valueRange = 0f..inAppMaxVolume.toFloat(),
                        steps = if (inAppMaxVolume > 1) inAppMaxVolume - 1 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedStream == AudioManager.STREAM_VOICE_CALL) {
                                    val nextVol = (inAppVolume - 1).coerceAtLeast(0)
                                    inAppVolume = nextVol
                                    if (nextVol == 0) {
                                        prefs.isCallZeroVolume = true
                                        try { audioManager.setStreamVolume(selectedStream, 0, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                                        try { audioManager.adjustStreamVolume(selectedStream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                                    } else {
                                        prefs.isCallZeroVolume = false
                                        try { audioManager.setStreamVolume(selectedStream, nextVol, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                                    }
                                } else {
                                    audioManager.adjustStreamVolume(selectedStream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                                    inAppVolume = audioManager.getStreamVolume(selectedStream)
                                }
                                onPrefsUpdated()
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }

                        Text(
                            text = if (selectedStream == AudioManager.STREAM_VOICE_CALL && inAppVolume == 0) "Call Volume: 0 (Muted)" else "Step-by-step buttons",
                            fontSize = 12.sp,
                            fontWeight = if (selectedStream == AudioManager.STREAM_VOICE_CALL && inAppVolume == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedStream == AudioManager.STREAM_VOICE_CALL && inAppVolume == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalIconButton(
                            onClick = {
                                if (selectedStream == AudioManager.STREAM_VOICE_CALL) {
                                    val nextVol = (inAppVolume + 1).coerceAtMost(inAppMaxVolume)
                                    inAppVolume = nextVol
                                    prefs.isCallZeroVolume = false
                                    try { audioManager.adjustStreamVolume(selectedStream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                                    try { audioManager.setStreamVolume(selectedStream, nextVol, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                                } else {
                                    audioManager.adjustStreamVolume(selectedStream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                                    inAppVolume = audioManager.getStreamVolume(selectedStream)
                                }
                                onPrefsUpdated()
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }
            }

            // Bubble Appearance & Customization Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bubble Customization",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoSizeSelectSmall, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Bubble Size", fontSize = 14.sp)
                        }
                        Text(text = "${bubbleSize}dp", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value = bubbleSize.toFloat(),
                        onValueChange = {
                            bubbleSize = it.roundToInt()
                            prefs.bubbleSizeDp = bubbleSize
                            onPrefsUpdated()
                        },
                        valueRange = 40f..80f,
                        steps = 7
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Opacity, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Bubble Opacity", fontSize = 14.sp)
                        }
                        Text(text = "${(bubbleOpacity * 100).roundToInt()}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value = bubbleOpacity,
                        onValueChange = {
                            bubbleOpacity = it
                            prefs.bubbleOpacity = bubbleOpacity
                            onPrefsUpdated()
                        },
                        valueRange = 0.3f..1.0f
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Bubble Preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(bubbleSize.dp)
                                .alpha(bubbleOpacity)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size((bubbleSize * 0.45f).dp)
                            )
                        }
                    }
                }
            }

            // Dark Mode & Haptic Settings Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme & Feedback",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Dark Mode Option
                    Text(text = "Appearance (Dark Mode)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = darkModeOption == 0,
                            onClick = {
                                darkModeOption = 0
                                onDarkModeChange(0)
                            },
                            label = { Text("System", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = darkModeOption == 1,
                            onClick = {
                                darkModeOption = 1
                                onDarkModeChange(1)
                            },
                            label = { Text("Light", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = darkModeOption == 2,
                            onClick = {
                                darkModeOption = 2
                                onDarkModeChange(2)
                            },
                            label = { Text("Dark", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Haptic Feedback Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = "Haptic Feedback", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    text = "Vibrate on volume changes and clicks",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isHapticEnabled,
                            onCheckedChange = {
                                isHapticEnabled = it
                                prefs.isHapticEnabled = it
                                onPrefsUpdated()
                            }
                        )
                    }
                }
            }

            // Notification / Status Bar Guide Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Status Bar Quick Access",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Use Vol +, Vol -, Mute, and Bubble toggle right from your notification panel anytime without opening the app.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
