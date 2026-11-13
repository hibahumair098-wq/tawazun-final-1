package com.example.ui.student

import android.content.Intent
import android.provider.Settings
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppCategory
import com.example.data.model.AppPolicy
import com.example.data.model.Child
import com.example.data.model.HomeworkTask
import com.example.data.repository.TawazunRepository
import com.example.data.usage.UsageTracker
import com.example.service.GuardState
import com.example.service.RecItem
import com.example.service.ScreenRecordService
import com.example.service.ScreenSnapService
import com.example.ui.theme.TawazunEmeraldDark
import com.example.ui.theme.TawazunEmeraldPrimary
import com.example.ui.theme.TawazunErrorRed
import com.example.ui.theme.TawazunGold
import com.example.ui.theme.TawazunLockedGrey
import com.example.ui.theme.TawazunSurface
import com.example.ui.theme.TawazunSurfaceVariant
import com.example.ui.theme.TawazunTextPrimary
import com.example.ui.theme.TawazunTextSecondary
import com.example.ui.theme.TawazunWarningAmber
import com.example.ui.theme.TawazunWhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDeviceScreen(
    repository: TawazunRepository,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session by repository.currentSession.collectAsState()
    val children by repository.children.collectAsState()
    val selectedChildId by repository.selectedChildId.collectAsState()
    val policiesMap by repository.policies.collectAsState()
    val homeworkTasks by repository.homeworkTasks.collectAsState()

    val currentChild = children.find { it.id == selectedChildId } ?: children.first()
    val policies = policiesMap[currentChild.id] ?: emptyList()
    val childHomework = homeworkTasks.filter { it.childId == currentChild.id }

    var currentTab by remember { mutableIntStateOf(0) }
    // 0 = Home / Tasks, 1 = Apps, 2 = Smart Chat, 3 = Browser, 4 = Bedtime

    var appLaunchBlockedDialog by remember { mutableStateOf<String?>(null) }
    var browserUrl by remember { mutableStateOf("https://ilmkidunya.com/past-papers") }
    var browserResult by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    var chatMessageDraft by remember { mutableStateOf("") }

    // --- ASLI USAGE TRACKING (UsageStatsManager) ---
    val context = LocalContext.current
    var usageAccessGranted by remember { mutableStateOf(UsageTracker.hasUsageAccess(context)) }
    LaunchedEffect(Unit) {
        // App khulne par aaj ka asli usage parh kar cloud sync karo
        repository.refreshTodayUsage(context)
    }

    // --- 🛡️ APP GUARD (AccessibilityService — asli blocking) ---
    var guardEnabled by remember { mutableStateOf(GuardState.isGuardEnabled(context)) }

    // --- 🪞 SCREEN SNAP + 🎞️ VIDEO RECORD (MediaProjection — live mirror) ---
    var snapRunning by remember { mutableStateOf(ScreenSnapService.isRunning) }
    var videoRunning by remember { mutableStateOf(ScreenRecordService.isRunning) }
    var pendingVideo by remember { mutableStateOf(false) } // konsa mode start hoga
    var recTick by remember { mutableStateOf(0) } // recordings list refresh
    var playingRec by remember { mutableStateOf<RecItem?>(null) }
    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (pendingVideo) {
                val serviceIntent = Intent(context, ScreenRecordService::class.java).apply {
                    putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                    putExtra(ScreenRecordService.EXTRA_CHILD_ID, currentChild.id)
                    putExtra(ScreenRecordService.EXTRA_TO_GALLERY, currentChild.videoToGallery)
                }
                context.startForegroundService(serviceIntent)
                videoRunning = true
            } else {
                val serviceIntent = Intent(context, ScreenSnapService::class.java).apply {
                    putExtra(ScreenSnapService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenSnapService.EXTRA_RESULT_DATA, result.data)
                    putExtra(ScreenSnapService.EXTRA_FAMILY_ID, session?.familyId ?: "")
                    putExtra(ScreenSnapService.EXTRA_CHILD_ID, currentChild.id)
                }
                context.startForegroundService(serviceIntent)
                snapRunning = true
            }
        }
    }

    val isBedtime = repository.isBedtimeActive(currentChild)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TawazunSurface,
                        titleContentColor = TawazunTextPrimary
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentChild.avatarEmoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = currentChild.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TawazunTextPrimary
                                )
                                Text(
                                    text = session?.email ?: currentChild.studentEmail,
                                    fontSize = 11.sp,
                                    color = TawazunTextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(containerColor = TawazunSurfaceVariant),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("student_logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp), tint = TawazunTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Out", fontSize = 11.sp, color = TawazunTextPrimary)
                        }
                    }
                )

                // Mandatory Persistent Screen-Mirroring Active Banner
                if (currentChild.mirroringActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TawazunErrorRed)
                            .padding(vertical = 4.dp, horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.CastConnected,
                                contentDescription = "Mirroring Active",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Screen Mirroring Active — Parent can observe screen for safety",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = TawazunSurface,
                contentColor = TawazunTextPrimary
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Assignment, contentDescription = "Homework") },
                    label = { Text("Balance", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "Apps") },
                    label = { Text("Apps", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Smart Chat") },
                    label = { Text("Smart Chat", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunWhatsAppGreen,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Public, contentDescription = "Safe Web") },
                    label = { Text("Safe Web", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Bedtime, contentDescription = "Bedtime") },
                    label = { Text("Bedtime", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunGold,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 📊 Usage Access banner — asli tracking ke liye ek dafa permission
            if (!usageAccessGranted) {                Card(
                    colors = CardDefaults.cardColors(containerColor = TawazunWarningAmber),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "📊 Asli App Usage Tracking ON karein",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tawazun ko batane ke liye ke aap ne YouTube/WhatsApp kitna use kiya — ek dafa \"Usage Access\" permission dein. Parent ko asli timing dikhti hai.",
                            fontSize = 11.sp,
                            color = Color.Black,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Settings Kholo", fontSize = 12.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    usageAccessGranted = UsageTracker.hasUsageAccess(context)
                                    if (usageAccessGranted) {
                                        repository.refreshTodayUsage(context)
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("✓ Check Dobara", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            // 🛡️ App Guard banner — asli blocking ke liye ek dafa permission
            if (!guardEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TawazunEmeraldDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "🛡️ App Guard ON karein — Asli Blocking",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Time limit khatam hone par Tawazun apps ko khud band kar deta hai. Settings > Accessibility > \"Tawazun Guard\" on karein.",
                            fontSize = 11.sp,
                            color = Color(0xFFD9EFE4),
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Settings Kholo", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    guardEnabled = GuardState.isGuardEnabled(context)
                                },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("✓ Check Dobara", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 🪞🪞 Mirror card — mode ke hisaab se (parent ne khud choose kiya hai)
            if (currentChild.mirroringActive && currentChild.mirrorMode == "video") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TawazunSurfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (videoRunning) "🎞️ Video Recording chal rahi hai" else "🎞️ Parent ne Video Recording mode ON kiya hai",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TawazunTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (videoRunning)
                                "Poori screen ki video record ho rahi hai — save: " +
                                    (if (currentChild.videoToGallery) "Gallery → Movies → Tawazun" else "app ka private folder") +
                                    ". Notification band hone tak chalti rahegi."
                            else
                                "Allow karein taake screen ki poori video record ho (parent baad mein dekh sakta hai).",
                            fontSize = 11.sp,
                            color = TawazunTextSecondary,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            if (!videoRunning) {
                                Button(
                                    onClick = {
                                        pendingVideo = true
                                        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("🎞️ Start Recording", fontSize = 12.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        context.stopService(Intent(context, ScreenRecordService::class.java))
                                        videoRunning = false
                                        recTick++
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("Stop Karein", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 📁 My Recordings — ab tak ki saved videos
                Card(
                    colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📁 My Recordings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TawazunTextPrimary
                            )
                            OutlinedButton(
                                onClick = { recTick++ },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("↻ Refresh", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val recordings = remember(recTick) {
                            ScreenRecordService.listRecordings(context)
                        }
                        if (recordings.isEmpty()) {
                            Text(
                                "Abhi koi video nahi — Start Recording ke baad yahan dikhegi.",
                                fontSize = 11.sp,
                                color = TawazunTextSecondary
                            )
                        }
                        recordings.take(5).forEach { rec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        rec.label,
                                        fontSize = 11.sp,
                                        color = TawazunTextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${rec.location} • ${rec.sizeMb} • ${rec.dateLabel}",
                                        fontSize = 10.sp,
                                        color = TawazunTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { playingRec = rec },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("▶", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // ▶ Video player dialog
                if (playingRec != null) {
                    val rec = playingRec!!
                    Dialog(onDismissRequest = { playingRec = null }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF101828)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(rec.uri)
                                            val mc = MediaController(ctx)
                                            setMediaController(mc)
                                            mc.setAnchorView(this)
                                            start()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(230.dp)
                                )
                                TextButton(
                                    onClick = { playingRec = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("✕ Band karein", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else if (currentChild.mirroringActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TawazunSurfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (snapRunning) "🪞 Screen Snap LIVE chal raha hai" else "🪞 Parent ne Screen Mirror ON kiya hai",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TawazunTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (snapRunning)
                                "Har 30 second aap ki screen ka snapshot parent ko milta hai. Notification band hone tak chalta rahega."
                            else
                                "Allow karein taake parent aap ki screen dekh sake (study time monitoring).",
                            fontSize = 11.sp,
                            color = TawazunTextSecondary,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            if (!snapRunning) {
                                Button(
                                    onClick = {
                                        pendingVideo = false
                                        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("🪞 Start Karein", fontSize = 12.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        context.stopService(Intent(context, ScreenSnapService::class.java))
                                        snapRunning = false
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("Stop Karein", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            when (currentTab) {
                0 -> StudentBalanceTab(
                    child = currentChild,
                    homework = childHomework,
                    policies = policies,
                    onTaskToggled = { taskId, done -> repository.markHomeworkCompletedByStudent(taskId, done) }
                )
                1 -> StudentAppsTab(
                    child = currentChild,
                    policies = policies,
                    onUseApp = { appKey, mins -> repository.recordAppUsage(currentChild.id, appKey, mins) },
                    onBlockedClick = { appLaunchBlockedDialog = it }
                )
                2 -> StudentSmartChatTab(
                    child = currentChild,
                    whatsappPolicy = policies.find { it.appKey == "whatsapp" } ?: AppPolicy("whatsapp", "WhatsApp", AppCategory.COMMUNICATION, "💬", 60),
                    onSimulateExhaustion = {
                        val p = policies.find { it.appKey == "whatsapp" }
                        if (p != null) {
                            repository.recordAppUsage(currentChild.id, "whatsapp", p.remainingMin + 1)
                        }
                    }
                )
                3 -> StudentBrowserTab(
                    child = currentChild,
                    onTestUrl = { url ->
                        browserUrl = url
                        browserResult = repository.testDomainAttempt(currentChild.id, url)
                    },
                    currentUrl = browserUrl,
                    lastResult = browserResult
                )
                4 -> BedtimePreviewTab(
                    child = currentChild
                )
            }
        }
    }

    if (appLaunchBlockedDialog != null) {
        AlertDialog(
            onDismissRequest = { appLaunchBlockedDialog = null },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TawazunErrorRed, modifier = Modifier.size(36.dp)) },
            title = { Text("App Locked By Balance Mode", fontWeight = FontWeight.Bold) },
            text = { Text(appLaunchBlockedDialog ?: "") },
            confirmButton = {
                Button(
                    onClick = { appLaunchBlockedDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary)
                ) {
                    Text("Understood")
                }
            }
        )
    }
}

// --- STUDENT TAB COMPOSABLES ---

@Composable
fun StudentBalanceTab(
    child: Child,
    homework: List<HomeworkTask>,
    policies: List<AppPolicy>,
    onTaskToggled: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Balance Status Header
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (child.homeworkUnlocked) TawazunEmeraldDark else TawazunSurfaceVariant
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (child.homeworkUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (child.homeworkUnlocked) TawazunEmeraldPrimary else TawazunWarningAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (child.homeworkUnlocked) "Entertainment UNLOCKED" else "Entertainment LOCKED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TawazunTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (child.homeworkUnlocked) {
                            "All assignments are completed and verified by your parent! Games and social media are unlocked within your daily timer."
                        } else {
                            "Complete your homework tasks below and have your parent verify them to unlock YouTube and games."
                        },
                        fontSize = 12.sp,
                        color = TawazunTextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Today's Homework Checklist
        item {
            Text(
                text = "Today's Homework Tasks",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TawazunTextPrimary
            )
            Text(
                text = "Check off tasks as you finish. Entertainment unlocks once your parent verifies.",
                fontSize = 11.sp,
                color = TawazunTextSecondary
            )
        }

        items(homework) { task ->
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.isCompletedByStudent,
                        onCheckedChange = { onTaskToggled(task.taskId, it) },
                        colors = CheckboxDefaults.colors(checkedColor = TawazunEmeraldPrimary)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                task.subject,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TawazunEmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("• ${task.dueTime}", fontSize = 11.sp, color = TawazunTextSecondary)
                        }

                        Text(
                            task.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TawazunTextPrimary
                        )

                        Text(
                            task.notesSummary,
                            fontSize = 11.sp,
                            color = TawazunLockedGrey
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (task.isVerifiedByParent) TawazunEmeraldDark else TawazunSurfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (task.isVerifiedByParent) "Parent Verified ✓" else "Awaiting Parent Verification",
                                fontSize = 10.sp,
                                color = if (task.isVerifiedByParent) Color.White else TawazunWarningAmber
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentAppsTab(
    child: Child,
    policies: List<AppPolicy>,
    onUseApp: (String, Int) -> Unit,
    onBlockedClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Your Apps & Timers",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TawazunTextPrimary
            )
            Text(
                text = "Tap 'Use (10m)' to test real-time timer countdown and balance lock.",
                fontSize = 11.sp,
                color = TawazunTextSecondary
            )
        }

        items(policies) { policy ->
            val isEntertainmentOrGame = policy.category == AppCategory.ENTERTAINMENT || policy.category == AppCategory.GAMING
            val isLockedByHomework = isEntertainmentOrGame && !child.homeworkUnlocked
            val isLockedByZeroLimit = policy.isBlockedByTimer

            val isLocked = isLockedByHomework || isLockedByZeroLimit

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isLocked) Color(0xFF1B1F22) else TawazunSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isLockedByHomework) {
                            onBlockedClick("${policy.appName} is locked by Balance Mode because your homework is still pending parent verification.")
                        } else if (isLockedByZeroLimit) {
                            onBlockedClick("${policy.appName} daily time allowance (${policy.dailyLimitMin} min) is completely exhausted.")
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(policy.iconEmoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                policy.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TawazunTextPrimary
                            )
                            if (isLocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = TawazunErrorRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isLockedByHomework) {
                                "🔒 Locked until homework verified"
                            } else if (isLockedByZeroLimit) {
                                "⏱️ Daily quota exhausted (0 min left)"
                            } else {
                                "${policy.remainingMin} min remaining (Used: ${policy.usedTodayMin}m / ${policy.dailyLimitMin}m)"
                            },
                            fontSize = 11.sp,
                            color = if (isLocked) TawazunErrorRed else TawazunEmeraldPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!isLocked) {
                        Button(
                            onClick = { onUseApp(policy.appKey, 10) },
                            colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Use 10m", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentSmartChatTab(
    child: Child,
    whatsappPolicy: AppPolicy,
    onSimulateExhaustion: () -> Unit
) {
    val isReadOnly = whatsappPolicy.remainingMin <= 0
    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Smart Chat Status Header
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isReadOnly) Color(0xFF2C1E12) else TawazunEmeraldDark
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "WhatsApp: Smart School Chat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isReadOnly) TawazunWarningAmber else TawazunWhatsAppGreen)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isReadOnly) "READ-ONLY MODE" else "${whatsappPolicy.remainingMin}m REMAINING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isReadOnly) {
                        "⚠️ Daily messaging quota is reached. Incoming teacher notes & class homework remain readable so learning is never blocked."
                    } else {
                        "Chatting with friends is limited, but school worksheets and notes sent by teachers are always accessible."
                    },
                    fontSize = 11.sp,
                    color = TawazunTextSecondary,
                    lineHeight = 15.sp
                )

                if (!isReadOnly) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSimulateExhaustion,
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Simulate Exhausting Quota (Test Read-Only)", fontSize = 10.sp, color = TawazunGold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Simulated Class Chat Messages
        Text("Class 9 - Matric (St. Jude's High School)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TawazunTextPrimary)
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ChatMessageBubble(
                    sender = "Sir Tariq (Math)",
                    time = "3:15 PM",
                    message = "Salam class. Please complete Exercise 2.3 questions 1 to 10. Bring solved copies tomorrow.",
                    attachment = "📄 Math_Ex_2.3_Worksheet.pdf (1.2 MB)",
                    isTeacher = true
                )
            }
            item {
                ChatMessageBubble(
                    sender = "Miss Sadia (Physics)",
                    time = "4:30 PM",
                    message = "Numerical problems 3.1 to 3.5 are due tomorrow morning. Do not copy from keys.",
                    attachment = "📷 Blackboard_Formulas.jpg",
                    isTeacher = true
                )
            }
            item {
                ChatMessageBubble(
                    sender = "Hamza (Classmate)",
                    time = "5:10 PM",
                    message = "Did anyone solve question 7 of math? Is the answer x = 4?",
                    isTeacher = false
                )
            }
            item {
                ChatMessageBubble(
                    sender = "You",
                    time = "5:20 PM",
                    message = "Yes, x = 4 is correct. Factored by splitting the middle term.",
                    isTeacher = false,
                    isSelf = true
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message Input Row
        if (isReadOnly) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TawazunSurfaceVariant)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒 Messaging Locked: Read-Only Mode Active (Limit Reached)",
                    fontSize = 12.sp,
                    color = TawazunWarningAmber,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Message Class 9 Group...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { messageText = "" },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TawazunWhatsAppGreen)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black)
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    sender: String,
    time: String,
    message: String,
    attachment: String? = null,
    isTeacher: Boolean = false,
    isSelf: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isSelf) TawazunEmeraldDark else TawazunSurface
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        sender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isTeacher) TawazunGold else TawazunEmeraldPrimary
                    )
                    Text(time, fontSize = 9.sp, color = TawazunLockedGrey)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(message, fontSize = 12.sp, color = TawazunTextPrimary, lineHeight = 15.sp)

                if (attachment != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(TawazunSurfaceVariant)
                            .padding(6.dp)
                    ) {
                        Text(attachment, fontSize = 11.sp, color = TawazunGold, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun StudentBrowserTab(
    child: Child,
    onTestUrl: (String) -> Unit,
    currentUrl: String,
    lastResult: Pair<Boolean, String?>?
) {
    var inputUrl by remember { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Safe Browser Navigation", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TawazunTextPrimary)
        Text("Test URL wildcard blocking in real-time.", fontSize = 11.sp, color = TawazunTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Enter domain or URL...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onTestUrl(inputUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary)
            ) {
                Text("Go")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset Test URLs (Including prompt test cases: m.youtube.com vs notyoutube.com)
        Text("Quick Test URLs:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TawazunTextSecondary)
        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    inputUrl = "https://m.youtube.com"
                    onTestUrl("https://m.youtube.com")
                },
                modifier = Modifier.height(30.dp)
            ) {
                Text("m.youtube.com (Block)", fontSize = 10.sp, color = TawazunErrorRed)
            }

            OutlinedButton(
                onClick = {
                    inputUrl = "https://notyoutube.com"
                    onTestUrl("https://notyoutube.com")
                },
                modifier = Modifier.height(30.dp)
            ) {
                Text("notyoutube.com (Allow)", fontSize = 10.sp, color = TawazunEmeraldPrimary)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    inputUrl = "https://ilmkidunya.com/past-papers"
                    onTestUrl("https://ilmkidunya.com/past-papers")
                },
                modifier = Modifier.height(30.dp)
            ) {
                Text("ilmkidunya.com (Allow)", fontSize = 10.sp, color = TawazunEmeraldPrimary)
            }

            OutlinedButton(
                onClick = {
                    inputUrl = "https://vt.tiktok.com"
                    onTestUrl("https://vt.tiktok.com")
                },
                modifier = Modifier.height(30.dp)
            ) {
                Text("vt.tiktok.com (Block)", fontSize = 10.sp, color = TawazunErrorRed)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Browser Render Result Box
        if (lastResult != null) {
            val (blocked, rule) = lastResult
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (blocked) Color(0xFF2C1616) else Color(0xFF0F2620)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (blocked) Icons.Default.Block else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (blocked) TawazunErrorRed else TawazunEmeraldPrimary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (blocked) "Website Blocked by Tawazun" else "Content Allowed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TawazunTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (blocked) {
                            "Domain '$currentUrl' is restricted under parent policy rule: '$rule'."
                        } else {
                            "Navigating safely to '$currentUrl'. Educational resource verified."
                        },
                        fontSize = 12.sp,
                        color = TawazunTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TawazunSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tap a test URL above or enter a domain to test Tawazun wildcard domain blocking.",
                    fontSize = 12.sp,
                    color = TawazunTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

@Composable
fun BedtimePreviewTab(
    child: Child
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080D12))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Bedtime",
                tint = TawazunGold,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Bedtime Wind-Down",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TawazunTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Rest window active: ${child.bedtimeStart} to ${child.bedtimeEnd}",
                fontSize = 14.sp,
                color = TawazunGold,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Device is gently soft-locked to ensure quality rest for tomorrow's school day. Emergency family calls and saved teacher homework notes remain reachable.",
                fontSize = 12.sp,
                color = TawazunTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {},
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TawazunGold)
            ) {
                Text("Emergency Call to Parents")
            }
        }
    }
}
