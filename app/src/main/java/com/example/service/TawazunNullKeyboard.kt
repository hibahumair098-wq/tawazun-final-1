package com.example.service

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout

/**
 * ⌨️ TAWAZUN TYPING LOCK KEYBOARD — "Null Keyboard" (user ki khud ki research!)
 *
 * Ye ek IME (input method) hai jo BACHAY KE PHONE ka soft keyboard hi nahi dikhata:
 *   - onCreateInputView() NULL return karta hai → jab ye keyboard selected ho,
 *     kisi bhi app mein text field par tap karne ka KOI ASAR NAHI (keyboard nahi khulta)
 *   - Chrome mein URL nahi likh sakta, search nahi kar sakta, WhatsApp mein type nahi kar sakta
 *
 * Kab lock hota hai? SIRF jab (prefs "tawazun_auth"):
 *   - child_mode = true (bachay ka mode chal raha hai) AUR
 *   - typing_lock = true (parent ne Rules tab se lock ON kiya)
 * Parent mode mein lock kabhi nahi lagta — aur ek-tap button se normal keyboard wapas.
 *
 * Escape (parent): ParentPinDialog mein "⌨️ Keyboard nahi khul raha?" button →
 * system keyboard picker → parent apna normal keyboard chun kar password likhta hai.
 * (Bacha ye kar bhi le to password ke bina child mode se bahar nahi ja sakta.)
 */
class TawazunNullKeyboard : InputMethodService() {

    companion object {
        // Wahi prefs file jo TawazunRepository use karta hai — dono ek hi app mein hain
        const val PREFS_NAME = "tawazun_auth"
        const val KEY_CHILD_MODE = "child_mode"
        const val KEY_TYPING_LOCK = "typing_lock"
    }

    /** Lock sirf tab: child mode ON + parent ne typing lock ON kiya ho. */
    private fun isLockedNow(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CHILD_MODE, false) &&
                prefs.getBoolean(KEY_TYPING_LOCK, false)
    }

    /**
     * 🔒 ASLI TRICK: view NULL → soft keyboard kabhi nahi banega/dikhega.
     * (Hardware/physical keyboard chalta rehta hai — research ke mutabiq.)
     */
    override fun onCreateInputView(): View? {
        if (isLockedNow()) return null

        // 🔓 Lock OFF (parent mode): chhota bar — ek tap mein normal keyboard wapas
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0F3D2E"))
            gravity = Gravity.CENTER
        }
        val btn = Button(this).apply {
            text = "⌨️ Wapas normal keyboard (Tawazun Lock off)"
            isAllCaps = false
            textSize = 13f
            setOnClickListener { switchToNormalKeyboard() }
        }
        bar.addView(
            btn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        return bar
    }

    /** System poochta "keyboard dikhau?" — locked = NAHI (view hi nahi banega). */
    override fun onEvaluateInputViewShown(): Boolean = !isLockedNow()

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        if (isLockedNow()) {
            requestHideSelf(0) // extra safety — koi purani keyboard na dikhe
        }
    }

    /** Parent ke liye: pichla normal keyboard wapas (ek tap), warna picker khol do. */
    private fun switchToNormalKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window?.window?.attributes?.token
            if (token == null || !imm.switchToLastInputMethod(token)) {
                imm.showInputMethodPicker()
            }
        } catch (_: Exception) {
        }
    }
}
