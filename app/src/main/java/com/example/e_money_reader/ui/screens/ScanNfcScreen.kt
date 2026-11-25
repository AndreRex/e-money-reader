package com.example.e_money_reader.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.e_money_reader.BuildConfig
import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.CardIssuer
import com.example.e_money_reader.ui.ScanUiState
import com.example.e_money_reader.ui.theme.TapAccentGreen
import com.example.e_money_reader.ui.theme.TapAccentGreenBg
import com.example.e_money_reader.ui.theme.TapAccentRed
import com.example.e_money_reader.ui.theme.TapAccentRedBg
import com.example.e_money_reader.ui.theme.TapBgElevated
import com.example.e_money_reader.ui.theme.TapBgPrimary
import com.example.e_money_reader.ui.theme.TapBgSurface
import com.example.e_money_reader.ui.theme.TapBorderSubtle
import com.example.e_money_reader.ui.theme.TapTextPrimary
import com.example.e_money_reader.ui.theme.TapTextSecondary
import com.example.e_money_reader.ui.theme.formatRupiah
import com.example.e_money_reader.ui.theme.issuerCardGradient

@Composable
fun ScanNfcScreen(
    scanState: ScanUiState,
    isNfcSupported: Boolean,
    isNfcEnabled: Boolean,
    onRetry: () -> Unit,
    onConfirmSetActive: () -> Unit,
    onKeepCurrentActive: () -> Unit,
    onCancel: () -> Unit,
    onSimulateDebugScan: (CardIssuer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BackHandler {
        if (scanState is ScanUiState.Success) {
            onKeepCurrentActive()
        } else {
            onCancel()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TapBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(TapBgSurface)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TapTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = when (scanState) {
                    is ScanUiState.Success -> "Card Detected"
                    is ScanUiState.Error -> "Scan Failed"
                    else -> "Scan NFC Card"
                },
                color = TapTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(38.dp))
        }

        when (scanState) {
            is ScanUiState.Success -> {
                ScanSuccessContent(
                    card = scanState.card,
                    requiresActiveConfirmation = scanState.requiresActiveConfirmation,
                    onConfirmSetActive = onConfirmSetActive,
                    onKeepCurrentActive = onKeepCurrentActive,
                    modifier = Modifier.weight(1f)
                )
            }

            is ScanUiState.Error -> {
                ScanErrorContent(
                    message = scanState.message,
                    onRetry = onRetry,
                    onCancel = onCancel,
                    modifier = Modifier.weight(1f)
                )
            }

            else -> {
                ScanWaitingContent(
                    isNfcSupported = isNfcSupported,
                    isNfcEnabled = isNfcEnabled,
                    onOpenNfcSettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        }
                    },
                    onCancel = onCancel,
                    onSimulateDebugScan = onSimulateDebugScan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScanWaitingContent(
    isNfcSupported: Boolean,
    isNfcEnabled: Boolean,
    onOpenNfcSettings: () -> Unit,
    onCancel: () -> Unit,
    onSimulateDebugScan: (CardIssuer) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfcPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(184.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .clip(CircleShape)
                .background(TapAccentGreen.copy(alpha = 0.08f))
                .border(1.dp, TapAccentGreen.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .clip(CircleShape)
                    .background(TapAccentGreen.copy(alpha = 0.16f))
                    .border(1.5.dp, TapAccentGreen.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(TapAccentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Nfc,
                        contentDescription = null,
                        tint = TapBgPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(24.dp))

        Text(
            text = "Hold card against phone back",
            color = TapTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "Compatible with Mandiri e-Money, BCA Flazz, BNI TapCash & BRI Brizzi",
            color = TapTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.size(16.dp))

        when {
            !isNfcSupported -> {
                StatusPill(
                    text = "● Sensor NFC tidak tersedia di perangkat ini",
                    bg = TapAccentRedBg,
                    fg = TapAccentRed,
                    border = TapAccentRed
                )
            }

            !isNfcEnabled -> {
                StatusPill(
                    text = "● NFC Nonaktif · Ketuk untuk Buka Pengaturan",
                    bg = TapAccentRedBg,
                    fg = TapAccentRed,
                    border = TapAccentRed,
                    onClick = onOpenNfcSettings
                )
            }

            else -> {
                StatusPill(
                    text = "● Antenna Active · 13.56 MHz",
                    bg = TapAccentGreenBg,
                    fg = TapAccentGreen,
                    border = TapAccentGreen
                )
            }
        }

        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.size(20.dp))
            Text(
                text = "Debug Simulator (Emulator):",
                color = TapTextSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Mandiri" to CardIssuer.MANDIRI,
                    "BCA Flazz" to CardIssuer.BCA_FLAZZ,
                    "TapCash" to CardIssuer.BNI_TAPCASH
                ).forEach { (label, issuer) ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TapBgElevated)
                            .border(1.dp, TapBorderSubtle, CircleShape)
                            .clickable { onSimulateDebugScan(issuer) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ $label",
                            color = TapTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onCancel,
            shape = CircleShape,
            border = BorderStroke(1.dp, TapBorderSubtle),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = TapBgSurface,
                contentColor = TapTextPrimary
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cancel",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ScanSuccessContent(
    card: CardEntity,
    requiresActiveConfirmation: Boolean,
    onConfirmSetActive: () -> Unit,
    onKeepCurrentActive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(TapAccentGreen.copy(alpha = 0.14f))
                .border(1.5.dp, TapAccentGreen.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(TapAccentGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = TapBgPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.size(18.dp))

        Text(
            text = "Read successfully",
            color = TapTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.size(6.dp))

        Text(
            text = "${card.customLabel} balance updated",
            color = TapTextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.size(12.dp))

        StatusPill(
            text = "• ${card.maskedCardNumber} · ${formatRupiah(card.lastBalance)}",
            bg = TapAccentGreenBg,
            fg = TapAccentGreen,
            border = TapAccentGreen
        )

        Spacer(modifier = Modifier.size(20.dp))

        val cardShape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(issuerCardGradient(card.issuer))
                .border(1.dp, TapAccentGreen.copy(alpha = 0.5f), cardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${card.issuer.displayName.uppercase()} · DETECTED",
                color = Color(0xFFA7F3D0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = card.customLabel,
                color = TapTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatRupiah(card.lastBalance),
                color = TapTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (requiresActiveConfirmation) {
            Button(
                onClick = onConfirmSetActive,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TapTextPrimary,
                    contentColor = TapBgPrimary
                ),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Confirm & Set as Active",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            OutlinedButton(
                onClick = onKeepCurrentActive,
                shape = CircleShape,
                border = BorderStroke(1.dp, TapBorderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = TapBgSurface,
                    contentColor = TapTextPrimary
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Done (Keep Current Active)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = onConfirmSetActive,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TapTextPrimary,
                    contentColor = TapBgPrimary
                ),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Confirm & Done",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScanErrorContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(TapAccentRedBg)
                .border(1.5.dp, TapAccentRed.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = TapAccentRed,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.size(18.dp))

        Text(
            text = "Kartu Tidak Terbaca",
            color = TapTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = message,
            color = TapTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onRetry,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = TapTextPrimary,
                contentColor = TapBgPrimary
            ),
            contentPadding = PaddingValues(vertical = 15.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Coba Scan Lagi",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.size(10.dp))

        OutlinedButton(
            onClick = onCancel,
            shape = CircleShape,
            border = BorderStroke(1.dp, TapBorderSubtle),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = TapBgSurface,
                contentColor = TapTextPrimary
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Kembali",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    bg: Color,
    fg: Color,
    border: Color,
    onClick: (() -> Unit)? = null
) {
    val base = Modifier
        .clip(CircleShape)
        .background(bg)
        .border(1.dp, border.copy(alpha = 0.65f), CircleShape)
    val mod = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = mod.padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
