package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onParentLogin: (String) -> Unit,
    onStudentLogin: (String) -> Unit,
    onGoogleSignIn: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Parent, 1 = Student
    var parentEmail by remember { mutableStateOf("parent.ahmed@gmail.com") }
    var studentEmail by remember { mutableStateOf("student.zain@gmail.com") }
    var password by remember { mutableStateOf("••••••••") }
    var consentChecked by remember { mutableStateOf(true) }
    var showConsentDialog by remember { mutableStateOf(false) }

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

        // Crucial User Constraint Notice Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = TawazunSurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TawazunGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Privacy Isolation: Student monitoring data is visible ONLY when the student account is logged out and the parent account logs in via email.",
                    fontSize = 12.sp,
                    color = TawazunTextPrimary,
                    lineHeight = 16.sp
                )
            }
        }

        // Parent vs Student Mode Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = TawazunSurface,
            contentColor = TawazunEmeraldPrimary,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = TawazunEmeraldPrimary
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Parent Login", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Student Login", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedTab == 0) {
            // --- PARENT LOGIN FORM ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Parental Dashboard Access",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TawazunTextPrimary
                    )
                    Text(
                        text = "Sign in to monitor balance, review homework, and set app limits.",
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
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password or Email-Link") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
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
                        onClick = { onParentLogin(parentEmail) },
                        enabled = parentEmail.isNotBlank() && consentChecked,
                        colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("parent_login_button")
                    ) {
                        Text("Sign In as Parent", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            parentEmail = "parent.ahmed@gmail.com"
                            onParentLogin("parent.ahmed@gmail.com")
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Demo: Sign in as Ahmed (Parent of Zain & Ayla)", fontSize = 13.sp)
                    }

                    if (onGoogleSignIn != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onGoogleSignIn,
                            colors = ButtonDefaults.buttonColors(containerColor = TawazunSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            Text("Sign in with Google", fontSize = 14.sp, color = TawazunTextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            // --- STUDENT LOGIN FORM ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Student Device Sign In",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TawazunTextPrimary
                    )
                    Text(
                        text = "Students access homework checklist, school WhatsApp, and timer status.",
                        fontSize = 12.sp,
                        color = TawazunTextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = studentEmail,
                        onValueChange = { studentEmail = it },
                        label = { Text("Student Email Address") },
                        leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_email_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = "••••••••",
                        onValueChange = {},
                        label = { Text("Student Device PIN / Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onStudentLogin(studentEmail) },
                        enabled = studentEmail.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TawazunGold),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("student_login_button")
                    ) {
                        Text("Log In as Student", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            studentEmail = "student.zain@gmail.com"
                            onStudentLogin("student.zain@gmail.com")
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Demo: Log In as Zain (Grade 9)", fontSize = 13.sp)
                    }
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
                    text = "• WhatsApp cannot be blocked outright because school homework arrives in class groups. We provide Smart Chat read-only mode.\n• Entertainment unlocks only after verified homework completion.\n• Strict 3-day data retention: mirror sessions and events auto-expire.\n• PKR 299/month via Google Play Billing. No credit card required.",
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
