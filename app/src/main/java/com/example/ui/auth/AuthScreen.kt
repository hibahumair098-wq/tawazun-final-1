package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TawazunEmeraldPrimary
import com.example.ui.theme.TawazunGold
import com.example.ui.theme.TawazunSurface
import com.example.ui.theme.TawazunSurfaceVariant
import com.example.ui.theme.TawazunTextPrimary
import com.example.ui.theme.TawazunTextSecondary
import com.example.ui.theme.TawazunWarningAmber

/**
 * 🔐 SIMPLE LOGIN — sirf parent ka email + password.
 * Student accounts ka concept khatam — parent hi ek login, aur bachay ka mode
 * parent ke dashboard se khulta hai (password se wapas).
 */
@Composable
fun AuthScreen(
    onParentAuth: (email: String, password: String, isSignUp: Boolean) -> Unit,
    authError: String? = null,
    authLoading: Boolean = false,
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var parentEmail by remember { mutableStateOf("") }
    var parentPassword by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var consentChecked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Brand Header
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Tawazun Shield",
                tint = TawazunEmeraldPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "توازن • Tawazun",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TawazunTextPrimary
        )

        Text(
            text = "Screen-time balance for Pakistani families",
            fontSize = 14.sp,
            color = TawazunTextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // REAL Auth error message (Firebase se)
        if (authError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = TawazunWarningAmber),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = authError,
                        fontSize = 12.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) { Text("OK") }
                }
            }
        }

        // --- PARENT LOGIN FORM (sirf yehi!) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TawazunSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Parent Login",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunTextPrimary
                )
                Text(
                    text = "Apne email se login karein — phir bachay ka mode khol kar us phone par limits lagayen.",
                    fontSize = 12.sp,
                    color = TawazunTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = parentEmail,
                    onValueChange = { parentEmail = it },
                    label = { Text("Parent Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parent_email_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = parentPassword,
                    onValueChange = { parentPassword = it },
                    label = { Text("Password (min 6 characters)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Parental Consent Checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = { consentChecked = it },
                        colors = CheckboxDefaults.colors(checkedColor = TawazunEmeraldPrimary)
                    )
                    Text(
                        text = "I accept Tawazun Parental Consent (v1.2-PK) and 3-Day Data Retention Guarantee.",
                        fontSize = 11.sp,
                        color = TawazunTextSecondary,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onParentAuth(parentEmail, parentPassword, isSignUp) },
                    enabled = !authLoading && parentEmail.contains("@") && parentPassword.length >= 6 && consentChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("parent_login_button")
                ) {
                    Text(
                        when {
                            authLoading -> "Please wait..."
                            isSignUp -> "Create Parent Account"
                            else -> "Sign In as Parent"
                        },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { isSignUp = !isSignUp },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSignUp) "Already have an account? Sign In"
                        else "New here? Create an account (Sign Up)",
                        fontSize = 13.sp,
                        color = TawazunEmeraldPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy & Pakistani Family Promise
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TawazunSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🇵🇰 Built for Pakistani Realities",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TawazunGold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Ek login, ek bacha — simple.\n• Entertainment unlocks only after verified homework completion.\n• Strict 3-day data retention: mirror sessions and events auto-expire.\n• PKR 299/month via Google Play Billing. No credit card required.",
                    fontSize = 11.sp,
                    color = TawazunTextSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Support: support@tawazun.pk (No phone or SMS support)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TawazunEmeraldPrimary
                )
            }
        }
    }
}
