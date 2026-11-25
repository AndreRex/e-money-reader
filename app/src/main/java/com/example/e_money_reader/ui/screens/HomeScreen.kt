package com.example.e_money_reader.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.ui.components.EmoneyCardVisual
import com.example.e_money_reader.ui.components.TransactionRow
import com.example.e_money_reader.ui.theme.TapAccentGreen
import com.example.e_money_reader.ui.theme.TapAccentGreenBg
import com.example.e_money_reader.ui.theme.TapAccentLavender
import com.example.e_money_reader.ui.theme.TapAccentLavenderBg
import com.example.e_money_reader.ui.theme.TapBgPrimary
import com.example.e_money_reader.ui.theme.TapBgSurface
import com.example.e_money_reader.ui.theme.TapBorderSubtle
import com.example.e_money_reader.ui.theme.TapTextMuted
import com.example.e_money_reader.ui.theme.TapTextPrimary
import com.example.e_money_reader.ui.theme.TapTextSecondary
import com.example.e_money_reader.ui.theme.formatDayGroupHeader
import com.example.e_money_reader.ui.theme.formatRelativeTime
import com.example.e_money_reader.ui.theme.formatRupiah
import com.example.e_money_reader.ui.theme.formatSignedRupiah
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    cards: List<CardEntity>,
    transactionsByCard: Map<String, List<TransactionEntity>>,
    onOpenDetail: (String) -> Unit,
    onOpenScan: () -> Unit,
    onEditCardLabel: (CardEntity) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TapBgPrimary)
            .statusBarsPadding()
    ) {
        if (cards.isEmpty()) {
            EmptyWalletState(onOpenScan = onOpenScan)
            return@Column
        }

        val activeIndex = remember(cards) {
            cards.indexOfFirst { it.isActive }.coerceAtLeast(0)
        }
        val pagerState = rememberPagerState(
            initialPage = activeIndex,
            pageCount = { cards.size }
        )
        val coroutineScope = rememberCoroutineScope()
        val activeCardUid = cards.firstOrNull { it.isActive }?.uid

        LaunchedEffect(activeCardUid) {
            val targetIdx = cards.indexOfFirst { it.uid == activeCardUid }
            if (targetIdx >= 0 && targetIdx != pagerState.currentPage) {
                pagerState.animateScrollToPage(targetIdx)
            }
        }

        val currentPage = pagerState.currentPage.coerceIn(0, cards.lastIndex)
        val selectedCard = cards[currentPage]
        val cardTransactions = transactionsByCard[selectedCard.uid].orEmpty()
        val groupedTransactions = remember(cardTransactions) {
            cardTransactions.groupBy { formatDayGroupHeader(it.timestamp) }
        }

        // Minimalist Sticky Top Bar: no left/right buttons, only centered pager indicator
        MinimalistPagerTopBar(
            pageCount = cards.size,
            currentPage = currentPage,
            onDotClick = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        val listState = rememberLazyListState()
        val exposedCardHeight = 136.dp
        val fullCardHeight = 264.dp
        val exposedCardPx = with(LocalDensity.current) { exposedCardHeight.toPx() }

        val cardRevealProgress by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset / exposedCardPx).coerceIn(0f, 1f)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // Item 0: Vertical Card Pager (bottom half tucked behind the foreground bubbles, hides as user scrolls down)
            item(key = "vertical_card_pager") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(exposedCardHeight)
                        .zIndex(0f)
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer {
                            translationY = cardRevealProgress * (exposedCardPx * 0.45f)
                            alpha = 1f - (cardRevealProgress * 0.95f)
                        }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        pageSpacing = 14.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val card = cards[page]
                        EmoneyCardVisual(
                            card = card,
                            showDetailPill = true,
                            showBottomBalance = false,
                            onDetailClick = { onOpenDetail(card.uid) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fullCardHeight)
                        )
                    }
                }
            }

            // Item 1+: Foreground Separate Bubbles Container (masks lower half of vertical card cleanly)
            item(key = "foreground_bubbles_top") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                        .background(
                            color = TapBgPrimary,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bubble 1: Card Balance + Scan NFC Card
                    BalanceAndScanBubble(
                        card = selectedCard,
                        onScanClick = onOpenScan
                    )

                    // Bubble 2: Edit Card Label & Details Quick Action
                    CardLabelQuickBubble(
                        card = selectedCard,
                        onClick = { onEditCardLabel(selectedCard) }
                    )
                }
            }

            if (groupedTransactions.isEmpty()) {
                item(key = "empty_tx_bubble") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                            .background(TapBgPrimary)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        BubbleContainer {
                            Text(
                                text = "Belum ada riwayat transaksi tercatat",
                                color = TapTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Transaksi dari chip kartu atau perubahan saldo akan otomatis tersimpan setiap kali kamu melakukan scan NFC.",
                                color = TapTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                groupedTransactions.entries.forEachIndexed { groupIdx, (dayLabel, txsInDay) ->
                    item(key = "tx_group_${selectedCard.uid}_$groupIdx") {
                        val dayNet = txsInDay.sumOf { it.amount }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(1f)
                                .background(TapBgPrimary)
                                .padding(horizontal = 20.dp)
                                .padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dayLabel,
                                    color = TapTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatSignedRupiah(dayNet),
                                    color = TapTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            BubbleContainer(verticalSpacing = 8.dp) {
                                txsInDay.forEach { tx ->
                                    TransactionRow(
                                        tx = tx,
                                        onClick = { onEditTransaction(tx) }
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

@Composable
private fun MinimalistPagerTopBar(
    pageCount: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val isSelected = index == currentPage
                val width by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 6.dp,
                    label = "pagerDotWidth"
                )
                val color by animateColorAsState(
                    targetValue = if (isSelected) TapTextPrimary else Color(0xFF3F3F4E),
                    label = "pagerDotColor"
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onDotClick(index) }
                )
            }
        }
    }
}

@Composable
private fun BalanceAndScanBubble(
    card: CardEntity,
    onScanClick: () -> Unit
) {
    BubbleContainer(verticalSpacing = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatRupiah(card.lastBalance),
                    color = TapTextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (card.isActive) "Card Balance · Primary Active" else "Card Balance · Saved Offline",
                    color = TapTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TapAccentGreenBg)
                    .border(1.dp, TapAccentGreen.copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "• ${formatRelativeTime(card.lastScannedAt)}",
                    color = TapAccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Button(
            onClick = onScanClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = TapTextPrimary,
                contentColor = TapBgPrimary
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.Nfc,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Scan NFC Card",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CardLabelQuickBubble(
    card: CardEntity,
    onClick: () -> Unit
) {
    BubbleContainer(
        modifier = Modifier.clickable(onClick = onClick),
        verticalSpacing = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(TapAccentLavenderBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Label,
                    contentDescription = null,
                    tint = TapAccentLavender,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = card.customLabel,
                    color = TapTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ubah nama custom kartu",
                    color = TapTextSecondary,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = TapTextSecondary
            )
        }
    }
}

@Composable
private fun BubbleContainer(
    modifier: Modifier = Modifier,
    verticalSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TapBgSurface)
            .border(1.dp, TapBorderSubtle, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        content()
    }
}

@Composable
private fun EmptyWalletState(
    onOpenScan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        BubbleContainer(verticalSpacing = 16.dp) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TapAccentGreenBg)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Nfc,
                    contentDescription = null,
                    tint = TapAccentGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "TapSaldo Siap Membaca Kartu",
                color = TapTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Tempelkan kartu e-money ke belakang HP kapan saja, atau tekan tombol di bawah untuk menyimpan kartu pertamamu.",
                color = TapTextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onOpenScan,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TapTextPrimary,
                    contentColor = TapBgPrimary
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Scan NFC Card",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
