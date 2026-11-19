package org.minanode.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.AlertDialog
import android.text.InputType
import android.view.WindowManager
import android.view.WindowInsetsController
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var preparingStart by mutableStateOf(false)
    private var pendingStart: NodeProfile? = null
    private var passwordDialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        val appearance = AppearancePreferences(this)
        setTheme(appearance.activityTheme())
        super.onCreate(savedInstanceState)
        // Compose owns the visible-system-bar padding on every supported Android version.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            var dark by remember { mutableStateOf(appearance.dark()) }
            var fullscreen by remember { mutableStateOf(appearance.fullscreen()) }
            var alertReminderSeconds by remember { mutableStateOf(appearance.productionAlertReminderSeconds()) }
            var alertLeadSeconds by remember { mutableIntStateOf(appearance.productionAlertLeadSeconds()) }
            DisposableEffect(dark, fullscreen) {
                window.decorView.doOnAttach { view ->
                    val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    view.windowInsetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)
                    applyFullscreen(view, fullscreen)
                }
                onDispose { }
            }
            MinaTheme(dark, fullscreen) {
                val context = LocalContext.current
                val openNavigationLabel = stringResource(R.string.open_navigation)
                val emptyLogsLabel = stringResource(R.string.logs_empty)
                val state by NodeState.flow.collectAsStateWithLifecycle()
                val store = remember { ProfileStore(this@MainActivity) }
                val vault = remember { ProducerVault(this@MainActivity) }
                val profileSaver = remember { Saver<NodeProfile, String>(
                    save = { it.toJson().put("network", it.network.name).toString() },
                    restore = { NodeProfile.fromJson(MinaNetwork.valueOf(JSONObject(it).getString("network")), JSONObject(it)) }) }
                var profile by rememberSaveable(stateSaver = profileSaver) { mutableStateOf(store.load()) }
                var profileDirty by rememberSaveable { mutableStateOf(false) }
                var hasKey by remember { mutableStateOf(vault.exists(profile.network)) }
                val pager = rememberPagerState { NodePage.entries.size }
                val scope = rememberCoroutineScope()
                var hamburger by remember { mutableStateOf(appearance.hamburger()) }
                var menuOpen by remember { mutableStateOf(false) }
                val page = NodePage.entries[pager.currentPage]
                val dashboardGestures = NodePage.entries[pager.settledPage] == NodePage.DASHBOARD
                val headerSwipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
                fun select(target: NodePage) { menuOpen = false; scope.launch { pager.animateScrollToPage(target.ordinal) } }
                val titleSwipe = Modifier.pointerInput(pager.settledPage, headerSwipeThreshold) {
                    val origin = pager.settledPage
                    var distance = 0f
                    detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
                        onDragEnd = {
                            val target = when {
                                distance > headerSwipeThreshold -> origin - 1
                                distance < -headerSwipeThreshold -> origin + 1
                                else -> origin
                            }
                            if (target != origin) NodePage.entries.getOrNull(target)?.let { select(it) }
                        }) { change, amount -> change.consume(); distance += amount }
                }
                BackHandler(enabled = menuOpen || pager.currentPage != 0) { if (menuOpen) menuOpen = false else select(NodePage.NODE) }
                val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val network = profile.network
                        val result = withContext(Dispatchers.IO) { runCatching { vault.import(network, uri) } }
                        hasKey = vault.exists(profile.network)
                        NodeState.message(result.fold({ uiText(R.string.key_imported) }, { uiText(R.string.import_failed, it.userText()) }))
                    }
                }
                val generateKey = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    hasKey = vault.exists(profile.network)
                }
                val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { continueStart() }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && !profileDirty && !preparingStart &&
                            NodeState.flow.value.run !in listOf(RunPhase.STARTING, RunPhase.RUNNING, RunPhase.STOPPING)) {
                            profile = store.load()
                            hasKey = vault.exists(profile.network)
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(state.run) {
                    if (state.run != RunPhase.STOPPED) preparingStart = false
                }
                val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching {
                            val stream = contentResolver.openOutputStream(uri) ?: throw UiException(uiText(R.string.file_inaccessible))
                            NodeFiles(this@MainActivity).export(stream)
                        } }
                        NodeState.message(result.fold({ uiText(R.string.log_exported) }, { uiText(R.string.export_failed, it.userText()) }))
                    }
                }
                val active = state.run in listOf(RunPhase.STARTING, RunPhase.RUNNING, RunPhase.STOPPING)
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(appContentInsets())) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth().then(titleSwipe), verticalAlignment = Alignment.CenterVertically) {
                                if (hamburger) Box {
                                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = openNavigationLabel }) { Text("\u2630") }
                                    DropdownMenu(menuOpen, { menuOpen = false }) {
                                        NodePage.entries.forEach { target ->
                                            DropdownMenuItem(text = { Text(stringResource(target.title)) }, onClick = { select(target) })
                                        }
                                    }
                                }
                                Text("MINA / ANDROID", Modifier.weight(1f), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary)
                                SlotIndicator(state.monitor, active && profile.producer)
                            }
                            if (!hamburger) NodeMenu(page, onSelect = { select(it) })
                            if (dashboardGestures) Text(stringResource(R.string.dashboard_navigation_help), titleSwipe,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { NodePage.entries[it].name },
                            flingBehavior = deliberatePagerFling(pager), userScrollEnabled = !dashboardGestures) { index ->
                            val current = NodePage.entries[index]
                            if (current == NodePage.DASHBOARD) {
                                if (state.run == RunPhase.RUNNING) AndroidView(
                                    factory = { DashboardView(it) { select(NodePage.NODE) } },
                                    modifier = Modifier.fillMaxSize(), update = { it.setAppearance(dark) }, onRelease = { it.dispose() })
                                else Text(stringResource(R.string.dashboard_api_error), Modifier.padding(24.dp))
                            } else if (current == NodePage.JOURNAL) {
                                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth().then(titleSwipe), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.journal), style = MaterialTheme.typography.titleLarge)
                                        TextButton(onClick = { export.launch("mina-android-log.txt") }) { Text(stringResource(R.string.export)) }
                                    }
                                    Text(state.message.resolve(context), titleSwipe.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                    Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                        SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                                            Text(state.log.ifBlank { emptyLogsLabel }, fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            } else Column(Modifier.fillMaxSize().verticalScroll(rememberSaveable(current, saver = ScrollState.Saver) { ScrollState(0) })
                                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (current == NodePage.SETTINGS) NavigationSettings(appearance, hamburger, onHamburger = { hamburger = it; appearance.setHamburger(it) },
                            onAndroidSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
                        if (current == NodePage.SETTINGS) AppearanceSettings(appearance.language(), dark, !preparingStart, fullscreen, alertLeadSeconds, alertReminderSeconds,
                            onLanguage = { appearance.setLanguage(it) },
                            onDark = { appearance.setDark(it); dark = it; setTheme(appearance.activityTheme()) },
                            onFullscreen = { appearance.setFullscreen(it); fullscreen = it },
                            onAlertTiming = { lead, reminder ->
                                appearance.setProductionAlertTiming(lead, reminder)
                                alertLeadSeconds = lead; alertReminderSeconds = reminder })
                        if (current == NodePage.SETTINGS) ProfileEditor(profile, !active && !preparingStart, hasKey,
                            onChange = { profile = it; profileDirty = true }, onNetwork = { profile = store.load(it); profileDirty = true; hasKey = vault.exists(it) },
                            onImport = { importKey.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            onGenerate = {
                                generateKey.launch(Intent(this@MainActivity, KeyGenerationActivity::class.java)
                                    .putExtra(KeyGenerationActivity.NETWORK, profile.network.name))
                            },
                            onRemove = { vault.remove(profile.network); hasKey = false },
                            onSave = { runCatching { profile.validate(hasKey); store.save(profile); profileDirty = false }
                                .onSuccess { NodeState.message(uiText(R.string.profile_saved)) }.onFailure { NodeState.message(it.userText()) } })
                        if (current == NodePage.SETTINGS) Text(state.message.resolve(context), style = MaterialTheme.typography.bodyMedium)
                        if (current == NodePage.MONITOR) MonitorPanel(state.monitor, store.load().producer, if (active) state.network else profile.network.cli, appearance.explorer(), active, titleSwipe)
                        if (current == NodePage.NODE) {
                        Column(Modifier.fillMaxWidth().then(titleSwipe), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text(stringResource(R.string.headline), style = MaterialTheme.typography.headlineLarge)
                            Text("${if (active) state.network else profile.network.cli} \u00b7 ARM64 \u00b7 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                NodeStatusBadge(state.run, state.status.phase)
                                Text(state.message.resolve(context), style = MaterialTheme.typography.bodyMedium)
                                if (state.run == RunPhase.STARTING || state.run == RunPhase.STOPPING) LinearProgressIndicator(Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(enabled = !active && !preparingStart, onClick = {
                                        runCatching { profile.validate(vault.exists(profile.network)); store.save(profile); profileDirty = false }.onSuccess {
                                            preparingStart = true
                                            pendingStart = profile
                                            // Finish the Android permission dialog before opening the
                                            // password dialog; never stack both windows at launch.
                                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else continueStart()
                                        }.onFailure { NodeState.message(it.userText()) }
                                    }) { Text(stringResource(R.string.start)) }
                                    OutlinedButton(enabled = active && state.run != RunPhase.STOPPING, onClick = {
                                        startService(Intent(this@MainActivity, NodeService::class.java).setAction(NodeService.STOP))
                                    }) { Text(stringResource(R.string.stop)) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric(stringResource(R.string.height), state.status.height?.toString() ?: "\u2014", Modifier.weight(1f))
                            Metric(stringResource(R.string.peers), state.status.peers.toString(), Modifier.weight(1f))
                        }
                        Text(stringResource(R.string.block_age_uptime, state.status.ageSeconds?.toString() ?: "\u2014", state.status.uptimeSeconds))
                        Text(stringResource(R.string.saved_state_api, state.status.persistence.resolve(context), NodeApi.PORT), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.selected_mode, stringResource(if (profile.producer) R.string.producer_mode else R.string.observer_mode)), style = MaterialTheme.typography.bodySmall)
                        if (state.vpn) Card {
                            Text(stringResource(R.string.vpn_warning), Modifier.padding(16.dp))
                        }
                        Text(stringResource(R.string.resource_warning), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.termux_separate), style = MaterialTheme.typography.bodySmall)
                        }
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val log = NodeFiles(this@MainActivity).tail()
            NodeState.flow.update { it.copy(log = log) }
        }
    }

    private fun continueStart() {
        val profile = pendingStart ?: return
        pendingStart = null
        if (profile.producer) passwordPrompt(profile) else launchNode(profile)
    }

    private fun launchNode(profile: NodeProfile, secret: CharArray? = null) {
        var id: String? = null
        runCatching {
            id = LaunchGate.offer(profile, secret)
            ContextCompat.startForegroundService(this,
                Intent(this, NodeService::class.java).setAction(NodeService.START).putExtra(NodeService.LAUNCH_ID, id))
        }.onFailure {
            LaunchGate.revoke(id); secret?.fill('\u0000'); preparingStart = false
            NodeState.message(uiText(R.string.start_failed, it.userText()))
        }
    }

    private fun passwordPrompt(profile: NodeProfile) {
        // Native editable text avoids retaining the password in Compose or saved state.
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setPadding(32, 24, 32, 24)
        }
        var submitted = false
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.password_title, profile.network.cli))
            .setMessage(getString(R.string.password_message))
            .setView(input).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.start) { _, _ ->
                val text = input.text
                val secret = CharArray(text.length) { text[it] }
                text.clear()
                submitted = true
                launchNode(profile, secret)
            }.create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        passwordDialog = dialog
        dialog.setOnDismissListener {
            input.text.clear(); passwordDialog = null
            if (!submitted) preparingStart = false
        }
        dialog.show()
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.doOnAttach { applyFullscreen(it, AppearancePreferences(this).fullscreen()) }
    }

    override fun onDestroy() {
        pendingStart = null
        passwordDialog?.dismiss()
        super.onDestroy()
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
