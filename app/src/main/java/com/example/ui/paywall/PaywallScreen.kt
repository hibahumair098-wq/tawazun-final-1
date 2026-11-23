package com.example.ui.paywall

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.trial.TrialClock
import com.example.ui.theme.TawazunAmberSoft
import com.example.ui.theme.TawazunCream
import com.example.ui.theme.TawazunEmeraldPrimary
import com.example.ui.theme.TawazunGold
import com.example.ui.theme.TawazunMintSoft
import com.example.ui.theme.TawazunSurface
import com.example.ui.theme.TawazunTextPrimary
import com.example.ui.theme.TawazunTextSecondary
import kotlinx.coroutines.delay

/**
 * ⏳ TRIAL PAYWALL — 72 ghante khatam hote hi khud khul jati hai.
 * Parent "payment ho gaya" dabaye to subscription activate ho jati hai.
 */
@Composable
fun PaywallScreen(
    remainingMs: Long,
    onActivate: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expired = remainingMs <= 0L
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TawazunCream)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (expired) "⏰" else "⏳", fontSize = 52.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Tawazun",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TawazunEmeraldPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (expired) "Aap ki 3-din free trial khatam ho gayi hai" else "Free trial chal rahi hai",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TawazunTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (expired) "Bachay ke device par Tawazun ab locked hai. ₨299/month subscription lete hi sab kuch wapas khul jayega."
                else "Timer khatam hote hi ye screen khud khul jayegi.",
                fontSize = 12.sp,
                color = TawazunTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Countdown / expired badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (expired) TawazunAmberSoft else TawazunMintSoft
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (expired) "TRIAL KHATAM" else "BAQA COUNTDOWN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TawazunTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (expired) "00:00:00" else TrialClock.format(remainingMs),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (expired) TawazunGold else TawazunEmeraldPrimary
                    )
                    Text(
                        if (expired) "72 ghante poore" else TrialClock.human(remainingMs),
                        fontSize = 11.sp,
                        color = TawazunTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Plan card
            Card(
                colors = CardDefaults.cardColors(containerColor = TawazunSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "Tawazun Family Shield",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TawazunTextPrimary
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("₨299", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TawazunGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("/ month", fontSize = 12.sp, color = TawazunTextSecondary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(
                        "✅ Screen-time limits + app timers",
                        "✅ Safe Web (harmful sites band)",
                        "✅ Keyboard lock (WhatsApp waghera)",
                        "✅ Homework unlock + mirroring",
                        "✅ 1 bacha (extra bacha ₨99/month)"
                    ).forEach {
                        Text(it, fontSize = 12.sp, color = TawazunTextPrimary, lineHeight = 21.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onActivate,
                colors = ButtonDefaults.buttonColors(containerColor = TawazunEmeraldPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("✅ Payment ho gaya — Activate karein", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "JazzCash / Easypaisa gateway jald aa raha hai. Filhal activation manual hai — payment ke baad ye button dabayein.",
                fontSize = 10.sp,
                color = TawazunTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onLogout) {
                Text("Log out", fontSize = 12.sp, color = TawazunTextSecondary)
            }
        }
    }
}

/** Overview / dashboard ke liye chhota live countdown card (har second update). */
@Composable
fun TrialCountdownCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var remaining by remember { mutableLongStateOf(TrialClock.remainingMs(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            remaining = TrialClock.remainingMs(context)
            delay(1000L)
        }
    }
    if (TrialClock.isPaid(context)) return

    val expired = remaining <= 0L
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (expired) TawazunAmberSoft else TawazunMintSoft
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expired) "⏰" else "⏳", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (expired) "Free trial khatam — subscription zaroori hai" else "Free trial ka waqt",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TawazunTextPrimary
                )
                Text(
                    if (expired) "₨299/month — Billing tab se activate karein"
                    else "${TrialClock.format(remaining)}  •  ${TrialClock.human(remaining)}",
                    fontSize = 11.sp,
                    color = TawazunTextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(TawazunGold, TawazunEmeraldPrimary)
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("3-DAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TawazunSurface)
            }
        }
    }
}
