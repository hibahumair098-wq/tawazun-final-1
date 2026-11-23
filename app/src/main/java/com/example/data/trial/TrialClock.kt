package com.example.data.trial

import android.content.Context

/**
 * ⏳ TAWAZUN TRIAL CLOCK — 72 ghante ka sakht countdown.
 *
 * Anchor (pehli dafa app khulne ka waqt) SharedPreferences mein SIRF EK DAFA
 * likha jata hai aur phir kabhi badla nahi jata — is liye:
 *   • logout / login / dobara login karne se timer RESET nahi hota
 *   • app band karne / phone restart karne se timer rukta nahi (wall-clock chalta hai)
 *   • Firestore ka trialEnd agar is se LATER ho to bhi local anchor jeetta hai
 *     (earliest end wins → koi bhi taraf se trial extend nahi ho sakta)
 */
object TrialClock {
    const val TRIAL_HOURS = 72L
    const val TRIAL_MS = TRIAL_HOURS * 3600L * 1000L

    private const val PREFS = "tawazun_trial"
    private const val KEY_ANCHOR = "trial_anchor_ms"
    private const val KEY_PAID = "paid"

    /** Pehli dafa ka waqt — set once, never overwritten. */
    fun anchorMs(context: Context?): Long {
        val ctx = context ?: return System.currentTimeMillis()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getLong(KEY_ANCHOR, 0L)
        if (saved > 0L) return saved
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_ANCHOR, now).apply()
        return now
    }

    fun endMs(context: Context?): Long = anchorMs(context) + TRIAL_MS

    fun remainingMs(context: Context?): Long {
        if (isPaid(context)) return 0L
        return (endMs(context) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun isExpired(context: Context?): Boolean = !isPaid(context) && remainingMs(context) <= 0L

    fun isPaid(context: Context?): Boolean =
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getBoolean(KEY_PAID, false) ?: false

    fun markPaid(context: Context?) {
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_PAID, true)?.apply()
    }

    /** "61:23:45" — 3 hisse wala countdown. */
    fun format(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%02d:%02d:%02d".format(total / 3600L, (total % 3600L) / 60L, total % 60L)
    }

    /** "2 din 14 ghante baaki" — insaani zubaan. */
    fun human(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val d = total / 86400L
        val h = (total % 86400L) / 3600L
        val m = (total % 3600L) / 60L
        return when {
            d > 0 -> "$d din $h ghante baaki"
            h > 0 -> "$h ghante $m minute baaki"
            else -> "$m minute baaki"
        }
    }
}
