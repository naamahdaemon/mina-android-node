package org.minanode.android

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.CharBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NodeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Mutex()
    @Volatile private var child: java.lang.Process? = null
    private lateinit var files: NodeFiles
    private lateinit var productionAlerts: ProductionAlerts
    private var wake: PowerManager.WakeLock? = null
    @Volatile private var powerController: ProducerPowerController? = null
    private var producerFile: File? = null
    @Volatile private var nodePid: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        files = NodeFiles(this)
        productionAlerts = ProductionAlerts(this).also { it.createChannel() }
        requireNotNull(getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == POWER_RESUME) {
            // A manual resume changes only the current process state, never preferences.
            powerController?.wake()
            if (child == null) stopSelf()
        } else if (intent?.action == POWER_SUSPEND) {
            // The next normal poll rechecks the current chain/schedule before any SIGSTOP.
            powerController?.requestSuspend()
            if (child == null) stopSelf()
        } else if (intent?.action == POWER_WAKE || intent?.action == RECONFIGURE) {
            val controller = powerController
            if (intent.action == RECONFIGURE || intent.getStringExtra("power-token") == controller?.token) controller?.wake()
            if (child == null) stopSelf()
        } else if (intent?.action == STOP) {
            requestStop()
        } else if (intent?.action == START && NodeState.flow.value.run !in
            listOf(RunPhase.STARTING, RunPhase.RUNNING, RunPhase.SUSPENDED, RunPhase.STOPPING)) {
            val authorized = LaunchGate.take(intent.getStringExtra(LAUNCH_ID))
            NodeState.flow.update { it.copy(run = RunPhase.STARTING, network = authorized?.profile?.network?.cli ?: it.network,
                status = ChainStatus(), message = uiText(R.string.starting_node)) }
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, notification(getString(R.string.starting_network, NodeState.flow.value.network)), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification(getString(R.string.starting_network, NodeState.flow.value.network)))
            }
            if (authorized == null) {
                NodeState.flow.update { it.copy(run = RunPhase.ERROR,
                    message = uiText(R.string.missing_confirmation)) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                scope.launch { startNode(authorized) }.invokeOnCompletion { authorized.close() }
            }
        } else if (intent?.action == START) {
            LaunchGate.revoke(intent.getStringExtra(LAUNCH_ID))
        }
        // Explicit user starts only. The supervisor terminates the native child
        // if the application dies, so no detached node is adopted on restart.
        return START_NOT_STICKY
    }

    private suspend fun startNode(authorized: AuthorizedLaunch) = lifecycle.withLock {
        if (NodeState.flow.value.run == RunPhase.STOPPING) return@withLock
        var secret = authorized.password
        try {
            val profile = authorized.profile
            files = NodeFiles(this, profile.network.cli)
            val vault = ProducerVault(this)
            profile.validate(vault.exists(profile.network))
            requireUi(!profile.producer || secret != null, R.string.error_password_required)
            if (profile.producer) producerFile = vault.materialize(profile.network)
            NodeState.flow.update { it.copy(network = profile.network.cli, monitor = MonitorState(),
                suspensionForecast = SuspensionForecast(if (profile.producer && ProducerPowerPreferences(this).enabled())
                    SuspensionReason.SYNC else SuspensionReason.OFF)) }
            val occupied = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", NodeApi.PORT), 500) }
            }.isSuccess
            requireUi(!occupied, R.string.error_port_occupied, NodeApi.PORT)
            val native = File(applicationInfo.nativeLibraryDir)
            val binary = File(native, "libmina.so")
            val supervisor = File(native, "libminasupervisor.so")
            requireUi(binary.canExecute() && supervisor.canExecute(), R.string.error_binary_missing)
            files.append("\n=== Starting ${profile.network.cli} ${java.time.Instant.now()} ===")
            files.append(if (profile.producer) "[app] Producer mode · password confirmed for this launch"
                else "[app] Node mode without block production")
            val builder = ProcessBuilder(listOf(supervisor.absolutePath, binary.absolutePath)
                + profile.arguments(files.work, producerFile)).directory(files.work).redirectErrorStream(true)
            builder.environment().apply {
                keys.filter { it.startsWith("MINA_") }.toList().forEach { remove(it) }
                put("HOME", noBackupFilesDir.absolutePath)
                put("TMPDIR", cacheDir.absolutePath)
                put("MINA_DISABLE_FILESYSTEM_LOGGING", "true")
                put("RUST_BACKTRACE", "1")
                if (profile.slowNetwork) {
                    put("STAGED_LEDGER_AUX_AND_PENDING_COINBASES_AT_BLOCK_TIMEOUT", "900")
                    put("BEST_TIP_WITH_PROOF_TIMEOUT", "120")
                    put("BLOCK_TIMEOUT", "120")
                }
            }
            val process = builder.start()
            child = process
            val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(secret ?: CharArray(0)))
            val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
            try {
                requireUi(bytes.size <= 16384, R.string.error_password_long)
                process.outputStream.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size).array())
                process.outputStream.write(bytes)
                process.outputStream.flush()
            } finally {
                bytes.fill(0); secret?.fill('\u0000'); secret = null
                if (encoded.hasArray()) encoded.array().fill(0)
            }
            wake = requireNotNull(getSystemService(PowerManager::class.java))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MinaNode:active")
                .apply { setReferenceCounted(false); acquire(10 * 60 * 1000L) }
            NodeState.flow.update { it.copy(run = RunPhase.RUNNING, wakeLock = true, message = uiText(R.string.connecting_network, profile.network.cli)) }
            val output = scope.launch {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach {
                            if (it.startsWith("[app] node-pid=")) nodePid = it.substringAfter('=').toIntOrNull()
                            files.append(it.take(64 * 1024))
                        }
                    }
                } catch (error: Exception) {
                    NodeState.message(uiText(R.string.journal_error, error.userText()))
                }
            }
            val poll = scope.launch {
                var lastWake = SystemClock.elapsedRealtime()
                val monitor = ProductionMonitor(this@NodeService, files)
                while (isActive && process.isAlive) {
                    val vpn = requireNotNull(getSystemService(ConnectivityManager::class.java)).let { manager ->
                        manager.getNetworkCapabilities(manager.activeNetwork)
                            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    }
                    val result = runCatching { NodeApi.status(profile.network.cli) }
                    val measured = runCatching { monitor.sample(nodePid, profile.producer) }.getOrNull()
                    NodeState.flow.update {
                        it.copy(status = result.getOrDefault(it.status), vpn = vpn, monitor = measured ?: it.monitor,
                            log = runCatching { files.tail() }.getOrDefault(it.log),
                            message = if (it.run == RunPhase.STOPPING) it.message
                                else result.fold({ uiText(R.string.api_available) }, { uiText(R.string.api_pending, it.userText()) }))
                    }
                    if (profile.producer && measured != null && measured.producerError == null && NodeState.flow.value.run == RunPhase.RUNNING) {
                        runCatching { productionAlerts.check(profile.network.cli, measured.upcomingSlots) }
                    }
                    if (SystemClock.elapsedRealtime() - lastWake > 5 * 60 * 1000L) {
                        wake?.acquire(10 * 60 * 1000L)
                        lastWake = SystemClock.elapsedRealtime()
                    }
                    if (ContextCompat.checkSelfPermission(this@NodeService, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                        requireNotNull(getSystemService(NotificationManager::class.java)).notify(ID,
                            notification(statusNotification()))
                    }
                    if (profile.producer && ProducerPowerPreferences(this@NodeService).enabled() &&
                        NodeState.flow.value.run == RunPhase.RUNNING && result.isSuccess && measured != null) {
                        val controller = powerController ?: ProducerPowerController(this@NodeService, files, { nodePid },
                            { wake?.acquire(10 * 60 * 1000L) }, { wake?.let { if (it.isHeld) it.release() } }, {
                                if (ContextCompat.checkSelfPermission(this@NodeService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                    requireNotNull(getSystemService(NotificationManager::class.java)).notify(ID, notification(statusNotification()))
                                }
                            })
                            .also { powerController = it }
                        runCatching { controller.consider(result.getOrThrow(), measured) }.onFailure {
                            files.append("[app] Power saving skipped: ${it.javaClass.simpleName}")
                        }
                    } else powerController?.unavailable()
                    delay(if (profile.producer) 1000 else 5000)
                }
            }
            scope.launch {
                val code = process.waitFor()
                output.join()
                poll.cancelAndJoin()
                lifecycle.withLock {
                    if (child === process) {
                        val stopped = NodeState.flow.value.run == RunPhase.STOPPING
                        powerController?.close(); powerController = null
                        child = null
                        nodePid = null
                        producerFile?.delete(); producerFile = null
                        files.append("=== Process ended: $code ===")
                        releaseWake()
                        NodeState.flow.update { it.copy(
                            run = if (stopped || code == 0) RunPhase.STOPPED else RunPhase.ERROR,
                            message = if (stopped) uiText(R.string.node_stopped) else uiText(R.string.process_ended, code),
                            log = files.tail(), wakeLock = false, powerWakeAtMs = null, powerCanSuspend = false) }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        } catch (error: Exception) {
            child?.destroyForcibly()
            child = null
            secret?.fill('\u0000')
            producerFile?.delete(); producerFile = null
            releaseWake()
            runCatching { files.append("Start failed: ${error.userText().resolve(this)}") }
            NodeState.flow.update { it.copy(run = RunPhase.ERROR, wakeLock = false, message = uiText(R.string.start_failed, error.userText())) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestStop() {
        NodeState.flow.update { it.copy(run = RunPhase.STOPPING, message = uiText(R.string.clean_stop), powerCanSuspend = false) }
        powerController?.close()
        productionAlerts.cancel()
        LaunchGate.clear()
        scope.launch {
            lifecycle.withLock {
                val process = child
                if (process == null) {
                    NodeState.flow.update { it.copy(run = RunPhase.STOPPED, message = uiText(R.string.node_stopped)) }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    nodePid?.let { runCatching { android.system.Os.kill(-it, android.system.OsConstants.SIGCONT) } }
                    runCatching { process.outputStream.write("stop\n".toByteArray()); process.outputStream.flush() }
                    // The supervisor sends SIGINT, then enforces a 90 s bound.
                }
            }
        }
    }

    private fun releaseWake() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
    }

    override fun onDestroy() {
        powerController?.close()
        productionAlerts.cancel()
        LaunchGate.clear()
        // EOF tells the supervisor to stop the node even if Android destroys
        // the service without an explicit Stop action.
        nodePid?.let { runCatching { android.system.Os.kill(-it, android.system.OsConstants.SIGCONT) } }
        runCatching { child?.outputStream?.close() }
        releaseWake()
        super.onDestroy()
        // Jobs stay alive just long enough to drain and reap the child.
        if (child == null) scope.cancel()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, NodeService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_node).setContentTitle("Mina Node · ${NodeState.flow.value.network}")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.stop), stop).build()
    }

    private fun statusNotification(): String {
        val state = NodeState.flow.value
        if (state.run == RunPhase.SUSPENDED) return getString(R.string.power_waiting)
        return getString(R.string.notification_status, phaseText(this, state.status.phase), state.status.peers)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        productionAlerts.createChannel()
        val manager = requireNotNull(getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        if (child != null && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(ID, notification(statusNotification()))
        }
    }

    companion object {
        const val START = "org.minanode.android.START"
        const val LAUNCH_ID = "org.minanode.android.LAUNCH_ID"
        const val POWER_RESUME = "org.minanode.android.POWER_RESUME"
        const val POWER_SUSPEND = "org.minanode.android.POWER_SUSPEND"
        const val POWER_WAKE = "org.minanode.android.POWER_WAKE"
        const val RECONFIGURE = "org.minanode.android.POWER_RECONFIGURE"
        const val STOP = "org.minanode.android.STOP"
        private const val CHANNEL = "mina-node"
        private const val ID = 1
    }
}
