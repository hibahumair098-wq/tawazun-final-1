package com.example.service

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

/**
 * ⌨️ TAWAZUN KEYBOARD — Per-App No-Keyboard Lock + asli QWERTY
 *
 * Parent Rules tab se choose karta hai ke KIS app mein keyboard band karna hai
 * (e.g. WhatsApp — app khulti hai, par bacha type nahi kar sakta).
 *
 * Kaam kaise karta hai:
 *   - Jab koi text field khulta hai, system humein batata hai kaunsi app hai
 *     (onStartInput → EditorInfo.packageName)
 *   - Agar wo app parent ki blocked list mein hai (aur child mode + lock ON hai)
 *     → keyboard ka view KABHI nahi dikhta (null keyboard — tap ka koi asar nahi)
 *   - Baqi har app (aur parent mode) mein → Tawazun ka apna asli QWERTY keyboard
 *
 * Prefs ("tawazun_auth" — TawazunRepository likhta hai):
 *   child_mode + typing_lock + keyboard_blocked_apps (appKeys ka set)
 */
class TawazunNullKeyboard : InputMethodService() {

    companion object {
        const val PREFS_NAME = "tawazun_auth"
        const val KEY_CHILD_MODE = "child_mode"
        const val KEY_TYPING_LOCK = "typing_lock"
        const val KEY_BLOCKED_APPS = "keyboard_blocked_apps"

        // Parent ke appKeys → asli Android package names
        val APP_PACKAGES: Map<String, List<String>> = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube", "com.google.android.apps.youtube.kids"),
            "chrome" to listOf("com.android.chrome"),
            "instagram" to listOf("com.instagram.android"),
            "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            "telegram" to listOf("org.telegram.messenger"),
            "snapchat" to listOf("com.snapchat.android")
        )
    }

    private var targetBlocked = false
    private var shiftOn = false
    private val letterKeys = mutableListOf<Button>()
    private var lettersPage: LinearLayout? = null
    private var symbolsPage: LinearLayout? = null

    // ---------------- LOCK LOGIC ----------------

    private fun isTargetAppBlocked(pkg: String?): Boolean {
        if (pkg == null || pkg == packageName) return false // Tawazun khud kabhi blocked nahi
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!(prefs.getBoolean(KEY_CHILD_MODE, false) && prefs.getBoolean(KEY_TYPING_LOCK, false))) return false
        val blockedApps = prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
        if (blockedApps.isEmpty()) return false
        return APP_PACKAGES.entries.any { (key, pkgs) -> pkg in pkgs && key in blockedApps }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        targetBlocked = isTargetAppBlocked(attribute?.packageName)
    }

    /** Blocked app → keyboard ka view kabhi nahi dikhata. */
    override fun onEvaluateInputViewShown(): Boolean = !targetBlocked

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        if (targetBlocked) requestHideSelf(0)
    }

    // ---------------- QWERTY KEYBOARD (allowed apps + parent mode) ----------------

    override fun onCreateInputView(): View = buildKeyboard()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildKeyboard(): View {
        shiftOn = false
        letterKeys.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F3D2E"))
            setPadding(dp(3), dp(5), dp(3), dp(6))
        }
        lettersPage = buildLettersPage()
        symbolsPage = buildSymbolsPage().apply { visibility = View.GONE }
        root.addView(lettersPage)
        root.addView(symbolsPage)
        return root
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun keyBtn(label: String, weight: Float = 1f, onTap: (Button) -> Unit): Button {
        val btn = Button(this).apply {
            text = label
            tag = label
            setTextColor(Color.WHITE)
            textSize = 16f
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#14532D"))
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { onTap(this) }
        }
        btn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        return btn
    }

    private fun letterKey(letter: String): Button {
        val btn = keyBtn(letter) { b ->
            val base = b.tag as String
            commitText(if (shiftOn) base.uppercase() else base)
            if (shiftOn) setShift(false)
        }
        letterKeys.add(btn)
        return btn
    }

    private fun buildLettersPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val r1 = row()
        "qwertyuiop".forEach { r1.addView(letterKey(it.toString())) }
        val r2 = row()
        "asdfghjkl".forEach { r2.addView(letterKey(it.toString())) }
        val r3 = row()
        r3.addView(keyBtn("⇧", 1.5f) { setShift(!shiftOn) })
        "zxcvbnm".forEach { r3.addView(letterKey(it.toString())) }
        r3.addView(keyBtn("⌫", 1.5f) { backspace() })
        val r4 = row()
        r4.addView(keyBtn("?123", 1.5f) { showSymbols(true) })
        r4.addView(keyBtn(",", 1f) { b -> commitText(b.tag as String) })
        r4.addView(keyBtn("space", 4f) { commitText(" ") })
        r4.addView(keyBtn(".", 1f) { b -> commitText(b.tag as String) })
        r4.addView(keyBtn("↵", 1.5f) { sendEnter() })
        page.addView(r1)
        page.addView(r2)
        page.addView(r3)
        page.addView(r4)
        return page
    }

    private fun buildSymbolsPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val r1 = row()
        "1234567890".forEach { r1.addView(keyBtn(it.toString()) { b -> commitText(b.tag as String) }) }
        val r2 = row()
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")").forEach {
            r2.addView(keyBtn(it) { b -> commitText(b.tag as String) })
        }
        val r3 = row()
        listOf("*", "\"", "'", ":", ";", "!", "?").forEach {
            r3.addView(keyBtn(it) { b -> commitText(b.tag as String) })
        }
        r3.addView(keyBtn("⌫", 1.5f) { backspace() })
        val r4 = row()
        r4.addView(keyBtn("ABC", 1.5f) { showSymbols(false) })
        r4.addView(keyBtn(",", 1f) { b -> commitText(b.tag as String) })
        r4.addView(keyBtn("space", 4f) { commitText(" ") })
        r4.addView(keyBtn(".", 1f) { b -> commitText(b.tag as String) })
        r4.addView(keyBtn("↵", 1.5f) { sendEnter() })
        page.addView(r1)
        page.addView(r2)
        page.addView(r3)
        page.addView(r4)
        return page
    }

    private fun setShift(on: Boolean) {
        shiftOn = on
        letterKeys.forEach { b ->
            val base = b.tag as String
            b.text = if (on) base.uppercase() else base
        }
    }

    private fun showSymbols(show: Boolean) {
        lettersPage?.visibility = if (show) View.GONE else View.VISIBLE
        symbolsPage?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun commitText(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun backspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun sendEnter() {
        val ic = currentInputConnection ?: return
        val action = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != 0) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
}
