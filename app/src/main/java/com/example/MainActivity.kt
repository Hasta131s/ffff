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
                    color = Color(0xFF0F111E) // Premium dark background
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title Section with gradient
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KayFlood",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Gelişmiş Otomatik Yazıcı & Flood Asistanı",
                    fontSize = 12.sp,
                    color = Color.LightGray.copy(alpha = 0.8f)
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF2E93),
                                Color(0xFF00F5FF)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isPermissionGranted) Color(0xFF00FF66).copy(alpha = 0.3f) else Color(0xFFFF453A).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E2136)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sistem Durumu",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isPermissionGranted) Color(0xFF00FF66) else Color(0xFFFF453A))
                        )
                        Text(
                            text = if (isPermissionGranted) "HİZMET AKTİF" else "İZİN GEREKİYOR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPermissionGranted) Color(0xFF00FF66) else Color(0xFFFF453A)
                        )
                    }
                }

                Text(
                    text = if (isPermissionGranted) {
                        "KayFlood Erişilebilirlik Servisi arka planda başarıyla bağlanmıştır. Diğer uygulamalara veya oyunlara girdiğinizde sağ kenardaki 'KF' simgesine basıp menüyü açarak anında flood başlatabilirsiniz!"
                    } else {
                        "KayFlood'un çalışabilmesi için telefonunuzun Erişilebilirlik Ayarları altından izin vermeniz gerekmektedir."
                    },
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                if (!isPermissionGranted) {
                    Button(
                        onClick = onGrantPermissionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_permission_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF2E93)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Erişilebilirlik İzni Ver",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // Show extra settings permission option for older/newer Android overlay if needed
                    OutlinedButton(
                        onClick = onOpenOverlaySettingsClick,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF00F5FF)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Üstte Gösterme İznini Düzenle", fontSize = 12.sp)
                    }
                }
            }
        }

        // Add New Preset Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF131728)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Yeni Şablon / Metin Ekle",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = newPresetTitle,
                    onValueChange = { newPresetTitle = it },
                    label = { Text("Başlık (Örn: Hazır Cevap)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = Color(0xFF00F5FF),
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = newPresetText,
                    onValueChange = { newPresetText = it },
                    label = { Text("Spam Edilecek Metin") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF2E93),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = Color(0xFFFF2E93),
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

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
                            Toast.makeText(context, "Lütfen boş alan bırakmayın!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E2136),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFF2E93).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kayıtlı Şablonlara Ekle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Section Title: My Presets
        Text(
            text = "Kayıtlı Flood Metinleri (${presetsList.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
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
                    text = "Henüz şablon eklemediniz.\nYukarıdan yenisini ekleyebilirsiniz.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presetsList) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF121526)
                        ),
                        shape = RoundedCornerShape(10.dp)
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
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (preset.isSystemDefault) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    Color(0xFF00F5FF).copy(alpha = 0.15f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "VARSAYILAN",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00F5FF)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = preset.text,
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
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
                                        tint = Color(0xFFFF453A),
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
