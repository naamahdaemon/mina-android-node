package org.minanode.android

import android.Manifest
import android.app.*
import android.content.Intent
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
    private var wake: PowerManager.WakeLock? = null
    private var producerFile: File? = null
    @Volatile private var nodePid: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        files = NodeFiles(this)
        requireNotNull(getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(CHANNEL, "Nœud Mina", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            requestStop()
        } else if (intent?.action == START && NodeState.flow.value.run !in
            listOf(RunPhase.STARTING, RunPhase.RUNNING, RunPhase.STOPPING)) {
            NodeState.flow.update { it.copy(run = RunPhase.STARTING, status = ChainStatus(), message = "Démarrage du nœud…") }
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, notification("Démarrage sur devnet"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification("Démarrage sur devnet"))
            }
            scope.launch { startNode() }
        }
        // Explicit user starts only. The supervisor terminates the native child
        // if the application dies, so no detached node is adopted on restart.
        return START_NOT_STICKY
    }

    private suspend fun startNode() = lifecycle.withLock {
        if (NodeState.flow.value.run == RunPhase.STOPPING) return@withLock
        var secret = LaunchSecret.take()
        try {
            val profile = ProfileStore(this).load()
            files = NodeFiles(this, profile.network.cli)
            val vault = ProducerVault(this)
            profile.validate(vault.exists(profile.network))
            require(!profile.producer || secret != null) { "Le mot de passe de la clé doit être saisi à chaque lancement" }
            if (profile.producer) producerFile = vault.materialize(profile.network)
            NodeState.flow.update { it.copy(network = profile.network.cli, monitor = MonitorState()) }
            val occupied = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", NodeApi.PORT), 500) }
            }.isSuccess
            check(!occupied) { "Le port ${NodeApi.PORT} est déjà occupé. Arrête l’autre instance." }
            val native = File(applicationInfo.nativeLibraryDir)
            val binary = File(native, "libmina.so")
            val supervisor = File(native, "libminasupervisor.so")
            check(binary.canExecute() && supervisor.canExecute()) { "Binaire ARM64 absent ou non exécutable dans cet APK" }
            files.append("\n=== Démarrage ${profile.network.cli} ${java.time.Instant.now()} ===")
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
                require(bytes.size <= 16384) { "Mot de passe trop long" }
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
            NodeState.flow.update { it.copy(run = RunPhase.RUNNING, wakeLock = true, message = "Nœud lancé · connexion à ${profile.network.cli}…") }
            val output = scope.launch {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach {
                            if (it.startsWith("[app] node-pid=")) nodePid = it.substringAfter('=').toIntOrNull()
                            files.append(it.take(64 * 1024))
                        }
                    }
                } catch (error: Exception) {
                    NodeState.message("Journal : ${error.message}")
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
                                else result.fold({ "API locale disponible" }, { "API en attente : ${it.message}" }))
                    }
                    if (SystemClock.elapsedRealtime() - lastWake > 5 * 60 * 1000L) {
                        wake?.acquire(10 * 60 * 1000L)
                        lastWake = SystemClock.elapsedRealtime()
                    }
                    if (ContextCompat.checkSelfPermission(this@NodeService, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                        requireNotNull(getSystemService(NotificationManager::class.java)).notify(ID,
                            notification("${NodeState.flow.value.status.phase} · ${NodeState.flow.value.status.peers} pairs"))
                    }
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
                        child = null
                        nodePid = null
                        producerFile?.delete(); producerFile = null
                        files.append("=== Fin du processus : $code ===")
                        releaseWake()
                        NodeState.flow.update { it.copy(
                            run = if (stopped || code == 0) RunPhase.STOPPED else RunPhase.ERROR,
                            message = if (stopped) "Nœud arrêté" else "Processus terminé (code $code)",
                            log = files.tail(), wakeLock = false) }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        } catch (error: Exception) {
            child?.destroyForcibly()
            child = null
            secret?.fill('\u0000'); LaunchSecret.clear()
            producerFile?.delete(); producerFile = null
            releaseWake()
            runCatching { files.append("Démarrage impossible : ${error.message}") }
            NodeState.flow.update { it.copy(run = RunPhase.ERROR, wakeLock = false, message = error.message ?: "Erreur de démarrage") }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestStop() {
        LaunchSecret.clear()
        NodeState.flow.update { it.copy(run = RunPhase.STOPPING, message = "Arrêt propre en cours…") }
        scope.launch {
            lifecycle.withLock {
                val process = child
                if (process == null) {
                    NodeState.flow.update { it.copy(run = RunPhase.STOPPED, message = "Nœud arrêté") }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
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
        LaunchSecret.clear()
        // EOF tells the supervisor to stop the node even if Android destroys
        // the service without an explicit Stop action.
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
            .addAction(0, "Arrêter", stop).build()
    }

    companion object {
        const val START = "org.minanode.android.START"
        const val STOP = "org.minanode.android.STOP"
        private const val CHANNEL = "mina-node"
        private const val ID = 1
    }
}
