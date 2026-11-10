package com.example.data.repository

import com.example.data.model.AppCategory
import com.example.data.model.AppPolicy
import com.example.data.model.AuditLog
import com.example.data.model.Child
import com.example.data.model.HomeworkTask
import com.example.data.model.MirrorSession
import com.example.data.model.SubscriptionInfo
import com.example.data.model.UsageEvent
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.model.WebAttempt
import com.example.domain.DomainFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TawazunRepository {

    // Current Session (null = logged out)
    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    // Children in family
    private val _children = MutableStateFlow<List<Child>>(
        listOf(
            Child(
                id = "child_zain",
                name = "Zain Ali",
                grade = "Grade 9 (Matric)",
                avatarEmoji = "👦",
                deviceModel = "Samsung Galaxy A15",
                studentEmail = "student.zain@gmail.com",
                homeworkUnlocked = false,
                bedtimeStart = "22:00",
                bedtimeEnd = "06:30",
                pairingCode = "TWZ-9104"
            ),
            Child(
                id = "child_ayla",
                name = "Ayla Khan",
                grade = "Grade 7 (Pre-O-Levels)",
                avatarEmoji = "👧",
                deviceModel = "Redmi Note 12",
                studentEmail = "student.ayla@gmail.com",
                homeworkUnlocked = true,
                bedtimeStart = "21:30",
                bedtimeEnd = "07:00",
                pairingCode = "TWZ-3829"
            )
        )
    )
    val children: StateFlow<List<Child>> = _children.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String>("child_zain")
    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()

    // Policies
    private val _policies = MutableStateFlow<Map<String, List<AppPolicy>>>(
        mapOf(
            "child_zain" to listOf(
                AppPolicy("whatsapp", "WhatsApp (School & Family)", AppCategory.COMMUNICATION, "💬", dailyLimitMin = 60, usedTodayMin = 40, groupName = "Chat Apps"),
                AppPolicy("khan_academy", "Khan Academy Urdu", AppCategory.EDUCATION, "📚", dailyLimitMin = 180, usedTodayMin = 45),
                AppPolicy("youtube", "YouTube", AppCategory.ENTERTAINMENT, "▶️", dailyLimitMin = 45, usedTodayMin = 25, groupName = "Social & Gaming"),
                AppPolicy("free_fire", "Free Fire MAX", AppCategory.GAMING, "🎮", dailyLimitMin = 30, usedTodayMin = 10, groupName = "Social & Gaming"),
                AppPolicy("tiktok", "TikTok", AppCategory.ENTERTAINMENT, "🎵", dailyLimitMin = 0, usedTodayMin = 0, groupName = "Social & Gaming"),
                AppPolicy("duolingo", "Duolingo", AppCategory.EDUCATION, "🦉", dailyLimitMin = 60, usedTodayMin = 20)
            ),
            "child_ayla" to listOf(
                AppPolicy("whatsapp", "WhatsApp (School)", AppCategory.COMMUNICATION, "💬", dailyLimitMin = 60, usedTodayMin = 15, groupName = "Chat Apps"),
                AppPolicy("khan_academy", "Khan Academy", AppCategory.EDUCATION, "📚", dailyLimitMin = 120, usedTodayMin = 30),
                AppPolicy("youtube", "YouTube Kids", AppCategory.ENTERTAINMENT, "▶️", dailyLimitMin = 60, usedTodayMin = 20),
                AppPolicy("roblox", "Roblox", AppCategory.GAMING, "🕹️", dailyLimitMin = 30, usedTodayMin = 0)
            )
        )
    )
    val policies: StateFlow<Map<String, List<AppPolicy>>> = _policies.asStateFlow()

    // Blocked Domains
    private val _blockedDomains = MutableStateFlow<List<String>>(
        listOf(
            "*.youtube.com",
            "*.tiktok.com",
            "*.roblox.com",
            "omegle.com",
            "*.chatroulette.com"
        )
    )
    val blockedDomains: StateFlow<List<String>> = _blockedDomains.asStateFlow()

    // Homework tasks
    private val _homeworkTasks = MutableStateFlow<List<HomeworkTask>>(
        listOf(
            HomeworkTask(
                taskId = "task_math_01",
                childId = "child_zain",
                title = "Quadratic Equations Exercise 2.3",
                subject = "Mathematics",
                dueTime = "Today, 6:00 PM",
                isCompletedByStudent = true,
                completedAt = System.currentTimeMillis() - 7200000,
                isVerifiedByParent = true,
                verifiedAt = System.currentTimeMillis() - 3600000,
                notesSummary = "Worksheet sent on Class WhatsApp Group (Ex 2.3 Q1-Q10)"
            ),
            HomeworkTask(
                taskId = "task_phys_02",
                childId = "child_zain",
                title = "Motion Numericals & Speed-Time Graph",
                subject = "Physics",
                dueTime = "Today, 7:30 PM",
                isCompletedByStudent = true,
                completedAt = System.currentTimeMillis() - 1800000,
                isVerifiedByParent = false,
                notesSummary = "Numericals 3.1 to 3.5 from Matric Physics Textbook"
            ),
            HomeworkTask(
                taskId = "task_urdu_03",
                childId = "child_zain",
                title = "Allama Iqbal Poetry Memorization (Bang-e-Dra)",
                subject = "Urdu Literature",
                dueTime = "Today, 8:30 PM",
                isCompletedByStudent = false,
                isVerifiedByParent = false,
                notesSummary = "Prepare 4 stanzas for morning assembly recitation"
            )
        )
    )
    val homeworkTasks: StateFlow<List<HomeworkTask>> = _homeworkTasks.asStateFlow()

    // 3-Day Window Usage Events
    private val _usageEvents = MutableStateFlow<List<UsageEvent>>(
        listOf(
            UsageEvent("ue_1", "child_zain", "whatsapp", "WhatsApp (School & Family)", System.currentTimeMillis() - 25 * 60 * 1000, 25),
            UsageEvent("ue_2", "child_zain", "khan_academy", "Khan Academy Urdu", System.currentTimeMillis() - 70 * 60 * 1000, 45),
            UsageEvent("ue_3", "child_zain", "free_fire", "Free Fire MAX", System.currentTimeMillis() - 130 * 60 * 1000, 10),
            UsageEvent("ue_4", "child_zain", "youtube", "YouTube", System.currentTimeMillis() - 200 * 60 * 1000, 25)
        )
    )
    val usageEvents: StateFlow<List<UsageEvent>> = _usageEvents.asStateFlow()

    // Web Attempts
    private val _webAttempts = MutableStateFlow<List<WebAttempt>>(
        listOf(
            WebAttempt("wa_1", "child_zain", "https://m.youtube.com/shorts", "m.youtube.com", System.currentTimeMillis() - 40 * 60 * 1000, blocked = true, matchedRule = "*.youtube.com"),
            WebAttempt("wa_2", "child_zain", "https://ilmkidunya.com/past-papers", "ilmkidunya.com", System.currentTimeMillis() - 90 * 60 * 1000, blocked = false),
            WebAttempt("wa_3", "child_zain", "https://vt.tiktok.com/clip", "vt.tiktok.com", System.currentTimeMillis() - 150 * 60 * 1000, blocked = true, matchedRule = "*.tiktok.com"),
            WebAttempt("wa_4", "child_zain", "https://sindhstudy.pk/notes", "sindhstudy.pk", System.currentTimeMillis() - 210 * 60 * 1000, blocked = false)
        )
    )
    val webAttempts: StateFlow<List<WebAttempt>> = _webAttempts.asStateFlow()

    // Mirror Sessions
    private val _mirrorSessions = MutableStateFlow<List<MirrorSession>>(
        listOf(
            MirrorSession(
                id = "mirror_live_01",
                childId = "child_zain",
                startedAt = System.currentTimeMillis() - 12 * 60 * 1000,
                frameCount = 78,
                appsUsed = listOf("WhatsApp (Class Group)", "Chrome (Notes)"),
                blockedCount = 1,
                status = "ACTIVE"
            )
        )
    )
    val mirrorSessions: StateFlow<List<MirrorSession>> = _mirrorSessions.asStateFlow()

    // Audit Logs
    private val _auditLogs = MutableStateFlow<List<AuditLog>>(
        listOf(
            AuditLog(
                id = "log_init",
                timestamp = System.currentTimeMillis() - 3600000 * 4,
                action = "CONSENT_ACCEPTED",
                performedBy = "parent.ahmed@gmail.com",
                details = "Signed consent v1.2-PK for child Zain Ali. 3-day data retention policy confirmed."
            ),
            AuditLog(
                id = "log_policy",
                timestamp = System.currentTimeMillis() - 3600000 * 2,
                action = "POLICY_UPDATE",
                performedBy = "parent.ahmed@gmail.com",
                details = "Updated TikTok dailyLimitMin to 0 min (Strict Block)."
            ),
            AuditLog(
                id = "log_hw",
                timestamp = System.currentTimeMillis() - 3600000,
                action = "HOMEWORK_VERIFIED",
                performedBy = "parent.ahmed@gmail.com",
                details = "Verified Mathematics Ex 2.3 completed tasks."
            )
        )
    )
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    // Subscription
    private val _subscription = MutableStateFlow(SubscriptionInfo())
    val subscription: StateFlow<SubscriptionInfo> = _subscription.asStateFlow()

    // --- AUTHENTICATION METHODS ---

    /**
     * Parent logs in with email.
     * Guaranteed: Student session is cleared, parent sees monitoring dashboard.
     */
    fun loginAsParent(email: String) {
        _currentSession.value = UserSession(
            email = email.trim(),
            role = UserRole.PARENT,
            isEmailVerified = true,
            consentAccepted = true
        )
        addAudit("PARENT_LOGIN", email, "Parent logged in. Student data is now viewable by verified parent.")
    }

    /**
     * Student logs in with student email.
     * Enforces prompt constraint: "Student's data will only be visible when the student's account
     * is logged out and the parent's account is logged in, through the email addresses."
     */
    fun loginAsStudent(email: String) {
        val matchedChild = _children.value.find { it.studentEmail.equals(email.trim(), ignoreCase = true) }
            ?: _children.value.first()
        _currentSession.value = UserSession(
            email = email.trim(),
            role = UserRole.STUDENT,
            childId = matchedChild.id
        )
        _selectedChildId.value = matchedChild.id
        addAudit("STUDENT_LOGIN", email, "Student logged in on device (${matchedChild.name}). Parental monitoring view is locked.")
    }

    fun logout() {
        val prevSession = _currentSession.value
        _currentSession.value = null
        if (prevSession != null) {
            addAudit("LOGOUT", prevSession.email, "Session logged out (${prevSession.role}).")
        }
    }

    fun setSelectedChild(childId: String) {
        _selectedChildId.value = childId
    }

    // --- HOMEWORK & BALANCE MODE ---

    fun markHomeworkCompletedByStudent(taskId: String, completed: Boolean) {
        _homeworkTasks.value = _homeworkTasks.value.map { task ->
            if (task.taskId == taskId) {
                task.copy(
                    isCompletedByStudent = completed,
                    completedAt = if (completed) System.currentTimeMillis() else null,
                    // If student un-completes, also reset parent verification
                    isVerifiedByParent = if (!completed) false else task.isVerifiedByParent
                )
            } else task
        }
        val task = _homeworkTasks.value.find { it.taskId == taskId }
        if (task != null) {
            recomputeUnlock(task.childId)
            addAudit("HOMEWORK_STATUS_CHANGED", _currentSession.value?.email ?: "Student", "Marked '${task.title}' completed = $completed")
        }
    }

    fun verifyHomeworkByParent(taskId: String, verified: Boolean) {
        _homeworkTasks.value = _homeworkTasks.value.map { task ->
            if (task.taskId == taskId) {
                task.copy(
                    isVerifiedByParent = verified,
                    verifiedAt = if (verified) System.currentTimeMillis() else null
                )
            } else task
        }
        val task = _homeworkTasks.value.find { it.taskId == taskId }
        if (task != null) {
            recomputeUnlock(task.childId)
            addAudit("HOMEWORK_VERIFIED", _currentSession.value?.email ?: "Parent", "Parent verification for '${task.title}' set to $verified")
        }
    }

    private fun recomputeUnlock(childId: String) {
        val tasksForChild = _homeworkTasks.value.filter { it.childId == childId }
        val allDoneAndVerified = tasksForChild.isNotEmpty() && tasksForChild.all { it.isCompletedByStudent && it.isVerifiedByParent }

        _children.value = _children.value.map { child ->
            if (child.id == childId) {
                child.copy(homeworkUnlocked = allDoneAndVerified)
            } else child
        }

        addAudit(
            "RECOMPUTE_UNLOCK",
            "Server/CloudFunction",
            "Child $childId homeworkUnlocked evaluated to $allDoneAndVerified (${tasksForChild.count { it.isVerifiedByParent }}/${tasksForChild.size} verified)."
        )
    }

    // --- APP POLICIES & LIMITS ---

    fun updatePolicy(childId: String, appKey: String, limitMin: Int) {
        val currentChildPolicies = _policies.value[childId] ?: return
        val updated = currentChildPolicies.map {
            if (it.appKey == appKey) it.copy(dailyLimitMin = limitMin) else it
        }
        _policies.value = _policies.value.toMutableMap().apply {
            put(childId, updated)
        }
        addAudit("POLICY_UPDATE", _currentSession.value?.email ?: "Parent", "Set daily limit for $appKey to $limitMin min for child $childId")
    }

    fun recordAppUsage(childId: String, appKey: String, minutesUsed: Int) {
        val currentChildPolicies = _policies.value[childId] ?: return
        val targetPolicy = currentChildPolicies.find { it.appKey == appKey } ?: return

        // If app belongs to a group (e.g. Social & Gaming), update group usage
        val group = targetPolicy.groupName
        val updatedPolicies = currentChildPolicies.map { policy ->
            if (group != null && policy.groupName == group) {
                policy.copy(usedTodayMin = policy.usedTodayMin + minutesUsed)
            } else if (policy.appKey == appKey) {
                policy.copy(usedTodayMin = policy.usedTodayMin + minutesUsed)
            } else {
                policy
            }
        }

        _policies.value = _policies.value.toMutableMap().apply {
            put(childId, updatedPolicies)
        }

        // Add 3-day usage event
        val newEvent = UsageEvent(
            id = "ue_${System.currentTimeMillis()}",
            childId = childId,
            appKey = appKey,
            appName = targetPolicy.appName,
            startedAt = System.currentTimeMillis() - minutesUsed * 60 * 1000,
            durationMin = minutesUsed
        )
        _usageEvents.value = listOf(newEvent) + _usageEvents.value
    }

    // --- WEB FILTERING ---

    fun testDomainAttempt(childId: String, rawUrl: String): Pair<Boolean, String?> {
        val check = DomainFilter.isBlocked(rawUrl, _blockedDomains.value)
        val attempt = WebAttempt(
            id = "wa_${System.currentTimeMillis()}",
            childId = childId,
            url = rawUrl,
            domain = DomainFilter.normalizeHost(rawUrl),
            attemptedAt = System.currentTimeMillis(),
            blocked = check.first,
            matchedRule = check.second
        )
        _webAttempts.value = listOf(attempt) + _webAttempts.value
        if (check.first) {
            addAudit("WEB_BLOCK", "DomainFilter", "Blocked navigation to '$rawUrl' under rule '${check.second}' for child $childId")
        }
        return check
    }

    fun addBlockedDomain(pattern: String) {
        val trimmed = pattern.trim().lowercase(Locale.ROOT)
        if (trimmed.isNotEmpty() && !_blockedDomains.value.contains(trimmed)) {
            _blockedDomains.value = _blockedDomains.value + trimmed
            addAudit("DOMAIN_RULE_ADDED", _currentSession.value?.email ?: "Parent", "Added block rule '$trimmed'")
        }
    }

    fun removeBlockedDomain(pattern: String) {
        _blockedDomains.value = _blockedDomains.value.filter { it != pattern }
        addAudit("DOMAIN_RULE_REMOVED", _currentSession.value?.email ?: "Parent", "Removed block rule '$pattern'")
    }

    // --- SCREEN MIRRORING & PURGING ---

    fun toggleMirroring(childId: String, active: Boolean) {
        _children.value = _children.value.map {
            if (it.id == childId) it.copy(mirroringActive = active) else it
        }
        addAudit("MIRROR_TOGGLE", _currentSession.value?.email ?: "Parent", "Screen mirroring $childId set to $active")
    }

    /**
     * Parent-facing "Delete now" button that immediately purges a child's mirror data and events.
     * Enforces the 3-day privacy guarantee.
     */
    fun purgeThreeDayData(childId: String) {
        _usageEvents.value = _usageEvents.value.filter { it.childId != childId }
        _webAttempts.value = _webAttempts.value.filter { it.childId != childId }
        _mirrorSessions.value = _mirrorSessions.value.filter { it.childId != childId }
        addAudit(
            "PURGE_3DAY_DATA",
            _currentSession.value?.email ?: "Parent",
            "Emergency privacy purge: Cleared all mirror frames, usage events, and web attempts for child $childId immediately."
        )
    }

    // --- BEDTIME LOGIC ---

    fun isBedtimeActive(child: Child): Boolean {
        return try {
            val now = Calendar.getInstance()
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
            if (!child.bedtimeActiveDays.contains(dayOfWeek)) return false

            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val startParts = child.bedtimeStart.split(":")
            val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()

            val endParts = child.bedtimeEnd.split(":")
            val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()

            if (startMin > endMin) {
                // Overnight bedtime (e.g. 22:00 to 06:30)
                currentMinutes >= startMin || currentMinutes < endMin
            } else {
                currentMinutes in startMin..endMin
            }
        } catch (e: Exception) {
            false
        }
    }

    // --- AUDIT LOGGER ---

    private fun addAudit(action: String, performedBy: String, details: String) {
        val log = AuditLog(
            id = "audit_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            action = action,
            performedBy = performedBy,
            details = details
        )
        _auditLogs.value = listOf(log) + _auditLogs.value
    }
}
