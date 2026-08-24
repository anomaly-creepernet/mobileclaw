package at.creepervm1000.mobileclaw.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import at.creepervm1000.mobileclaw.AgentApp
import at.creepervm1000.mobileclaw.core.CronStore
import at.creepervm1000.mobileclaw.core.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the agent alive when the app isn't open.
 *
 * Two loops run here: a five-minute battery check, and a scheduler that fires the agent's
 * self-created crons. Both feed events into the same [at.creepervm1000.mobileclaw.agent.AgentEngine].
 */
class AgentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var batteryJob: Job? = null
    private var cronJob: Job? = null

    private lateinit var monitor: BatteryMonitor
    private var wakeLock: PowerManager.WakeLock? = null

    private val app get() = applicationContext as AgentApp

    /** Reacts immediately to a level change instead of waiting out the 5-minute poll. */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val percent = monitor.currentPercent()
            updateNotification(percent)
            if (percent in 0..15) {
                scope.launch { dispatchBatteryEvents() }
            }
        }
    }

    companion object {
        const val ACTION_START = "at.creepervm1000.mobileclaw.START"
        const val ACTION_STOP = "at.creepervm1000.mobileclaw.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AgentService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AgentService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        monitor = BatteryMonitor(this, app.engine)
        Notifier.ensureChannels(this)
        // Explicit export flag: required for context-registered receivers on API 34+.
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { app.prefs.update { it.copy(serviceEnabled = false) } }
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat(monitor.currentPercent())

        if (app.prefs.settings.first().useWakeLock) acquireWakeLock()

        if (batteryJob == null) batteryJob = scope.launch { batteryLoop() }
        if (cronJob == null) cronJob = scope.launch { cronLoop() }

        return START_STICKY
    }

    private fun startForegroundCompat(percent: Int) {
        val notification = buildServiceNotification(percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifier.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifier.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun buildServiceNotification(percent: Int) = Notifier.buildNotification(
        context = this,
        title = "Agent running",
        body = buildServiceNotificationBody(percent),
        ongoing = true,
    )

    private fun updateNotification(percent: Int) {
        runCatching {
            Notifier.notify(
                context = this,
                title = "Agent running",
                body = buildServiceNotificationBody(percent),
                id = Notifier.SERVICE_NOTIFICATION_ID,
                ongoing = true,
            )
        }
    }

    private fun buildServiceNotificationBody(percent: Int) = buildString {
        append(if (percent >= 0) "Battery $percent%" else "Watching device")
        val crons = app.crons.jobs.value.count { it.enabled }
        if (crons > 0) append(" · $crons scheduled task${if (crons == 1) "" else "s"}")
    }

    private suspend fun batteryLoop() {
        while (scope.isActive) {
            dispatchBatteryEvents()
            delay(BatteryMonitor.CHECK_INTERVAL_MS)
        }
    }

    private suspend fun dispatchBatteryEvents() {
        runCatching {
            val events = monitor.check()
            events.forEach { event ->
                if (app.prefs.settings.first().isConfigured) {
                    app.engine.sendEvent(event)
                }
            }
        }
    }

    private suspend fun cronLoop() {
        // Give the store a moment to load before the first sweep.
        app.crons.load()
        while (scope.isActive) {
            delay(CronStore.TICK_MS)
            runCatching {
                val now = System.currentTimeMillis()
                val due = app.crons.due(now)
                if (due.isEmpty()) return@runCatching
                if (!app.prefs.settings.first().isConfigured) return@runCatching

                for (job in due) {
                    // Skip rather than queue: a slow turn shouldn't cause a pile-up.
                    if (app.engine.isBusy()) break
                    app.crons.markRun(job.id, System.currentTimeMillis())
                    app.engine.sendEvent(
                        "Scheduled task \"${job.name}\" fired (every ${job.intervalMinutes} min). " +
                            "Your instruction to yourself was: ${job.prompt}"
                    )
                }
                updateNotification(monitor.currentPercent())
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mobileclaw:agent").apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
