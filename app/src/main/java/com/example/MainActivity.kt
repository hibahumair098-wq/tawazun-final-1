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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.repository.TawazunRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.parent.ParentDashboardScreen
import com.example.ui.student.StudentDeviceScreen
import com.example.ui.theme.TawazunEmeraldPrimary
import com.example.ui.theme.TawazunSurface
import com.example.ui.theme.TawazunTextPrimary
import com.example.ui.theme.TawazunTextSecondary
import com.example.ui.theme.TawazunTheme
import com.example.util.configureAppCheck
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        setContent {
            TawazunTheme(darkTheme = true) {
                val repository = remember { TawazunRepository() }
                val session by repository.currentSession.collectAsState()
                val authError by repository.authError.collectAsState()
                val authLoading by repository.authLoading.collectAsState()
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
                                            Log.w(TAG, "Google Sign-In cancelled: ${e.message}", e)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Google Sign-In error", e)
                                            // SECURITY: error par login NAHI hota
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

                            if (!current.isEmailVerified) {
                                // 🔒 VERIFICATION GATE: jab tak email verify nahi, andar entry NAHI
                                EmailVerificationGateScreen(
                                    email = current.email,
                                    onCheckAgain = {
                                        coroutineScope.launch { repository.refreshVerificationStatus() }
                                    },
                                    onResend = { repository.resendVerificationEmail() },
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
 * 🔒 Email Verification Gate — verify hone tak app ka andar ka rasta band.
 * User sirf 3 kaam kar sakta hai: link click karke "Check" dabana,
 * email dobara mangaana, ya log out.
 */
@Composable
fun EmailVerificationGateScreen(
    email: String,
    onCheckAgain: () -> Unit,
    onResend: () -> Unit,
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
                    text = "Hum ne verify-link bheja hai:\n$email\n\nApna inbox (aur SPAM folder) kholo aur \"Verify your email\" par click karo. Tab hi app mein entry milegi.",
                    fontSize = 13.sp,
                    color = TawazunTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
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
                    onClick = onResend,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📧 Email Dobara Bhejo (Resend)", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onLogout) {
                    Text("Log Out", fontSize = 12.sp, color = TawazunTextSecondary)
                }
            }
        }
    }
}
