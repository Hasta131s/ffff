package com.example

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.database.AppDatabase
import com.example.database.PresetMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

class KayFloodService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.ACTION_STOP_SERVICE"
        const val NOTIFICATION_ID = 9181
        const val CHANNEL_ID = "kayflood_service_channel"

        // Global state for activity to observe or trigger toggles
        val isServiceRunning = MutableStateFlow(false)
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    // Room Database
    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Active screen text target tracker
    private var activeEditTextNode: AccessibilityNodeInfo? = null
    private var activeAppName: String = "Tespit Edilmedi"

    // Settings State
    private var isFloodingActive by mutableStateOf(false)
    private var textToFlood by mutableStateOf("KayFlood!")
    private var floodDelayMs by mutableLongStateOf(300L)
    private var isExpanded by mutableStateOf(false)
    private var presetsList = mutableStateListOf<PresetMessage>()
    private var showPresetsDropdown by mutableStateOf(false)
    private var messageCount by mutableIntStateOf(0)
    private var autoStartOnFocus by mutableStateOf(true)

    // Window Layout Params
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var screenWidth = 0
    private var screenHeight = 0

    // Lifecycle variables for ComposeView tree initialization
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val viewModelStore = ViewModelStore()

    // Flood Coroutine Job
    private var floodJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        savedStateRegistryController.performRestore(null)
        database = AppDatabase.getDatabase(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Read screen size
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        // Load custom presets
        scope.launch {
            database.presetDao().getAllPresets().collectLatest { list ->
                presetsList.clear()
                presetsList.addAll(list)
                if (list.isNotEmpty() && textToFlood == "KayFlood!") {
                    textToFlood = list.first().text
                }
            }
        }

        isServiceRunning.value = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSystem()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingMenu()
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val source = event.source
                if (source != null && source.isEditable) {
                    activeEditTextNode = source
                    activeAppName = getAppNameFromPackage(event.packageName?.toString() ?: "")
                    
                    if (autoStartOnFocus && isFloodingActive && floodJob == null) {
                        triggerSpamLoop()
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Keep track of active application window
                val source = event.source
                if (source != null) {
                    activeAppName = getAppNameFromPackage(event.packageName?.toString() ?: "")
                }
            }
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        if (packageName.isEmpty()) return "Tespit Edilmedi"
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }

    // Helper functions for spammer engine
    private fun triggerSpamLoop() {
        floodJob?.cancel()
        floodJob = scope.launch {
            while (isFloodingActive) {
                val node = activeEditTextNode ?: findFocusedEditText(rootInActiveWindow)
                if (node != null) {
                    try {
                        val isNodeValid = node.refresh()
                        if (isNodeValid && node.isEditable) {
                            val bundle = Bundle()
                            bundle.putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                textToFlood
                            )
                            val worked = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                            if (worked) {
                                delay(90) // Mini delay for IME integration
                                val sendButton = findSendButton(rootInActiveWindow)
                                if (sendButton != null) {
                                    sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    messageCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Node could be stale, clear active target
                        activeEditTextNode = null
                    }
                }
                delay(floodDelayMs)
            }
            floodJob = null
        }
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isEditable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val focused = findFocusedEditText(child)
            if (focused != null) return focused
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isClickable) {
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""

            val matchesSend = text.contains("gönder") || text == "gonder" || text == "send" ||
                    text == "paylaş" || text == "paylas" || text == "tamam" || text == "ok" ||
                    text == "enter" || text == "post" || desc.contains("gönder") ||
                    desc.contains("gonder") || desc.contains("send") || desc.contains("submit") ||
                    desc.contains("post") || id.endsWith("send") || id.endsWith("send_btn") ||
                    id.endsWith("sendbutton") || id.contains("gonder") || id.contains("send_message")

            if (matchesSend) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButton(child)
            if (found != null) return found
        }
        return null
    }

    private fun startFlooding() {
        isFloodingActive = true
        triggerSpamLoop()
    }

    private fun stopFlooding() {
        isFloodingActive = false
        floodJob?.cancel()
        floodJob = null
    }

    private fun showFloatingMenu() {
        if (composeView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 160 // Position on the right side initially
            y = screenHeight / 3
        }

        val context = applicationContext
        composeView = ComposeView(context).apply {
            // Setup owners for Compose to work in custom window
            setViewTreeLifecycleOwner(this@KayFloodService)
            setViewTreeSavedStateRegistryOwner(this@KayFloodService)
            
            // Set simple custom ViewModelStoreOwner to support Compose inside WindowManager
            val vmStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore get() = this@KayFloodService.viewModelStore
            }
            setViewTreeViewModelStoreOwner(vmStoreOwner)

            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFFD0BCFF),
                        onPrimary = Color(0xFF381E72),
                        secondary = Color(0xFFEADDFF),
                        background = Color(0xFF1C1B1F),
                        surface = Color(0xFF2B2930),
                        onSurface = Color(0xFFE6E1E5)
                    )
                ) {
                    FloatingMenuUI(
                        onDrag = { dx, dy ->
                            overlayParams.x = (overlayParams.x + dx).coerceIn(0, screenWidth)
                            overlayParams.y = (overlayParams.y + dy).coerceIn(0, screenHeight)
                            try {
                                windowManager.updateViewLayout(composeView, overlayParams)
                            } catch (e: Exception) {
                                // View might be removed mid-transaction
                            }
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, overlayParams)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun FloatingMenuUI(onDrag: (Int, Int) -> Unit) {
        val widthTransition by animateDpAsState(targetValue = if (isExpanded) 280.dp else 48.dp, label = "width")
        val heightTransition by animateDpAsState(targetValue = if (isExpanded) 360.dp else 48.dp, label = "height")
        val elevationTransition by animateDpAsState(targetValue = if (isExpanded) 12.dp else 4.dp, label = "elevation")
        val roundedSize by animateDpAsState(targetValue = if (isExpanded) 24.dp else 24.dp, label = "rounded")

        Box(
            modifier = Modifier
                .size(widthTransition, heightTransition)
                .shadow(elevationTransition, RoundedCornerShape(roundedSize))
                .clip(RoundedCornerShape(roundedSize))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2B2930).copy(alpha = 0.98f),
                            Color(0xFF1C1B1F).copy(alpha = 0.98f)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                }
        ) {
            if (!isExpanded) {
                // Collapsed Tiny Button Mode (Icon of KayFlood)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFD0BCFF),
                                        Color(0xFF381E72)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "KF",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Active blinking green light if flooding is on
                    if (isFloodingActive) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FF00))
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            } else {
                // Expanded Glassmorphic Advanced Control Menu
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isFloodingActive) Color(0xFF00FF66) else Color.Gray)
                            )
                            Text(
                                text = "KayFlood",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Minimise Button
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Collapse",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Text Selector Dropdown Trigger
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { showPresetsDropdown = !showPresetsDropdown }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aktif Flood Metni",
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = textToFlood,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                            }
                            Icon(
                                imageVector = if (showPresetsDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Dropdown",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Dropdown Presets List
                    if (showPresetsDropdown) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1C1B1F))
                        ) {
                            if (presetsList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Kayıtlı metin yok", color = Color.Gray, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(presetsList) { preset ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (textToFlood == preset.text) MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.2f
                                                    ) else Color.Transparent
                                                )
                                                .clickable {
                                                    textToFlood = preset.text
                                                    showPresetsDropdown = false
                                                }
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Message,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = preset.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = preset.text,
                                                    fontSize = 10.sp,
                                                    color = Color.LightGray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Speed Interval / Delay Controls
                    Text(
                        text = "Hız Ayarı (Gecikme: ${floodDelayMs}ms)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val speeds = listOf(
                            Triple("Turbo", 150L, Color(0xFFD0BCFF)),
                            Triple("Hızlı", 300L, Color(0xFFEADDFF)),
                            Triple("Sakin", 800L, Color(0xFF49454F))
                        )
                        speeds.forEach { (label, ms, color) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (floodDelayMs == ms) color else Color.White.copy(alpha = 0.08f))
                                    .clickable { floodDelayMs = ms }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (floodDelayMs == ms) {
                                        if (ms == 800L) Color.White else Color(0xFF381E72)
                                    } else Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live statistics
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Hedef Uygulama", fontSize = 9.sp, color = Color.Gray)
                            Text(
                                text = activeAppName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Flood Sayısı", fontSize = 9.sp, color = Color.Gray)
                            Text(
                                text = "$messageCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEADDFF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Final Action row: PLAY/PAUSE/KAPAT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Play/Pause Floating service toggle button
                        Button(
                            onClick = {
                                if (isFloodingActive) {
                                    stopFlooding()
                                } else {
                                    startFlooding()
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFloodingActive) Color(0xFFF2B8B5) else Color(0xFFD0BCFF),
                                contentColor = if (isFloodingActive) Color(0xFF601410) else Color(0xFF381E72)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (isFloodingActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isFloodingActive) "Durdur" else "Başlat",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFloodingActive) "DURDUR" else "BAŞLAT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Complete shutdown button
                        Button(
                            onClick = { stopSystem() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(
                                text = "KAPAT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        val stopIntent = Intent(this, KayFloodService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, pendingIntentFlags)

        return NotificationCompatBuilder(this)
            .setContentTitle("KayFlood Aktif")
            .setContentText(if (isFloodingActive) "Otomatik flood aktif: $textToFlood" else "Sistem çalışıyor, menüden kontrol edebilirsiniz.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "KAPAT", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "KayFlood Servis Bildirimi",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    // Custom notification builder to avoid external dependencies issues
    private class NotificationCompatBuilder(private val context: Context) {
        private var title: String = ""
        private var text: String = ""
        private var smallIcon: Int = android.R.drawable.ic_dialog_info
        private var pendingIntent: PendingIntent? = null
        private var ongoing: Boolean = false
        private var actionText: String = ""
        private var actionIntent: PendingIntent? = null

        fun setContentTitle(title: String) = apply { this.title = title }
        fun setContentText(text: String) = apply { this.text = text }
        fun setSmallIcon(icon: Int) = apply { this.smallIcon = icon }
        fun setContentIntent(intent: PendingIntent) = apply { this.pendingIntent = intent }
        fun setOngoing(ongoing: Boolean) = apply { this.ongoing = ongoing }
        fun addAction(icon: Int, title: String, intent: PendingIntent) = apply {
            this.actionText = title
            this.actionIntent = intent
        }

        fun build(): Notification {
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(smallIcon)
                .setOngoing(ongoing)

            pendingIntent?.let { builder.setContentIntent(it) }

            if (actionText.isNotEmpty() && actionIntent != null) {
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    Notification.Action.Builder(0, actionText, actionIntent).build()
                } else {
                    null
                }
                if (action != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    builder.addAction(action)
                }
            }

            return builder.build()
        }
    }

    private fun stopSystem() {
        isFloodingActive = false
        floodJob?.cancel()
        floodJob = null
        isServiceRunning.value = false

        // Destroy Compose logic
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        viewModelStore.clear()

        // Remove overlay window
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed or not added
            }
            composeView = null
        }

        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSystem()
        scope.cancel()
    }
}
