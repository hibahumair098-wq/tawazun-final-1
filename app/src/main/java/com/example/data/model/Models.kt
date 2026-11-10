package com.example.data.model

enum class UserRole {
    PARENT,
    STUDENT
}

enum class AppCategory {
    EDUCATION,
    COMMUNICATION,
    ENTERTAINMENT,
    GAMING
}

data class UserSession(
    val email: String,
    val role: UserRole,
    val familyId: String = "fam_pk_001",
    val childId: String? = null,
    val isEmailVerified: Boolean = true,
    val consentAccepted: Boolean = true,
    val consentAt: String = "2026-11-01T10:00:00Z",
    val consentVersion: String = "v1.2-PK"
)

data class Child(
    val id: String,
    val familyId: String = "fam_pk_001",
    val name: String,
    val grade: String,
    val avatarEmoji: String,
    val deviceModel: String,
    val studentEmail: String,
    val homeworkUnlocked: Boolean = false,
    val bedtimeStart: String = "22:00",
    val bedtimeEnd: String = "06:30",
    val bedtimeActiveDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // Sun to Sat
    val mirroringActive: Boolean = false,
    val pairingCode: String = "TWZ-7492",
    val isRevoked: Boolean = false
)

data class AppPolicy(
    val appKey: String,
    val appName: String,
    val category: AppCategory,
    val iconEmoji: String,
    val dailyLimitMin: Int,
    val usedTodayMin: Int = 0,
    val groupName: String? = null // e.g. "Social & Gaming"
) {
    val remainingMin: Int
        get() = (dailyLimitMin - usedTodayMin).coerceAtLeast(0)

    val isBlockedByTimer: Boolean
        get() = dailyLimitMin > 0 && remainingMin <= 0
}

data class HomeworkTask(
    val taskId: String,
    val childId: String,
    val title: String,
    val subject: String,
    val dueTime: String,
    val isCompletedByStudent: Boolean = false,
    val completedAt: Long? = null,
    val isVerifiedByParent: Boolean = false,
    val verifiedAt: Long? = null,
    val expireAt: Long = System.currentTimeMillis() + 3L * 24 * 3600 * 1000,
    val notesSummary: String = "Received via School Class WhatsApp"
)

data class UsageEvent(
    val id: String,
    val childId: String,
    val appKey: String,
    val appName: String,
    val startedAt: Long,
    val durationMin: Int,
    val expireAt: Long = System.currentTimeMillis() + 3L * 24 * 3600 * 1000
)

data class WebAttempt(
    val id: String,
    val childId: String,
    val url: String,
    val domain: String,
    val attemptedAt: Long,
    val blocked: Boolean,
    val matchedRule: String? = null,
    val expireAt: Long = System.currentTimeMillis() + 3L * 24 * 3600 * 1000
)

data class MirrorSession(
    val id: String,
    val childId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val frameCount: Int = 42,
    val appsUsed: List<String> = listOf("WhatsApp", "Chrome", "YouTube"),
    val blockedCount: Int = 1,
    val downloadUrl: String = "https://storage.googleapis.com/tawazun-vault.appspot.com/mirror/fam_pk_001/session_live.jpg?sign=exp10m",
    val urlExpiresAt: Long = System.currentTimeMillis() + 10 * 60 * 1000, // 10 min signed URL
    val expireAt: Long = System.currentTimeMillis() + 3L * 24 * 3600 * 1000,
    val status: String = "ACTIVE"
)

data class AuditLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val performedBy: String,
    val details: String
)

data class SubscriptionInfo(
    val status: String = "ACTIVE_TRIAL", // ACTIVE_TRIAL, ACTIVE_SUBSCRIPTION, GRACE_PERIOD
    val planName: String = "Tawazun Family Shield",
    val price: String = "PKR 299/month",
    val trialDaysRemaining: Int = 5,
    val playBillingId: String = "pk.tawazun.monthly.299",
    val purchaseToken: String = "play_token_enc_91028301928"
)
