package com.mercan.fuzulplanim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mercan.fuzulplanim.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FuzulTheme { FuzulApp() } }
    }
}

enum class Screen { HOME, PLAN, TRANSACTIONS, FUZUL, MORE, FIXED, CARDS, ASSETS, FILES, REPORTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuzulApp(vm: MainViewModel = viewModel()) {
    val fixed by vm.fixed.collectAsStateWithLifecycle()
    val installments by vm.installments.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val months by vm.months.collectAsStateWithLifecycle()
    val contract by vm.contract.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val imports by vm.imports.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun showVmMessage() {
        val msg = vm.consumeMessage() ?: return
        scope.launch { snackbar.showSnackbar(msg) }
    }

    val budgetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBudget(uri) { showVmMessage() }
    }
    val statementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareStatement(uri) { showVmMessage() }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                            ?: error("Dosya açılamadı")
                    }
                }
                raw.onSuccess { vm.restoreBackup(it) { showVmMessage() } }
                    .onFailure { snackbar.showSnackbar("Yedek okunamadı: ${it.message}") }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val raw = vm.exportBackup()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(raw.toByteArray(StandardCharsets.UTF_8)) }
                            ?: error("Dosya oluşturulamadı")
                    }
                }.onSuccess { snackbar.showSnackbar("Yedek kaydedildi") }
                    .onFailure { snackbar.showSnackbar("Yedek kaydedilemedi: ${it.message}") }
            }
        }
    }

    val isRoot = screen in listOf(Screen.HOME, Screen.PLAN, Screen.TRANSACTIONS, Screen.FUZUL, Screen.MORE)
    val title = when (screen) {
        Screen.HOME -> "Fuzul Planım"
        Screen.PLAN -> "Aylık Plan"
        Screen.TRANSACTIONS -> "İşlemler"
        Screen.FUZUL -> "Fuzul"
        Screen.MORE -> "Daha Fazla"
        Screen.FIXED -> "Sabit Giderler"
        Screen.CARDS -> "Kartlar & Taksitler"
        Screen.ASSETS -> "Varlıklar"
        Screen.FILES -> "Dosya Merkezi"
        Screen.REPORTS -> "Raporlar"
    }

    Scaffold(
        containerColor = SurfaceSoft,
        topBar = {
            TopAppBar(
                title = { Column { Text(title, color = Color.White); if (screen == Screen.HOME) Text("Tüm finansın tek ekranda", color = Color.White.copy(alpha=.72f), style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = {
                    if (!isRoot) IconButton(onClick = { screen = Screen.MORE }) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(
                    Triple(Screen.HOME, "Ana Sayfa", Icons.Rounded.Home),
                    Triple(Screen.PLAN, "Plan", Icons.Rounded.CalendarMonth),
                    Triple(Screen.TRANSACTIONS, "İşlemler", Icons.Rounded.ReceiptLong),
                    Triple(Screen.FUZUL, "Fuzul", Icons.Rounded.DirectionsCar),
                    Triple(Screen.MORE, "Diğer", Icons.Rounded.MoreHoriz)
                ).forEach { (s, label, icon) ->
                    val selected = when (s) {
                        Screen.MORE -> screen in listOf(Screen.MORE, Screen.FIXED, Screen.CARDS, Screen.ASSETS, Screen.FILES, Screen.REPORTS)
                        else -> screen == s
                    }
                    NavigationBarItem(selected = selected, onClick = { screen = s }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                Screen.HOME -> HomeScreen(vm, fixed, installments, assets, transactions, months, contract,
                    onImportExcel = { budgetLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                    onOpenPlan = { screen = Screen.PLAN }, onOpenFuzul = { screen = Screen.FUZUL })
                Screen.PLAN -> PlanScreen(vm, months, fixed, installments)
                Screen.TRANSACTIONS -> TransactionsScreen(transactions)
                Screen.FUZUL -> FuzulScreen(vm, contract, months, fixed, installments)
                Screen.MORE -> MoreScreen(onFixed = { screen = Screen.FIXED }, onCards = { screen = Screen.CARDS }, onAssets = { screen = Screen.ASSETS }, onFiles = { screen = Screen.FILES }, onReports = { screen = Screen.REPORTS })
                Screen.FIXED -> FixedExpensesScreen(vm, fixed)
                Screen.CARDS -> CardsScreen(vm, cards, installments)
                Screen.ASSETS -> AssetsScreen(vm, assets)
                Screen.FILES -> FileCenterScreen(vm, imports,
                    onBudget = { budgetLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                    onStatement = { statementLauncher.launch(arrayOf("application/pdf","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/csv","text/plain","application/octet-stream")) },
                    onExport = { exportLauncher.launch("FuzulPlanim_Yedek.json") },
                    onRestore = { restoreLauncher.launch(arrayOf("application/json","text/plain","application/octet-stream")) })
                Screen.REPORTS -> ReportsScreen(vm, transactions, months, fixed, installments)
            }

            if (vm.busy) {
                Surface(color = Color.Black.copy(alpha = .28f), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(shape = MaterialTheme.shapes.large) {
                            Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                Text("Dosya işleniyor…", modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    vm.pendingStatement?.let { preview ->
        StatementPreviewDialog(preview, onCancel = { vm.clearPendingStatement() }) { source ->
            vm.commitPendingStatement(source) { showVmMessage() }
        }
    }
}
