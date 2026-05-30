package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.AppDatabase
import com.example.database.PresetMessage
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val isServicePermissionGranted = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val serviceRunningState by KayFloodService.isServiceRunning.collectAsStateWithLifecycle()
                val isPermissionGranted by isServicePermissionGranted.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1C1B1F) // High Density dark background
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        KayFloodDashboard(
                            modifier = Modifier.padding(innerPadding),
                            isServiceActive = serviceRunningState,
                            isPermissionGranted = isPermissionGranted,
                            onGrantPermissionClick = { openAccessibilitySettings() },
                            onOpenOverlaySettingsClick = { openOverlaySettings() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        isServicePermissionGranted.value = isAccessibilityServiceEnabled(this, KayFloodService::class.java)
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (enabledService in enabledServices) {
            val serviceInfo = enabledService.resolveInfo.serviceInfo
            if (serviceInfo.packageName == context.packageName && serviceInfo.name == service.name) {
                return true
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Listeden 'KayFlood' servisini bulup aktif edin", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erişilebilirlik ayarları açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Üzerine yazma izin ayarları açılamadı", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun KayFloodDashboard(
    modifier: Modifier = Modifier,
    isServiceActive: Boolean,
    isPermissionGranted: Boolean,
    onGrantPermissionClick: () -> Unit,
    onOpenOverlaySettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var presetsList by remember { mutableStateOf(emptyList<PresetMessage>()) }
    var newPresetTitle by remember { mutableStateOf("") }
    var newPresetText by remember { mutableStateOf("") }

    // Read presets in Realtime Flow
    LaunchedEffect(Unit) {
        database.presetDao().getAllPresets().collect { list ->
            presetsList = list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Header - High Density themed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // High Density Purple Icon box
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD0BCFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "KF",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF381E72),
                        fontSize = 18.sp
                    )
                }

                Column {
                    Text(
                        text = "KayFlood",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5)
                    )
                    Text(
                        text = "Sistem Aktif • v2.4.0",
                        fontSize = 11.sp,
                        color = Color(0xFFCAC4D0)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { /* Extra settings options optionally */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = Color(0xFFE6E1E5)
                )
            }
        }

        // Statistics Grid as seen in theme blueprint
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats 1
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ŞABLON COUNT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${presetsList.size} Adet",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6E1E5)
                    )
                }
            }

            // Stats 2
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "HIZLI SPAM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "150ms / ms",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6E1E5)
                    )
                }
            }
        }

        // Status Card - High Density Rounded Style
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isPermissionGranted) Color(0xFF00FF00).copy(alpha = 0.2f) else Color(0xFFF2B8B5).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2B2930)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sistem İzinleri",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isPermissionGranted) Color(0xFF00FF00) else Color(0xFFF2B8B5))
                        )
                        Text(
                            text = if (isPermissionGranted) "AKTİF" else "KAPALI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPermissionGranted) Color(0xFF00FF00) else Color(0xFFF2B8B5)
                        )
                    }
                }

                Text(
                    text = if (isPermissionGranted) {
                        "Her şey hazır! Oyun veya chat uygulamasına girdikten sonra sağ kısımdaki KayFlood mod menüsünü açıp istediğiniz şablonu seçerek floodu başlatabilirsiniz."
                    } else {
                        "KayFlood'un arka planda metin kutularını algılayıp yazabilmesi için Erişilebilirlik izni vermeniz gerekmektedir."
                    },
                    fontSize = 12.sp,
                    color = Color(0xFFCAC4D0)
                )

                if (!isPermissionGranted) {
                    Button(
                        onClick = onGrantPermissionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_permission_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEADDFF),
                            contentColor = Color(0xFF21005D)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ERİŞİLEBİLİRLİK İZNİ VER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenOverlaySettingsClick,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF49454F)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ÜSTTE GÖSTERME İZNİNİ DÜZENLE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Add New Preset Form Card - High Density Rounded Style
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2B2930)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Yeni Şablon Metni Ekle",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5)
                )

                OutlinedTextField(
                    value = newPresetTitle,
                    onValueChange = { newPresetTitle = it },
                    label = { Text("Mesaş Başlığı (örn: GG WP)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F),
                        focusedLabelColor = Color(0xFFD0BCFF),
                        unfocusedLabelColor = Color(0xFFCAC4D0),
                        focusedTextColor = Color(0xFFE6E1E5),
                        unfocusedTextColor = Color(0xFFE6E1E5)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = newPresetText,
                    onValueChange = { newPresetText = it },
                    label = { Text("Sürekli Gönderilecek Metin") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F),
                        focusedLabelColor = Color(0xFFD0BCFF),
                        unfocusedLabelColor = Color(0xFFCAC4D0),
                        focusedTextColor = Color(0xFFE6E1E5),
                        unfocusedTextColor = Color(0xFFE6E1E5)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = {
                        if (newPresetTitle.isNotBlank() && newPresetText.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                database.presetDao().insert(
                                    PresetMessage(
                                        title = newPresetTitle.trim(),
                                        text = newPresetText.trim(),
                                        isSystemDefault = false
                                    )
                                )
                                newPresetTitle = ""
                                newPresetText = ""
                            }
                        } else {
                            Toast.makeText(context, "Lütfen alanları dondurun!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "KAYDET VE LİSTEYE EKLE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Section Title
        Text(
            text = "Kayıtlı Flood Kalıpları",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6E1E5),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Presets list
        if (presetsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Kayıtlı şablon bulunamadı.",
                    color = Color(0xFFCAC4D0),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(presetsList) { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2B2930)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = preset.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE6E1E5),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (preset.isSystemDefault) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    Color(0xFFD0BCFF).copy(alpha = 0.15f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "SİSTEM",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD0BCFF)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = preset.text,
                                    fontSize = 11.sp,
                                    color = Color(0xFFCAC4D0),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!preset.isSystemDefault) {
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            database.presetDao().delete(preset)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Sil",
                                        tint = Color(0xFFF2B8B5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
