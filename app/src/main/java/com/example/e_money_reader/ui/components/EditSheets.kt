package com.example.e_money_reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.ui.theme.TapAccentLavender
import com.example.e_money_reader.ui.theme.TapAccentLavenderBg
import com.example.e_money_reader.ui.theme.TapBgElevated
import com.example.e_money_reader.ui.theme.TapBgPrimary
import com.example.e_money_reader.ui.theme.TapBgSurface
import com.example.e_money_reader.ui.theme.TapBorderSubtle
import com.example.e_money_reader.ui.theme.TapTextMuted
import com.example.e_money_reader.ui.theme.TapTextPrimary
import com.example.e_money_reader.ui.theme.TapTextSecondary
import com.example.e_money_reader.ui.theme.formatRupiah
import com.example.e_money_reader.ui.theme.formatSignedRupiah
import com.example.e_money_reader.ui.theme.issuerCardGradient

private val CardLabelSuggestions = listOf(
    "Primary Commute",
    "Daily Commute",
    "Tol Trans Jawa",
    "Family Backup",
    "Office Transit"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardLabelSheet(
    card: CardEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var labelText by remember(card.uid) { mutableStateOf(card.customLabel) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TapBgSurface,
        contentColor = TapTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    color = TapTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Edit Card Label",
                    color = TapTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "      ", fontSize = 13.sp)
            }

            // Mini Card Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(issuerCardGradient(card.issuer))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${card.issuer.displayName} · ${card.maskedCardNumber}",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRupiah(card.lastBalance),
                    color = TapTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Custom Card Label",
                color = TapTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                singleLine = true,
                placeholder = { Text("Masukkan nama kartu...", color = TapTextMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TapBgPrimary,
                    unfocusedContainerColor = TapBgPrimary,
                    focusedBorderColor = TapAccentLavender,
                    unfocusedBorderColor = TapBorderSubtle,
                    focusedTextColor = TapTextPrimary,
                    unfocusedTextColor = TapTextPrimary,
                    cursorColor = TapAccentLavender
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardLabelSuggestions.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TapBgElevated)
                            .clickable { labelText = suggestion }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = suggestion,
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Text(
                text = "Nama kartu disimpan secara lokal di perangkatmu saja.",
                color = TapTextMuted,
                fontSize = 11.sp
            )

            Button(
                onClick = {
                    val finalLabel = labelText.trim().ifEmpty { card.issuer.displayName }
                    onSave(finalLabel)
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TapTextPrimary,
                    contentColor = TapBgPrimary
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Save Label",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(
    tx: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (String?, TransactionCategory) -> Unit
) {
    var titleInput by remember(tx.id) { mutableStateOf(tx.customTitle.orEmpty()) }
    var selectedCategory by remember(tx.id) { mutableStateOf(tx.category) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TapBgSurface,
        contentColor = TapTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Transaction",
                    color = TapTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (titleInput.isNotBlank()) {
                    TextButton(onClick = { titleInput = "" }) {
                        Text(
                            text = "Reset Default",
                            color = TapAccentLavender,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Text(
                text = "Default: Transaksi #${tx.sequenceNumber} · ${formatSignedRupiah(tx.amount)}",
                color = TapTextSecondary,
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Transaksi #${tx.sequenceNumber} (kosongkan untuk default)",
                        color = TapTextMuted
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TapBgPrimary,
                    unfocusedContainerColor = TapBgPrimary,
                    focusedBorderColor = TapAccentLavender,
                    unfocusedBorderColor = TapBorderSubtle,
                    focusedTextColor = TapTextPrimary,
                    unfocusedTextColor = TapTextPrimary,
                    cursorColor = TapAccentLavender
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Kategori",
                color = TapTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) TapAccentLavenderBg else TapBgElevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) TapAccentLavender else TapBorderSubtle,
                                shape = CircleShape
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = cat.displayName,
                            color = if (isSelected) TapAccentLavender else TapTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSave(titleInput.trim().ifEmpty { null }, selectedCategory)
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TapTextPrimary,
                    contentColor = TapBgPrimary
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Save Transaction",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
