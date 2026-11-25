package com.example.e_money_reader

import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.e_money_reader.ui.ScanUiState
import com.example.e_money_reader.ui.TapSaldoViewModel
import com.example.e_money_reader.ui.components.EditCardLabelSheet
import com.example.e_money_reader.ui.components.EditTransactionSheet
import com.example.e_money_reader.ui.screens.CardDetailsScreen
import com.example.e_money_reader.ui.screens.HomeScreen
import com.example.e_money_reader.ui.screens.ScanNfcScreen
import com.example.e_money_reader.ui.theme.TapSaldoTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: TapSaldoViewModel by viewModels {
        TapSaldoViewModel.Factory(applicationContext)
    }

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        syncNfcStatus()
        extractTagFromIntent(intent)?.let(viewModel::onNfcTagDiscovered)

        setContent {
            TapSaldoTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val lifecycleOwner = LocalLifecycleOwner.current

                // Enable NFC Reader Mode when Activity is RESUMED and no Edit modal sheet is open
                DisposableEffect(lifecycleOwner, uiState.isModalSheetOpen) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                syncNfcStatus()
                                if (!uiState.isModalSheetOpen) {
                                    enableNfcReader()
                                } else {
                                    disableNfcReader()
                                }
                            }
                            Lifecycle.Event.ON_PAUSE -> disableNfcReader()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        if (uiState.isModalSheetOpen) disableNfcReader() else enableNfcReader()
                    }
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        disableNfcReader()
                    }
                }

                // Auto-navigate to Scan confirmation/error screen when an auto-scan finishes
                LaunchedEffect(uiState.scanState) {
                    val state = uiState.scanState
                    if ((state is ScanUiState.Success || state is ScanUiState.Error) && currentRoute != ROUTE_SCAN) {
                        navController.navigate(ROUTE_SCAN) {
                            launchSingleTop = true
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            cards = uiState.cards,
                            transactionsByCard = uiState.transactionsByCard,
                            isCardsLoaded = uiState.isCardsLoaded,
                            onOpenDetail = { uid ->
                                navController.navigate("details/$uid") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenScan = {
                                viewModel.startManualScan()
                                navController.navigate(ROUTE_SCAN) {
                                    launchSingleTop = true
                                }
                            },
                            onEditCardLabel = viewModel::openEditCardLabel,
                            onEditTransaction = viewModel::openEditTransaction
                        )
                    }

                    composable(
                        route = ROUTE_DETAILS,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })
                    ) { entry ->
                        val uid = entry.arguments?.getString("uid").orEmpty()
                        val card = uiState.cards.firstOrNull { it.uid == uid }
                        if (card != null) {
                            CardDetailsScreen(
                                card = card,
                                transactions = uiState.transactionsByCard[uid].orEmpty(),
                                onBack = { navController.popBackStack() },
                                onEditCardLabel = viewModel::openEditCardLabel,
                                onEditTransaction = viewModel::openEditTransaction,
                                onSetActive = viewModel::setActiveCard,
                                onDeleteCard = viewModel::deleteCard
                            )
                        } else if (uiState.isCardsLoaded) {
                            LaunchedEffect(uid) {
                                if (navController.currentBackStackEntry?.destination?.route == ROUTE_DETAILS) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    }

                    composable(ROUTE_SCAN) {
                        ScanNfcScreen(
                            scanState = uiState.scanState,
                            isNfcSupported = uiState.isNfcSupported,
                            isNfcEnabled = uiState.isNfcEnabled,
                            onRetry = viewModel::startManualScan,
                            onConfirmSetActive = {
                                viewModel.confirmScannedCardAsActive()
                                navController.popBackStack(ROUTE_HOME, inclusive = false)
                            },
                            onKeepCurrentActive = {
                                viewModel.acknowledgeScanKeepCurrentActive()
                                navController.popBackStack(ROUTE_HOME, inclusive = false)
                            },
                            onCancel = {
                                viewModel.cancelScan()
                                navController.popBackStack()
                            },
                            onSimulateDebugScan = viewModel::simulateDebugScan
                        )
                    }
                }

                uiState.editingCardForLabel?.let { cardToEdit ->
                    EditCardLabelSheet(
                        card = cardToEdit,
                        onDismiss = viewModel::dismissSheets,
                        onSave = { newLabel ->
                            viewModel.saveCardLabel(cardToEdit.uid, newLabel)
                        }
                    )
                }

                uiState.editingTransaction?.let { txToEdit ->
                    EditTransactionSheet(
                        tx = txToEdit,
                        onDismiss = viewModel::dismissSheets,
                        onSave = { customTitle, category ->
                            viewModel.saveTransactionEdits(txToEdit.id, customTitle, category)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractTagFromIntent(intent)?.let(viewModel::onNfcTagDiscovered)
    }

    private fun syncNfcStatus() {
        val adapter = nfcAdapter
        viewModel.updateNfcAvailability(
            supported = adapter != null,
            enabled = adapter?.isEnabled == true
        )
    }

    private fun enableNfcReader() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        adapter.enableReaderMode(
            this,
            { tag -> viewModel.onNfcTagDiscovered(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    private fun disableNfcReader() {
        val adapter = nfcAdapter ?: return
        runCatching { adapter.disableReaderMode(this) }
    }

    private fun extractTagFromIntent(intent: Intent?): Tag? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    companion object {
        private const val ROUTE_HOME = "home"
        private const val ROUTE_DETAILS = "details/{uid}"
        private const val ROUTE_SCAN = "scan"
    }
}
