package com.example.e_money_reader.ui

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.CardIssuer
import com.example.e_money_reader.data.local.TapSaldoDatabase
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.data.repository.CardRepository
import com.example.e_money_reader.nfc.ApduTransceiver
import com.example.e_money_reader.nfc.ChipTransaction
import com.example.e_money_reader.nfc.EmoneyReaderManager
import com.example.e_money_reader.nfc.IsoDepApduTransceiver
import com.example.e_money_reader.nfc.ParsedCardData
import com.example.e_money_reader.nfc.ReadResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object WaitingForTap : ScanUiState
    data class Success(
        val card: CardEntity,
        val requiresActiveConfirmation: Boolean
    ) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

data class TapSaldoUiState(
    val cards: List<CardEntity> = emptyList(),
    val transactionsByCard: Map<String, List<TransactionEntity>> = emptyMap(),
    val scanState: ScanUiState = ScanUiState.Idle,
    val editingCardForLabel: CardEntity? = null,
    val editingTransaction: TransactionEntity? = null,
    val isNfcSupported: Boolean = true,
    val isNfcEnabled: Boolean = true
) {
    val isModalSheetOpen: Boolean
        get() = editingCardForLabel != null || editingTransaction != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class TapSaldoViewModel(
    private val repository: CardRepository,
    private val readerManager: EmoneyReaderManager = EmoneyReaderManager.default(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(TapSaldoUiState())
    val uiState: StateFlow<TapSaldoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCards()
                .flatMapLatest { cards ->
                    if (cards.isEmpty()) {
                        flowOf(cards to emptyMap())
                    } else {
                        val txFlows = cards.map { card ->
                            repository.observeTransactions(card.uid)
                        }
                        combine(txFlows) { txArrays ->
                            val map = cards.indices.associate { idx ->
                                cards[idx].uid to txArrays[idx]
                            }
                            cards to map
                        }
                    }
                }
                .collect { (cards, txMap) ->
                    _uiState.update {
                        it.copy(cards = cards, transactionsByCard = txMap)
                    }
                }
        }
    }

    fun updateNfcAvailability(supported: Boolean, enabled: Boolean) {
        _uiState.update { it.copy(isNfcSupported = supported, isNfcEnabled = enabled) }
    }

    fun startManualScan() {
        _uiState.update { it.copy(scanState = ScanUiState.WaitingForTap) }
    }

    fun cancelScan() {
        _uiState.update { it.copy(scanState = ScanUiState.Idle) }
    }

    fun acknowledgeScanKeepCurrentActive() {
        _uiState.update { it.copy(scanState = ScanUiState.Idle) }
    }

    fun confirmScannedCardAsActive() {
        val current = _uiState.value.scanState
        if (current is ScanUiState.Success) {
            viewModelScope.launch(ioDispatcher) {
                repository.setActiveCard(current.card.uid)
                _uiState.update { it.copy(scanState = ScanUiState.Idle) }
            }
        } else {
            _uiState.update { it.copy(scanState = ScanUiState.Idle) }
        }
    }

    fun onNfcTagDiscovered(tag: Tag) {
        if (_uiState.value.isModalSheetOpen) return
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            _uiState.update {
                it.copy(
                    scanState = ScanUiState.Error(
                        "Tag NFC ini tidak mendukung protokol ISO-DEP (bukan kartu e-money yang kompatibel)."
                    )
                )
            }
            return
        }
        processTransceiver(IsoDepApduTransceiver(isoDep)) {
            runCatching { isoDep.close() }
        }
    }

    fun processTransceiver(
        transceiver: ApduTransceiver,
        onFinally: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val activeBeforeScan = _uiState.value.cards.firstOrNull { it.isActive }
            val result = withContext(ioDispatcher) {
                try {
                    readerManager.readCard(transceiver)
                } finally {
                    onFinally()
                }
            }
            when (result) {
                is ReadResult.Success -> {
                    val savedCard = withContext(ioDispatcher) {
                        repository.saveScannedCard(result.data)
                    }
                    val needsConfirm = activeBeforeScan != null && activeBeforeScan.uid != savedCard.uid
                    _uiState.update {
                        it.copy(
                            scanState = ScanUiState.Success(
                                card = savedCard,
                                requiresActiveConfirmation = needsConfirm
                            )
                        )
                    }
                }
                is ReadResult.Error -> {
                    _uiState.update { it.copy(scanState = ScanUiState.Error(result.message)) }
                }
            }
        }
    }

    fun simulateDebugScan(issuer: CardIssuer = CardIssuer.MANDIRI) {
        val now = System.currentTimeMillis()
        val sample = when (issuer) {
            CardIssuer.MANDIRI -> ParsedCardData(
                uid = "04A1B2C34821",
                cardNumber = "6032982012344821",
                issuer = CardIssuer.MANDIRI,
                balance = 148_500L,
                chipTransactions = listOf(
                    ChipTransaction("SIM-M1", -3_500L, 56_000L, now - 86_400_000L),
                    ChipTransaction("SIM-M2", 100_000L, 156_000L, now - 43_200_000L),
                    ChipTransaction("SIM-M3", -7_500L, 148_500L, now - 300_000L)
                )
            )
            CardIssuer.BCA_FLAZZ -> ParsedCardData(
                uid = "049988777734",
                cardNumber = "1098000012347734",
                issuer = CardIssuer.BCA_FLAZZ,
                balance = 72_000L,
                chipTransactions = listOf(
                    ChipTransaction("SIM-F1", 100_000L, 100_000L, now - 172_800_000L),
                    ChipTransaction("SIM-F2", -13_000L, 87_000L, now - 90_000_000L),
                    ChipTransaction("SIM-F3", -15_000L, 72_000L, now - 600_000L)
                )
            )
            else -> ParsedCardData(
                uid = "041122331109",
                cardNumber = "7546120000001109",
                issuer = CardIssuer.BNI_TAPCASH,
                balance = 24_500L,
                chipTransactions = listOf(
                    ChipTransaction("SIM-B1", -3_000L, 24_500L, now - 7_200_000L)
                )
            )
        }
        viewModelScope.launch {
            val activeBefore = _uiState.value.cards.firstOrNull { it.isActive }
            val saved = withContext(ioDispatcher) { repository.saveScannedCard(sample, now) }
            val needsConfirm = activeBefore != null && activeBefore.uid != saved.uid
            _uiState.update {
                it.copy(scanState = ScanUiState.Success(saved, needsConfirm))
            }
        }
    }

    fun setActiveCard(uid: String) {
        viewModelScope.launch(ioDispatcher) {
            repository.setActiveCard(uid)
        }
    }

    fun openEditCardLabel(card: CardEntity) {
        _uiState.update { it.copy(editingCardForLabel = card, editingTransaction = null) }
    }

    fun saveCardLabel(uid: String, newLabel: String) {
        viewModelScope.launch(ioDispatcher) {
            repository.updateCardLabel(uid, newLabel)
            _uiState.update { it.copy(editingCardForLabel = null) }
        }
    }

    fun openEditTransaction(tx: TransactionEntity) {
        _uiState.update { it.copy(editingTransaction = tx, editingCardForLabel = null) }
    }

    fun saveTransactionEdits(id: Long, customTitle: String?, category: TransactionCategory) {
        viewModelScope.launch(ioDispatcher) {
            repository.updateTransaction(id, customTitle, category)
            _uiState.update { it.copy(editingTransaction = null) }
        }
    }

    fun dismissSheets() {
        _uiState.update { it.copy(editingCardForLabel = null, editingTransaction = null) }
    }

    fun deleteCard(uid: String) {
        viewModelScope.launch(ioDispatcher) {
            repository.deleteCard(uid)
        }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dao = TapSaldoDatabase.getInstance(appContext).dao()
            return TapSaldoViewModel(CardRepository(dao)) as T
        }
    }
}
