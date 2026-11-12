package com.example.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ASLI app usage parhne wala engine (Android ka UsageStatsManager).
 *
 * Android ka usool: koi bhi app chup-ke se doosri apps ka usage nahi parh sakta —
 * bacha khud Settings mein "Usage Access" permission deta hai. Tawazun sirf
 * uske baad usage parhta hai. (Transparency by design!)
 */
object UsageTracker {

    // Tawazun ke app keys → Android package names
    private val APP_PACKAGES: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube", "com.google.android.apps.youtube.kids"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "instagram" to listOf("com.instagram.android"),
        "khan_academy" to listOf("org.khanacademy.android"),
        "free_fire" to listOf("com.dts.freefireth", "com.dts.freefiremax"),
        "roblox" to listOf("com.roblox.client"),
        "duolingo" to listOf("com.duolingo"),
        "chrome" to listOf("com.android.chrome")
    )

    /** Kya user ne Usage Access permission diya hai? */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Aaj (raat 12 baje se ab tak) har tracked app ka total usage — minutes mein.
     * Return: appKey → minutes (sirf jinka usage > 0 hai).
     */
    fun todayUsageMinutes(context: Context): Map<String, Int> {
        if (!hasUsageAccess(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val stats = usm.queryAndAggregateUsageStats(calendar.timeInMillis, System.currentTimeMillis())

        val result = mutableMapOf<String, Int>()
        for ((appKey, packages) in APP_PACKAGES) {
            val totalMs = packages.sumOf { pkg -> stats[pkg]?.totalTimeInForeground ?: 0L }
            if (totalMs > 0) {
                result[appKey] = (totalMs / 60000L).toInt()
            }
        }
        return result
    }

    /** Aaj ki date string (Firestore doc IDs ke liye). */
    fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())

    /** Kis app key ke kaunse Android packages hain (Guard ke liye). */
    fun packagesFor(appKey: String): List<String>? = APP_PACKAGES[appKey]
}
