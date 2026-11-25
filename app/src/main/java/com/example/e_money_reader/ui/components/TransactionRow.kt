package com.example.e_money_reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.ui.theme.TapAccentGreen
import com.example.e_money_reader.ui.theme.TapAccentGreenBg
import com.example.e_money_reader.ui.theme.TapBgElevated
import com.example.e_money_reader.ui.theme.TapTextPrimary
import com.example.e_money_reader.ui.theme.TapTextSecondary
import com.example.e_money_reader.ui.theme.formatDateTimeShort
import com.example.e_money_reader.ui.theme.formatSignedRupiah

@Composable
fun TransactionRow(
    tx: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositive = tx.amount > 0L
    val icon = when {
        isPositive || tx.category == TransactionCategory.TOPUP -> Icons.Rounded.Add
        tx.category == TransactionCategory.TRANSIT -> Icons.Rounded.DirectionsBus
        tx.category == TransactionCategory.TOLL -> Icons.Rounded.DirectionsCar
        else -> Icons.AutoMirrored.Rounded.ReceiptLong
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isPositive) TapAccentGreenBg else TapBgElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tx.category.displayName,
                tint = if (isPositive) TapAccentGreen else TapTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = tx.displayTitle,
                color = TapTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${tx.category.displayName} · ${formatDateTimeShort(tx.timestamp)}",
                color = TapTextSecondary,
                fontSize = 11.sp
            )
        }

        Text(
            text = formatSignedRupiah(tx.amount),
            color = if (isPositive) TapAccentGreen else TapTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
