package com.example.e_money_reader.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.ui.components.EmoneyCardVisual
import com.example.e_money_reader.ui.components.TransactionRow
import com.example.e_money_reader.ui.theme.TapAccentGreen
import com.example.e_money_reader.ui.theme.TapAccentGreenBg
import com.example.e_money_reader.ui.theme.TapAccentLavender
import com.example.e_money_reader.ui.theme.TapAccentLavenderBg
import com.example.e_money_reader.ui.theme.TapAccentRed
import com.example.e_money_reader.ui.theme.TapAccentRedBg
import com.example.e_money_reader.ui.theme.TapBgPrimary
import com.example.e_money_reader.ui.theme.TapBgSurface
import com.example.e_money_reader.ui.theme.TapBorderSubtle
import com.example.e_money_reader.ui.theme.TapTextMuted
import com.example.e_money_reader.ui.theme.TapTextPrimary
import com.example.e_money_reader.ui.theme.TapTextSecondary
import com.example.e_money_reader.ui.theme.formatRelativeTime
import com.example.e_money_reader.ui.theme.formatRupiah

@Composable
fun CardDetailsScreen(
    card: CardEntity,
    transactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onEditCardLabel: (CardEntity) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onSetActive: (String) -> Unit,
    onDeleteCard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<TransactionCategory?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val filteredTransactions = remember(transactions, selectedFilter) {
        if (selectedFilter == null) transactions
        else transactions.filter { it.category == selectedFilter }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = TapBgSurface,
            title = {
                Text(
                    text = "Hapus Kartu Ini?",
                    color = TapTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Kartu \"${card.customLabel}\" beserta seluruh riwayat transaksinya yang tersimpan di HP akan dihapus permanen.",
                    color = TapTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteCard(card.uid)
                    }
                ) {
                    Text("Hapus", color = TapAccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal", color = TapTextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TapBgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconBtn(
                bg = TapBgSurface,
                tint = TapTextPrimary,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDesc = "Back",
                onClick = onBack
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Card Details",
                color = TapTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            CircleIconBtn(
                bg = TapAccentLavenderBg,
                tint = TapAccentLavender,
                icon = Icons.AutoMirrored.Rounded.Label,
                contentDesc = "Edit Card Label",
                onClick = { onEditCardLabel(card) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            CircleIconBtn(
                bg = TapAccentRedBg,
                tint = TapAccentRed,
                icon = Icons.Rounded.DeleteOutline,
                contentDesc = "Delete Card",
                onClick = { showDeleteDialog = true }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "full_card_artwork") {
                EmoneyCardVisual(
                    card = card,
                    showDetailPill = false,
                    showBottomBalance = true,
                    onDetailClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(key = "stored_balance_panel") {
                val shape = RoundedCornerShape(20.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(TapBgSurface)
                        .border(1.dp, TapBorderSubtle, shape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Stored Balance",
                                color = TapTextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatRupiah(card.lastBalance),
                                color = TapTextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TapAccentGreenBg)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "• Saved",
                                color = TapAccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last NFC scan: ${formatRelativeTime(card.lastScannedAt)}",
                            color = TapTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (card.isActive) {
                            Text(
                                text = "• Active on Home",
                                color = TapAccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(TapAccentLavenderBg)
                                    .clickable { onSetActive(card.uid) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Set as Active",
                                    color = TapAccentLavender,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Card Label Row (both top-right tag icon and this row open Edit Card Label)
            item(key = "card_label_row") {
                val shape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(TapBgSurface)
                        .border(1.dp, TapBorderSubtle, shape)
                        .clickable { onEditCardLabel(card) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Card Label",
                            color = TapTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = card.customLabel,
                            color = TapTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Edit >",
                        color = TapAccentLavender,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // History Header & Category Filter Chips
            item(key = "history_header_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Card Tap History (${filteredTransactions.size} records)",
                        color = TapTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChipPill(
                            label = "All",
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null }
                        )
                        listOf(
                            TransactionCategory.TRANSIT,
                            TransactionCategory.TOLL,
                            TransactionCategory.TOPUP,
                            TransactionCategory.OTHER
                        ).forEach { cat ->
                            FilterChipPill(
                                label = cat.displayName,
                                selected = selectedFilter == cat,
                                onClick = { selectedFilter = cat }
                            )
                        }
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item(key = "no_filtered_tx") {
                    Text(
                        text = "Tidak ada transaksi pada kategori ini.",
                        color = TapTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(TapBgSurface)
                            .border(1.dp, TapBorderSubtle, shape)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        TransactionRow(
                            tx = tx,
                            onClick = { onEditTransaction(tx) }
                        )
                    }
                }
            }

            item(key = "offline_footer_note") {
                Text(
                    text = "Offline contactless card snapshot. Ketuk transaksi mana saja untuk mengubah nama custom (default Transaksi #N) dan kategorinya.",
                    color = TapTextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun CircleIconBtn(
    bg: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) TapTextPrimary else TapBgSurface)
            .border(
                width = 1.dp,
                color = if (selected) TapTextPrimary else TapBorderSubtle,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TapBgPrimary else TapTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
