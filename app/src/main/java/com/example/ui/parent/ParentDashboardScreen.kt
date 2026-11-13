package com.example.ui.parent

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppCategory
import com.example.data.model.AppPolicy
import com.example.data.model.Child
import com.example.data.model.HomeworkTask
import com.example.data.repository.TawazunRepository
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    repository: TawazunRepository,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session by repository.currentSession.collectAsState()
    val children by repository.children.collectAsState()
    val selectedChildId by repository.selectedChildId.collectAsState()
    val policiesMap by repository.policies.collectAsState()
    val homeworkTasks by repository.homeworkTasks.collectAsState()
    val blockedDomains by repository.blockedDomains.collectAsState()
    val usageEvents by repository.usageEvents.collectAsState()
    val webAttempts by repository.webAttempts.collectAsState()
    val allowedWebsites by repository.allowedWebsites.collectAsState()
    val mirrorSessions by repository.mirrorSessions.collectAsState()
    val screenSnaps by repository.screenSnaps.collectAsState()
    val auditLogs by repository.auditLogs.collectAsState()
    val subscription by repository.subscription.collectAsState()
    val childrenLoading by repository.childrenLoading.collectAsState()

    var showAddChildDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Family mein abhi koi bacha nahi — "pehla bacha add karein" ka screen
    if (children.isEmpty()) {
        if (childrenLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TawazunEmeraldPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Family data load ho rahi hai...", color = TawazunTextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👨‍👩‍👧", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Apni family shuru karein",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TawazunTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Pehla bacha add karein — uska naam, class aur email. Phir wo apne phone par isi email se account bana sakta hai.",
                            fontSize = 13.sp,
                            color = TawazunTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddChildDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("➕ Pehla Bacha Add Karein", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onLogout) {
                            Text("Log Out", fontSize = 13.sp, color = TawazunTextSecondary)
                        }
                    }
                }
            }
        }
        if (showAddChildDialog) {
            AddChildDialog(
                onDismiss = { showAddChildDialog = false },
                onConfirm = { name, grade, email, device, emoji ->
                    scope.launch { repository.addChild(name, grade, email, device, emoji) }
                    showAddChildDialog = false
                }
            )
        }
        return
    }

    val currentChild = children.find { it.id == selectedChildId } ?: children.first()
    val currentChildPolicies = policiesMap[selectedChildId] ?: emptyList()
    val currentChildHomework = homeworkTasks.filter { it.childId == selectedChildId }

    var currentTab by remember { mutableIntStateOf(0) }
    // 0 = Overview, 1 = Homework, 2 = Rules, 3 = Mirror, 4 = Children, 5 = Billing, 6 = Audit

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TawazunSurface,
                    titleContentColor = TawazunTextPrimary
                ),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tawazun Parent",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TawazunEmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TawazunEmeraldDark)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "3-Day Privacy Vault",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            }
                        }
                        Text(
                            text = session?.email ?: "parent.ahmed@gmail.com",
                            fontSize = 11.sp,
                            color = TawazunTextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddChildDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Child",
                            tint = TawazunEmeraldPrimary
                        )
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("parent_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out",
                            tint = TawazunTextSecondary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = TawazunSurface,
                contentColor = TawazunTextPrimary
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Overview") },
                    label = { Text("Overview", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        val pendingCount = currentChildHomework.count { !it.isVerifiedByParent }
                        if (pendingCount > 0) {
                            Badge(containerColor = TawazunWarningAmber) {
                                Text(pendingCount.toString())
                            }
                        } else {
                            Icon(Icons.Default.Assignment, contentDescription = "Homework")
                        }
                    },
                    label = { Text("Homework", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Rule, contentDescription = "Rules") },
                    label = { Text("Rules", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.CastConnected, contentDescription = "Mirror") },
                    label = { Text("Mirror", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 5,
                    onClick = { currentTab = 5 },
                    icon = { Icon(Icons.Default.Payment, contentDescription = "Billing") },
                    label = { Text("Billing", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 6,
                    onClick = { currentTab = 6 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Audit") },
                    label = { Text("Audit", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TawazunEmeraldPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Child Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TawazunSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monitoring:",
                    fontSize = 12.sp,
                    color = TawazunTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(10.dp))
                children.forEach { child ->
                    val isSelected = child.id == selectedChildId
                    FilterChip(
                        selected = isSelected,
                        onClick = { repository.setSelectedChild(child.id) },
                        label = { Text("${child.avatarEmoji} ${child.name}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TawazunEmeraldPrimary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // 3-Day Privacy Notice (Prominently mandated by prompt)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = TawazunSurfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = TawazunEmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Showing last 3 days — older data is deleted automatically for your child's privacy.",
                        fontSize = 11.sp,
                        color = TawazunTextPrimary,
                        modifier = Modifier.weight(1f),
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TawazunErrorRed),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Delete now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tab Content
            when (currentTab) {
                0 -> OverviewTab(
                    child = currentChild,
                    homework = currentChildHomework,
                    policies = currentChildPolicies,
                    webAttempts = webAttempts.filter { it.childId == selectedChildId },
                    onNavigateToHomework = { currentTab = 1 },
                    onNavigateToRules = { currentTab = 2 }
                )
                1 -> HomeworkTab(
                    child = currentChild,
                    tasks = currentChildHomework,
                    onVerify = { taskId, verified -> repository.verifyHomeworkByParent(taskId, verified) },
                    onAddTask = { title, subject, due ->
                        repository.addHomeworkTask(currentChild.id, title, subject, due)
                    }
                )
                2 -> RulesTab(
                    child = currentChild,
                    policies = currentChildPolicies,
                    blockedDomains = blockedDomains,
                    allowedWebsites = allowedWebsites,
                    onUpdateLimit = { appKey, limit -> repository.updatePolicy(currentChild.id, appKey, limit) },
                    onAddDomain = { repository.addBlockedDomain(it) },
                    onRemoveDomain = { repository.removeBlockedDomain(it) },
                    onAddAllowed = { repository.addAllowedWebsite(it) },
                    onRemoveAllowed = { repository.removeAllowedWebsite(it) }
                )
                3 -> MirrorTab(
                    child = currentChild,
                    sessions = mirrorSessions.filter { it.childId == selectedChildId },
                    onToggleMirror = { active -> repository.toggleMirroring(currentChild.id, active) },
                    onSetMode = { mode, gallery -> repository.setMirrorMode(currentChild.id, mode, gallery) },
                    liveSnap = screenSnaps[currentChild.id]
                )
                4 -> ChildrenTab(
                    children = children,
                    onSelectChild = { repository.setSelectedChild(it) }
                )
                5 -> BillingTab(
                    subscription = subscription
                )
                6 -> AuditTab(
                    auditLogs = auditLogs
                )
            }
        }
    }

    if (showAddChildDialog) {
        AddChildDialog(
            onDismiss = { showAddChildDialog = false },
            onConfirm = { name, grade, email, device, emoji ->
                scope.launch { repository.addChild(name, grade, email, device, emoji) }
                showAddChildDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Emergency Privacy Purge", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will immediately purge all screen-mirror frames, usage events, and web attempts for ${currentChild.name} from the secure vault. This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.purgeThreeDayData(currentChild.id)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TawazunErrorRed)
                ) {
                    Text("Purge Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- TAB SUB-COMPONENTS ---

@Composable
fun OverviewTab(
    child: Child,
    homework: List<HomeworkTask>,
    policies: List<AppPolicy>,
    webAttempts: List<com.example.data.model.WebAttempt>,
    onNavigateToHomework: () -> Unit,
    onNavigateToRules: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Balance Mode Banner
        item {
            val pendingVerification = homework.count { !it.isVerifiedByParent }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (child.homeworkUnlocked) TawazunEmeraldDark else Color(0xFF2C1616)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (child.homeworkUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (child.homeworkUnlocked) TawazunEmeraldPrimary else TawazunWarningAmber,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (child.homeworkUnlocked) "Balance Mode: Entertainment Unlocked" else "Balance Mode: Entertainment Locked",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TawazunTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (child.homeworkUnlocked) {
                                "All homework verified! Entertainment & gaming apps are active within daily limits."
                            } else {
                                "$pendingVerification homework task(s) require parent verification before entertainment opens."
                            },
                            fontSize = 12.sp,
                            color = TawazunTextSecondary
                        )
                    }
                    Button(
                        onClick = onNavigateToHomework,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (child.homeworkUnlocked) TawazunEmeraldPrimary else TawazunWarningAmber
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            if (child.homeworkUnlocked) "Review" else "Verify",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Quick Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Screen Time",
                    value = "${policies.sumOf { it.usedTodayMin }} min",
                    subtitle = "Today's usage",
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Web Filter",
                    value = "${webAttempts.count { it.blocked }} blocked",
                    subtitle = "Harmful domains",
                    icon = Icons.Default.Block,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Device Health",
                    value = "Online",
                    subtitle = child.deviceModel,
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // App Allowances Preview
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
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
                            text = "App Timers & Group Quotas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TawazunTextPrimary
                        )
                        TextButton(onClick = onNavigateToRules) {
                            Text("Manage", fontSize = 12.sp, color = TawazunEmeraldPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    policies.take(4).forEach { policy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(policy.iconEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    policy.appName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TawazunTextPrimary
                                )
                                Text(
                                    if (policy.groupName != null) "Group: ${policy.groupName}" else policy.category.name,
                                    fontSize = 11.sp,
                                    color = TawazunTextSecondary
                                )
                            }
                            Text(
                                text = "${policy.usedTodayMin} / ${policy.dailyLimitMin} min",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (policy.isBlockedByTimer) TawazunErrorRed else TawazunEmeraldPrimary
                            )
                        }
                    }
                }
            }
        }

        // Recent Blocked Web Attempts
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Recent Web Navigation Attempts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TawazunTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (webAttempts.isEmpty()) {
                        Text(
                            "No web attempts in the 3-day window.",
                            fontSize = 12.sp,
                            color = TawazunTextSecondary
                        )
                    } else {
                        webAttempts.take(3).forEach { attempt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (attempt.blocked) Icons.Default.Block else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (attempt.blocked) TawazunErrorRed else TawazunEmeraldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        attempt.domain,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TawazunTextPrimary
                                    )
                                    if (attempt.matchedRule != null) {
                                        Text(
                                            "Matched rule: ${attempt.matchedRule}",
                                            fontSize = 10.sp,
                                            color = TawazunTextSecondary
                                        )
                                    }
                                }
                                Text(
                                    if (attempt.blocked) "BLOCKED" else "ALLOWED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (attempt.blocked) TawazunErrorRed else TawazunEmeraldPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = TawazunSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = TawazunEmeraldPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TawazunTextPrimary)
            Text(title, fontSize = 11.sp, color = TawazunTextSecondary)
            Text(subtitle, fontSize = 9.sp, color = TawazunLockedGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun HomeworkTab(
    child: Child,
    tasks: List<HomeworkTask>,
    onVerify: (String, Boolean) -> Unit,
    onAddTask: (title: String, subject: String, dueTime: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddHomeworkDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, subject, due ->
                onAddTask(title, subject, due)
                showAddDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("➕ Naya Homework Add Karein", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Balance Gatekeeper: Homework Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TawazunGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "In Pakistani households, children use WhatsApp to receive teacher notes. Entertainment stays locked until you verify every assignment is completed.",
                        fontSize = 11.sp,
                        color = TawazunTextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        items(tasks) { task ->
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
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
                            text = task.subject,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TawazunEmeraldPrimary
                        )
                        Text(
                            text = task.dueTime,
                            fontSize = 11.sp,
                            color = TawazunTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = TawazunTextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Teacher note: ${task.notesSummary}",
                        fontSize = 12.sp,
                        color = TawazunTextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (task.isCompletedByStudent) TawazunEmeraldPrimary else TawazunWarningAmber)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (task.isCompletedByStudent) "Done by Student" else "Pending Student",
                                fontSize = 11.sp,
                                color = TawazunTextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (task.isVerifiedByParent) "Verified ✓" else "Parent Verify",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (task.isVerifiedByParent) TawazunEmeraldPrimary else TawazunWarningAmber
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = task.isVerifiedByParent,
                                onCheckedChange = { onVerify(task.taskId, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TawazunEmeraldPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RulesTab(
    child: Child,
    policies: List<AppPolicy>,
    blockedDomains: List<String>,
    allowedWebsites: List<String>,
    onUpdateLimit: (String, Int) -> Unit,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onAddAllowed: (String) -> Unit,
    onRemoveAllowed: (String) -> Unit
) {
    var newDomainText by remember { mutableStateOf("") }
    var newSiteText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "App Time Limits & Group Allocations",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TawazunTextPrimary
            )
            Text(
                text = "0 minutes = App is completely blocked on device. Grouped apps share daily allowance.",
                fontSize = 11.sp,
                color = TawazunTextSecondary
            )
        }

        items(policies) { policy ->
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(policy.iconEmoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    policy.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TawazunTextPrimary
                                )
                                Text(
                                    if (policy.groupName != null) "Group: ${policy.groupName}" else policy.category.name,
                                    fontSize = 11.sp,
                                    color = TawazunTextSecondary
                                )
                            }
                        }

                        Text(
                            text = if (policy.dailyLimitMin == 0) "BLOCKED" else "${policy.dailyLimitMin} min/day",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (policy.dailyLimitMin == 0) TawazunErrorRed else TawazunEmeraldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = policy.dailyLimitMin.toFloat(),
                        onValueChange = { onUpdateLimit(policy.appKey, it.toInt()) },
                        valueRange = 0f..240f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = TawazunEmeraldPrimary,
                            activeTrackColor = TawazunEmeraldPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0m (Blocked)", fontSize = 10.sp, color = TawazunErrorRed)
                        Text("1 Hour", fontSize = 10.sp, color = TawazunTextSecondary)
                        Text("2 Hours", fontSize = 10.sp, color = TawazunTextSecondary)
                        Text("4 Hours", fontSize = 10.sp, color = TawazunTextSecondary)
                    }
                }
            }
        }

        // 🌐 ALLOWED WEBSITES — bacha SIRF inhi par ja sakta hai (app ke andar)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Allowed Websites",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TawazunTextPrimary
                    )
                    Text(
                        "Bacha inhi websites ko Tawazun ke Safe Web tab mein khol sakta hai — baqi sab block ho jati hai.",
                        fontSize = 11.sp,
                        color = TawazunTextSecondary,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSiteText,
                            onValueChange = { newSiteText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("khanacademy.org", fontSize = 12.sp) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newSiteText.isNotBlank()) {
                                    onAddAllowed(newSiteText)
                                    newSiteText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Add", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (allowedWebsites.isEmpty()) {
                        Text(
                            "Abhi koi website allow nahi — upar likh kar Add karein (jaise khanacademy.org ya ilmkidunya.com).",
                            fontSize = 11.sp,
                            color = TawazunTextSecondary
                        )
                    }
                    allowedWebsites.forEach { site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    site
                                        .removePrefix("https://")
                                        .removePrefix("http://")
                                        .removePrefix("www.")
                                        .substringBefore('/'),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TawazunTextPrimary
                                )
                                Text(
                                    site,
                                    fontSize = 10.sp,
                                    color = TawazunTextSecondary,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onRemoveAllowed(site) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("🗑 Remove", fontSize = 10.sp, color = TawazunErrorRed)
                            }
                        }
                    }
                }
            }
        }

        // Domain Blocker Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Browser Domain Filter (Wildcards)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TawazunTextPrimary
            )
            Text(
                text = "Supports *.youtube.com (matches m.youtube.com, never notyoutube.com).",
                fontSize = 11.sp,
                color = TawazunTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newDomainText,
                    onValueChange = { newDomainText = it },
                    placeholder = { Text("e.g. *.tiktok.com") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newDomainText.isNotBlank()) {
                            onAddDomain(newDomainText)
                            newDomainText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary)
                ) {
                    Text("Add")
                }
            }
        }

        items(blockedDomains) { domain ->
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = TawazunErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(domain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TawazunTextPrimary)
                    }
                    IconButton(onClick = { onRemoveDomain(domain) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TawazunTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MirrorTab(
    child: Child,
    sessions: List<com.example.data.model.MirrorSession>,
    onToggleMirror: (Boolean) -> Unit,
    onSetMode: (String, Boolean) -> Unit,
    liveSnap: Pair<String, Long>? = null
) {
    val activeSession = sessions.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 🔴 ASLI LIVE SCREEN SNAP (bachay ke phone se, Firestore se) — sirf SNAP mode mein
        if (child.mirrorMode != "video") {
            item {
                Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
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
                            "🔴 Live Screen Snap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TawazunErrorRed
                        )
                        Text(
                            text = liveSnap?.second?.let {
                                "Last: " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                            } ?: "No snapshot yet",
                            fontSize = 11.sp,
                            color = TawazunTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (liveSnap != null) {
                        val bitmap = remember(liveSnap.first) {
                            try {
                                val bytes = Base64.decode(liveSnap.first, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Live screen snapshot",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            Text(
                                "Snapshot decode nahi hua — agli update par dobara try hoga.",
                                fontSize = 12.sp,
                                color = TawazunTextSecondary
                            )
                        }
                    } else {
                        Text(
                            "Bachay ke phone par Screen Snap start hona chahiye:\n1. Neeche Mirror toggle ON karein\n2. Bachay ki app mein 🪞 card aa jayega\n3. Bacha 'Start' kare (ek dafa permission)",
                            fontSize = 12.sp,
                            color = TawazunTextSecondary,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Har 30 second taaza hota hai • 3-Day Privacy Vault mein hi rehta hai",
                        fontSize = 10.sp,
                        color = TawazunTextSecondary
                    )
                    }
                }
            }
        } else {
            // 🎞️ VIDEO MODE — video bachay ke phone par save hoti hai
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "🎞️ Video Recording Mode ON hai",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TawazunEmeraldPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Bachay ki poori screen ki PROPER VIDEO record hoti hai — koi second nahi chhootega.\n" +
                                "Save jagah: " + (if (child.videoToGallery) "Gallery → Movies → Tawazun" else "App ka private folder") +
                                "\nVideo bachay ke phone par rehti hai — bachay ki app mein '📁 My Recordings' se dekhein.",
                            fontSize = 12.sp,
                            color = TawazunTextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Live Screen Mirroring",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TawazunTextPrimary
                            )
                            Text(
                                "Target: ${child.deviceModel}",
                                fontSize = 12.sp,
                                color = TawazunTextSecondary
                            )
                        }

                        Switch(
                            checked = child.mirroringActive,
                            onCheckedChange = onToggleMirror,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TawazunEmeraldPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 🎛️ RECORDING MODE — parent khud choose kare (consent + choice)
                    Text(
                        "Recording Mode — khud choose karein:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TawazunTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row {
                        OutlinedButton(
                            onClick = { onSetMode("snap", child.videoToGallery) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (child.mirrorMode != "video") TawazunEmeraldPrimary else Color.Transparent,
                                contentColor = if (child.mirrorMode != "video") Color.White else TawazunTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text("📸 Snap", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onSetMode("video", child.videoToGallery) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (child.mirrorMode == "video") TawazunEmeraldPrimary else Color.Transparent,
                                contentColor = if (child.mirrorMode == "video") Color.White else TawazunTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text("🎞️ Video", fontSize = 12.sp)
                        }
                    }
                    if (child.mirrorMode == "video") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Gallery mein save karein",
                                    fontSize = 12.sp,
                                    color = TawazunTextPrimary
                                )
                                Text(
                                    "Off = app ka private folder (zyada hidden)",
                                    fontSize = 10.sp,
                                    color = TawazunTextSecondary
                                )
                            }
                            Switch(
                                checked = child.videoToGallery,
                                onCheckedChange = { onSetMode("video", it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TawazunEmeraldPrimary
                                )
                            )
                        }
                        Text(
                            "Poori screen ki proper video record hogi — koi second nahi chhootega. Video bachay ke phone par save hoti hai (cloud par nahi — FREE plan).",
                            fontSize = 10.sp,
                            color = TawazunTextSecondary,
                            lineHeight = 14.sp
                        )
                    } else {
                        Text(
                            "Snap mode: har 30 second ek photo — parent ko LIVE milti hai.",
                            fontSize = 10.sp,
                            color = TawazunTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "🔒 Security: Parents view frames via signed Cloud Storage URLs expiring in ≤10 minutes. A persistent indicator is displayed on the child's screen whenever active.",
                        fontSize = 11.sp,
                        color = TawazunTextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Live Frame Simulation Preview
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (child.mirroringActive) TawazunErrorRed else TawazunLockedGrey)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (child.mirroringActive) "LIVE STREAM ACTIVE" else "STREAM PAUSED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                "Signed URL TTL: 9m 24s",
                                fontSize = 10.sp,
                                color = TawazunGold
                            )
                        }

                        // Simulated Screen Frame Content
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F201C))
                                .border(1.dp, TawazunEmeraldPrimary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "💬 Class 9 WhatsApp Group",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TawazunEmeraldPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Sir Tariq: 'Exercise 2.3 Math notes uploaded'",
                                    fontSize = 12.sp,
                                    color = TawazunTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Smart Chat: Read-Only Enabled",
                                    fontSize = 11.sp,
                                    color = TawazunGold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Frames Captured: 78", fontSize = 11.sp, color = TawazunTextSecondary)
                            Text("Retention: 3 Days Max", fontSize = 11.sp, color = TawazunTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChildrenTab(
    children: List<Child>,
    onSelectChild: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(children) { child ->
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(child.avatarEmoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(child.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TawazunTextPrimary)
                                Text(child.grade, fontSize = 12.sp, color = TawazunTextSecondary)
                            }
                        }

                        Button(
                            onClick = { onSelectChild(child.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Select", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Device: ${child.deviceModel}", fontSize = 12.sp, color = TawazunTextSecondary)
                    Text("Pairing Code: ${child.pairingCode}", fontSize = 12.sp, color = TawazunGold, fontWeight = FontWeight.SemiBold)
                    Text("Bedtime Window: ${child.bedtimeStart} - ${child.bedtimeEnd}", fontSize = 12.sp, color = TawazunTextSecondary)
                }
            }
        }
    }
}

@Composable
fun BillingTab(
    subscription: com.example.data.model.SubscriptionInfo
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TawazunSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        subscription.planName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TawazunTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TawazunEmeraldDark)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(subscription.status, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    subscription.price,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunGold
                )
                Text(
                    "7-Day Free Trial (5 days remaining) • 3-Day Grace Period",
                    fontSize = 12.sp,
                    color = TawazunTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "🔒 Security & Compliance Guarantee:\n• Digital subscriptions are managed strictly through Google Play Billing (15% standard digital fee).\n• Server-side purchase verification via Cloud Function webhook with idempotency key.\n• ZERO credit card data is ever collected or processed by Tawazun.",
                    fontSize = 11.sp,
                    color = TawazunTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun AuditTab(
    auditLogs: List<com.example.data.model.AuditLog>
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Immutable Security Audit Trail",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TawazunTextPrimary
            )
            Text(
                "Every policy modification, device revocation, mirror viewing, and emergency purge is recorded.",
                fontSize = 11.sp,
                color = TawazunTextSecondary
            )
        }

        items(auditLogs) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            log.action,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TawazunEmeraldPrimary
                        )
                        Text(
                            dateFormat.format(Date(log.timestamp)),
                            fontSize = 10.sp,
                            color = TawazunTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        log.details,
                        fontSize = 12.sp,
                        color = TawazunTextPrimary
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Actor: ${log.performedBy}",
                        fontSize = 10.sp,
                        color = TawazunLockedGrey
                    )
                }
            }
        }
    }
}

