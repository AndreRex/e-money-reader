package com.example.e_money_reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.e_money_reader.data.local.CardIssuer
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val TapBgPrimary = Color(0xFF0B0B0E)
val TapBgSurface = Color(0xFF18181D)
val TapBgElevated = Color(0xFF222229)
val TapBorderSubtle = Color(0xFF262630)
val TapTextPrimary = Color(0xFFFFFFFF)
val TapTextSecondary = Color(0xFF9CA3AF)
val TapTextMuted = Color(0xFF6B7280)
val TapAccentGreen = Color(0xFF22C55E)
val TapAccentGreenBg = Color(0xFF142A1E)
val TapAccentLavender = Color(0xFF9188F2)
val TapAccentLavenderBg = Color(0xFF23203A)
val TapAccentRed = Color(0xFFF87171)
val TapAccentRedBg = Color(0xFF2A181B)
val TapGoldChip = Color(0xFFEAB308)

private val TapDarkColorScheme = darkColorScheme(
    primary = TapTextPrimary,
    onPrimary = TapBgPrimary,
    secondary = TapAccentLavender,
    tertiary = TapAccentGreen,
    background = TapBgPrimary,
    onBackground = TapTextPrimary,
    surface = TapBgSurface,
    onSurface = TapTextPrimary,
    surfaceVariant = TapBgElevated,
    outline = TapBorderSubtle,
    error = TapAccentRed
)

@Composable
fun TapSaldoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TapDarkColorScheme,
        content = content
    )
}

fun issuerCardGradient(issuer: CardIssuer): Brush {
    val (start, end) = when (issuer) {
        CardIssuer.MANDIRI -> Color(0xFF1E3A8A) to Color(0xFF0D152B)
        CardIssuer.BCA_FLAZZ -> Color(0xFF0F766E) to Color(0xFF0D152B)
        CardIssuer.BNI_TAPCASH -> Color(0xFF9A3412) to Color(0xFF1C100C)
        CardIssuer.BRI_BRIZZI -> Color(0xFF1D4ED8) to Color(0xFF0F172A)
        CardIssuer.UNKNOWN -> Color(0xFF334155) to Color(0xFF0F172A)
    }
    return Brush.linearGradient(
        colors = listOf(start, end),
        start = Offset.Zero,
        end = Offset(600f, 600f)
    )
}

fun issuerDotColor(issuer: CardIssuer): Color = when (issuer) {
    CardIssuer.MANDIRI -> Color(0xFFF59E0B)
    CardIssuer.BCA_FLAZZ -> Color(0xFF2DD4BF)
    CardIssuer.BNI_TAPCASH -> Color(0xFFFB923C)
    CardIssuer.BRI_BRIZZI -> Color(0xFF38BDF8)
    CardIssuer.UNKNOWN -> Color(0xFFCBD5E1)
}

private val rupiahSymbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID")).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}

fun formatRupiah(amount: Long): String {
    val formatter = DecimalFormat("#,###", rupiahSymbols)
    return if (amount < 0L) "-Rp ${formatter.format(abs(amount))}"
    else "Rp ${formatter.format(amount)}"
}

fun formatSignedRupiah(amount: Long): String {
    val formatter = DecimalFormat("#,###", rupiahSymbols)
    val absText = formatter.format(abs(amount))
    return when {
        amount > 0L -> "+Rp $absText"
        amount < 0L -> "-Rp $absText"
        else -> "Rp 0"
    }
}

fun formatRelativeTime(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (timestampMillis <= 0L) return "Never scanned"
    val diffSec = ((nowMillis - timestampMillis) / 1_000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3_600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3_600L}h ago"
        else -> "${diffSec / 86_400L}d ago"
    }
}

fun formatDateTimeShort(timestampMillis: Long): String {
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("id-ID"))
    return fmt.format(Date(timestampMillis))
}

fun formatDayGroupHeader(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val txCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }

    val sameYear = nowCal.get(Calendar.YEAR) == txCal.get(Calendar.YEAR)
    val dayDiff = nowCal.get(Calendar.DAY_OF_YEAR) - txCal.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        else -> SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID")).format(Date(timestampMillis))
    }
}
