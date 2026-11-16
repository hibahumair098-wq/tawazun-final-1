package com.example

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.model.UserRole
import com.example.data.repository.LinkRequest
import com.example.data.repository.TawazunRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.parent.ParentDashboardScreen
import com.example.ui.student.StudentDeviceScreen
import com.example.ui.theme.TawazunEmeraldPrimary
import com.example.ui.theme.TawazunErrorRed
import com.example.ui.theme.TawazunSurface
import com.example.ui.theme.TawazunTextPrimary
import com.example.ui.theme.TawazunTextSecondary
import com.example.ui.theme.TawazunTheme
import com.example.util.configureAppCheck
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        setContent {
            TawazunTheme(darkTheme = false) {
                val repository = remember { TawazunRepository(applicationContext) }
                val session by repository.currentSession.collectAsState()
                val authError by repository.authError.collectAsState()
                val authLoading by repository.authLoading.collectAsState()
                val verifyNotice by repository.verifyNotice.collectAsState()
                val subscription by repository.subscription.collectAsState()
                val myRequest by repository.myRequest.collectAsState()
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val credentialManager = remember { CredentialManager.create(context) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val current = session) {
                        null -> {
                            AuthScreen(
                                onParentAuth = { email, password, isSignUp ->
                                    coroutineScope.launch {
                                        if (isSignUp) repository.signUpParent(email, password)
                                        else repository.signInParent(email, password)
                                    }
                                },
                                onStudentAuth = { email, password, isSignUp ->
                                    coroutineScope.launch {
                                        if (isSignUp) repository.signUpStudent(email, password)
                                        else repository.signInStudent(email, password)
                                    }
                                },
                                onGoogleSignIn = {
                                    // Naye Firebase project (tawzun-dca15) ka web client ID —
                                    // google-services.json se auto-generate hota hai
                                    val clientId = context.getString(R.string.default_web_client_id)
                                    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                                    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

                                    coroutineScope.launch {
                                        try {
                                            val result = credentialManager.getCredential(context as Activity, request)
                                            val credential = result.credential
                                            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                                // REAL Firebase login with Google token
                                                repository.loginWithGoogleIdToken(googleIdToken)
                                            }
                                        } catch (e: GetCredentialCancellationException) {
                                            // User ne cancel kiya — koi error nahi
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Google Sign-In error", e)
                                            // SECURITY: error par login NAHI hota — wazahat ke sath batado
                                            repository.showAuthError(
                                                "Google Sign-In nahi chala. Do wajah ho sakti hain: " +
                                                    "(1) Firebase console mein SHA-1 add nahi hua hai, " +
                                                    "(2) test-environment/emulator par Google account nahi khulta. Asli phone par try karein."
                                            )
                                        }
                                    }
                                },
                                authError = authError,
                                authLoading = authLoading,
                                onDismissError = { repository.clearAuthError() },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        else -> {
                            BackHandler {
                                repository.logout()
                            }

                            if (current.role == UserRole.STUDENT && current.familyId.isBlank()) {
                                // ⏳ Student account ban gaya — parent ke add hone ka intezar
                                WaitingForParentScreen(
                                    email = current.email,
                                    myRequest = myRequest,
                                    onSendRequest = { parentEmail ->
                                        repository.sendRequestToParent(parentEmail)
                                    },
                                    onLogout = { repository.logout() },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else if (!current.isEmailVerified) {
                                // 🔒 VERIFICATION GATE v2 — verify hone tak andar entry nahi
                                EmailVerificationGateScreen(
                                    email = current.email,
                                    notice = verifyNotice,
                                    onCheckAgain = {
                                        coroutineScope.launch { repository.refreshVerificationStatus() }
                                    },
                                    onResend = { repository.resendVerificationEmail() },
                                    onLogout = { repository.logout() },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else if (current.role == UserRole.STUDENT && subscription.status == "TRIAL_EXPIRED") {
                                // ⏰ Trial khatam — student lock (parent renew kare to khulega)
                                TrialExpiredScreen(
                                    email = current.email,
                                    onLogout = { repository.logout() },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    when (current.role) {
                                        UserRole.PARENT -> {
                                            ParentDashboardScreen(
                                                repository = repository,
                                                onLogout = { repository.logout() },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        UserRole.STUDENT -> {
                                            StudentDeviceScreen(
                                                repository = repository,
                                                onLogout = { repository.logout() },
                                                modifier = Modifier.weight(1f)
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
    }
}

/**
 * 🔒 Email Verification Gate v2 — verify hone tak andar ka rasta band.
 * Resend par ASLI feedback milta hai (email chala gaya ya fail hua).
 */
@Composable
fun EmailVerificationGateScreen(
    email: String,
    notice: String?,
    onCheckAgain: () -> Unit,
    onResend: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ⏳ 60-second cooldown — Firebase ka "unusual activity" block parhne se bachne ke liye
    var cooldownUntil by remember { mutableStateOf(0L) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cooldownUntil) {
        while (System.currentTimeMillis() < cooldownUntil) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
        nowMs = System.currentTimeMillis()
    }
    val secondsLeft = ((cooldownUntil - nowMs) / 1000L).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TawazunSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📬", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Email Verify Karein",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hum ne verify-link bheja hai:\n$email\n\n1️⃣ Apni Gmail kholen — SPAM folder bhi check karein\n2️⃣ \"Verify your email\" par tap karein (link Chrome mein khulega)\n3️⃣ Wapas Tawazun aayein aur neeche \"Check Karo\" dabayein",
                    fontSize = 13.sp,
                    color = TawazunTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                if (notice != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = notice,
                        fontSize = 12.sp,
                        color = if (notice.startsWith("✓")) TawazunEmeraldPrimary else TawazunErrorRed,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onCheckAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("✅ Maine Link Click Kiya — Check Karo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onResend()
                        cooldownUntil = System.currentTimeMillis() + 60_000L
                    },
                    enabled = secondsLeft <= 0,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (secondsLeft > 0) "⏳ ${secondsLeft}s mein dobara..." else "📧 Email Dobara Bhejo (Resend)",
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onLogout) {
                    Text("Log Out", fontSize = 12.sp, color = TawazunTextSecondary)
                }
            }
        }
    }
}

/**
 * ⏳ Waiting for Parent — student ka account ban gaya / login ho gaya,
 * magar parent ne abhi is email ko apni app mein add nahi kiya.
 * Parent ke add karte hi yeh screen khud student app mein badal jati hai.
 */
@Composable
fun WaitingForParentScreen(
    email: String,
    myRequest: LinkRequest?,
    onSendRequest: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TawazunSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⏳", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Account Tayyar Hai!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ab parent se kahin ke apni Tawazun app mein aap ko add karein — yeh email use karein:",
                    fontSize = 13.sp,
                    color = TawazunTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = email,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunEmeraldPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Parent ke add karte hi yeh screen khud badal jayegi — kuch karne ki zaroorat nahi. 🔄",
                    fontSize = 12.sp,
                    color = TawazunTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                // 📨 YA — student khud parent ko request bhej sake
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "— YA —",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (myRequest != null && myRequest.status == "pending") {
                    Text(
                        text = "✓ Request bheji gayi: ${myRequest.parentEmail}\nParent ke Tawazun app mein Approve karte hi ye screen khud badal jayegi. 📨",
                        fontSize = 12.sp,
                        color = TawazunEmeraldPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                } else {
                    if (myRequest != null && myRequest.status == "rejected") {
                        Text(
                            text = "✗ Parent ne ye request mana kar di thi. Naya parent email try karein ya parent se seedha add karwayein.",
                            fontSize = 12.sp,
                            color = TawazunErrorRed,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    var parentEmail by remember(myRequest?.id) { mutableStateOf("") }
                    OutlinedTextField(
                        value = parentEmail,
                        onValueChange = { parentEmail = it },
                        label = { Text("Parent ka email (jaise abc@gmail.com)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onSendRequest(parentEmail) },
                        enabled = parentEmail.contains("@"),
                        colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📨 Parent Ko Request Bhejo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = onLogout) {
                    Text("Log Out", fontSize = 12.sp, color = TawazunTextSecondary)
                }
            }
        }
    }
}

/**
 * ⏰ TrialExpiredScreen — 3-din free trial khatam hone par STUDENT ka lock.
 * Parent renew kare (₨299/month — Coming Soon, ya demo extend) to ye lock khud khatam ho jata hai.
 */
@Composable
fun TrialExpiredScreen(
    email: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TawazunSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⏰", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Free Trial Khatam Ho Gayi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunErrorRed
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tawazun ka 3-din free trial mukammal ho gaya hai ($email).\n\nParent se kahin ke apni Tawazun app ke Billing tab mein renewal karein — ₨299/month.\nRenew hote hi ye app khud khul jayegi. ✨",
                    fontSize = 13.sp,
                    color = TawazunTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = onLogout) {
                    Text("Log Out", fontSize = 12.sp, color = TawazunTextSecondary)
                }
            }
        }
    }
}