@Composable
fun AddChildDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, grade: String, studentEmail: String, deviceModel: String, avatarEmoji: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("👦") }
    val emojiChoices = listOf("👦", "👧", "🧒", "🧑")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naya Bacha Add Karein", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bachay ka naam") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Class (e.g. Grade 9)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Bachay ka email (login ke liye)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = device,
                    onValueChange = { device = it },
                    label = { Text("Phone model (optional)") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiChoices.forEach { choice ->
                        FilterChip(
                            selected = emoji == choice,
                            onClick = { emoji = choice },
                            label = { Text(choice, fontSize = 20.sp) }
                        )
                    }
                }
                Text(
                    "Bacha isi email se apne phone par account banayega.",
                    fontSize = 11.sp,
                    color = TawazunTextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim(),
                        grade.trim(),
                        email.trim(),
                        device.trim().ifBlank { "Not paired yet" },
                        emoji
                    )
                },
                enabled = name.isNotBlank() && email.contains("@"),
                colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary)
            ) {
                Text("Add Karein")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddHomeworkDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, subject: String, dueTime: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naya Homework", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Kya homework hai? (e.g. Math Exercise 2.3)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Mathematics)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it },
                    label = { Text("Kab tak? (e.g. Aaj shaam 6 baje)") },
                    singleLine = true
                )
                Text(
                    "Task cloud mein save hoga — bachay ke phone par foran dikhega.",
                    fontSize = 11.sp,
                    color = TawazunTextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title.trim(), subject.trim(), due.trim()) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary)
            ) {
                Text("Add Karein")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
