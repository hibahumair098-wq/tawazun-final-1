package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * 🛡️ TAWAZUN GUARD — asli app blocking ka dil.
 *
 * Ye Android ki AccessibilityService hai. Bacha jab bhi koi blocked app kholta hai
 * (jaise YouTube jab limit khatam ho), Tawazun usay turant home screen par bhej deta hai.
 *
 * Privacy ka khayal: hum sirf app ka NAAM parhte hain (package name) —
 * screen ka content kabhi nahi parha jata (canRetrieveWindowContent = false).
 */
class TawazunGuardService : AccessibilityService() {

    private var lastBlockAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // Tawazun apne aap ko block nahi karta

        if (!GuardState.isBlocked(this, pkg)) return

        // Debounce: 800ms mein ek hi dafa kick
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 800) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, "⛔ Tawazun: ye app locked hai!", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        // Kuch nahi — service simple hai
    }
}

/**
 * Guard ka memory + cache.
 * Repository isay update karta hai (kya block hai), service isay parhta hai —
 * chahe app band ho jaye, service chalti rehti hai (cache se).
 */
object GuardState {

    private const val PREFS = "tawazun_guard"
    private const val KEY_BLOCKED = "blocked_pkgs"

    @Volatile
    private var blockedInMemory: Set<String>? = null

    /** Repository se call hota hai jab policies/usage/homework badlein. */
    fun update(context: Context?, blockedPackages: Set<String>) {
        blockedInMemory = blockedPackages
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putStringSet(KEY_BLOCKED, blockedPackages)
            ?.apply()
    }

    /** Kya ye app is waqt block hai? (memory pehle, warna cache) */
    fun isBlocked(context: Context, pkg: String): Boolean {
        val memory = blockedInMemory
        if (memory != null) return memory.contains(pkg)
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        return saved.contains(pkg)
    }

    /** Kya bacha ne Accessibility Service on ki hai? */
    fun isGuardEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = ComponentName(context, TawazunGuardService::class.java)
        return enabled.contains(component.flattenToString()) ||
            enabled.contains(component.flattenToShortString()) ||
            enabled.contains("TawazunGuardService")
    }
}

