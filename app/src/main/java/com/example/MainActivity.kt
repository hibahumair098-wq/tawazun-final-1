package com.example

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.theme.TawazunTheme
import com.example.ui.theme.TawazunWarningAmber
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

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                // ⚠️ Email abhi VERIFY nahi hua — inbox mein link click karo
                                if (!current.isEmailVerified) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = TawazunWarningAmber),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "⚠️ Email not verified",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = "Apna inbox kholo aur \"Verify your email\" wala link click karo. Phir app dobara kholo.",
                                                    fontSize = 11.sp,
                                                    color = Color.Black
                                                )
                                            }
                                            TextButton(onClick = { repository.resendVerificationEmail() }) {
                                                Text("Resend", color = Color.Black)
                                            }
                                        }
                                    }
                                }

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

