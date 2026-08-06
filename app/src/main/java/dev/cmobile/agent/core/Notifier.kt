package dev.cmobile.agent.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.cmobile.agent.R
import java.util.concurrent.atomic.AtomicInteger

object Notifier {

    const val CHANNEL_SERVICE = "agent_service"
    const val CHANNEL_MESSAGES = "agent_messages"
    const val CHANNEL_ALERTS = "agent_alerts"

    const val SERVICE_NOTIFICATION_ID = 1

    private val nextId = AtomicInteger(1000)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Agent running",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "The persistent notice shown while the agent is awake in the background."
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Agent messages",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications the agent chooses to send you."
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Critical alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Urgent notices, such as a critically low battery."
                enableVibration(true)
            }
        )
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Whether POST_NOTIFICATIONS is held. Always true below Android 13, where the permission
     * is install-time.
     *
     * The app never prompts for this in-app; [openNotificationSettings] hands the user to the
     * system page instead, which is the only surface that can also re-enable notifications that
     * were switched off after being granted.
     */
    fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Opens the system notification settings page for this app. */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private object Settings {
        const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
        const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
    }

    /**
     * Posts a notification. Returns the id used, or null if the OS refused (no permission).
     */
    fun notify(
        context: Context,
        title: String,
        body: String,
        urgent: Boolean = false,
        id: Int? = null,
        ongoing: Boolean = false,
    ): Int? {
        ensureChannels(context)
        if (!areNotificationsEnabled(context)) return null

        val notificationId = id ?: nextId.incrementAndGet()
        val notification = buildNotification(context, title, body, urgent, ongoing)

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            notificationId
        } catch (_: SecurityException) {
            null
        }
    }

    /** Dismisses a notification this app posted. */
    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    fun buildNotification(
        context: Context,
        title: String,
        body: String,
        urgent: Boolean = false,
        ongoing: Boolean = false,
    ): Notification {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return NotificationCompat.Builder(
            context,
            if (urgent) CHANNEL_ALERTS else if (ongoing) CHANNEL_SERVICE else CHANNEL_MESSAGES,
        )
            .setSmallIcon(R.drawable.ic_agent)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(ongoing)
            .setSilent(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(pendingIntent)
            .build()
    }
}
