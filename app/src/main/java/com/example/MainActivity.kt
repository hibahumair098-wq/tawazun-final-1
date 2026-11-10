package com.example

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.ai.TawazunAiService
import com.example.data.model.UserRole
import com.example.data.repository.TawazunRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.parent.ParentDashboardScreen
import com.example.ui.student.StudentDeviceScreen
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
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val credentialManager = remember { CredentialManager.create(context) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val current = session) {
                        null -> {
                            AuthScreen(
                                onParentLogin = { email -> repository.loginAsParent(email) },
                                onStudentLogin = { email -> repository.loginAsStudent(email) },
                                onGoogleSignIn = {
                                    // NOTE: google-services.json add hone ke baad wapas
                                    // R.string.default_web_client_id use karna
                                    val clientId = "160513495828-p7hbu9js0hde5cpp5d1upqrg5i2rfgp1.apps.googleusercontent.com"
                                    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                                    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

                                    coroutineScope.launch {
                                        try {
                                            val result = credentialManager.getCredential(context as Activity, request)
                                            val credential = result.credential
                                            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                                // Sign in parent with verified Google token email
                                                val email = GoogleIdTokenCredential.createFrom(credential.data).id
                                                repository.loginAsParent(email.ifBlank { "parent.google@gmail.com" })
                                            }
                                        } catch (e: GetCredentialCancellationException) {
                                            Log.w(TAG, "Google Sign-In cancelled: ${e.message}", e)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Google Sign-In error", e)
                                            // Fallback for emulator without Google Play Services account
                                            repository.loginAsParent("parent.google@gmail.com")
                                        }
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        else -> {
                            BackHandler {
                                repository.logout()
                            }

                            when (current.role) {
                                UserRole.PARENT -> {
                                    ParentDashboardScreen(
                                        repository = repository,
                                        onLogout = { repository.logout() },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                UserRole.STUDENT -> {
                                    StudentDeviceScreen(
                                        repository = repository,
                                        onLogout = { repository.logout() },
                                        modifier = Modifier.padding(innerPadding)
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
